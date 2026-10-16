$ErrorActionPreference = 'Stop'

$version = '@VERSION@'
$jvmArgs = @(@JVM_ARGS@)
$nativeArgs = @(@NATIVE_ARGS@)
$defaultRuntime = 'jvm'
$nativeLimitationExitCode = 86

$appHome = $PSScriptRoot
$runtimeFile = 'config\runtime'
$runtime = $env:SYSAPI_RUNTIME
$runtimeSource = 'SYSAPI_RUNTIME'
if (-not $runtime -and (Test-Path $runtimeFile)) {
    $runtime = "$(Get-Content $runtimeFile -TotalCount 1)".Trim()
    $runtimeSource = $runtimeFile
}
if (-not $runtime) { $runtime = $defaultRuntime }
$dataDir = if ($env:SYSAPI_DATA_DIR) { $env:SYSAPI_DATA_DIR } else { 'data' }
$marker = Join-Path $dataDir 'native-disabled'
$readyFile = Join-Path $dataDir '.native-ready'

Add-Type -TypeDefinition @'
using System.Runtime.InteropServices;

public static class SysApiConsoleControl {
    public delegate bool Handler(int controlType);

    [DllImport("kernel32.dll")]
    private static extern bool SetConsoleCtrlHandler(Handler handler, bool add);

    private static readonly Handler handler = controlType => { Stopping = true; return true; };

    public static volatile bool Stopping;

    public static void Install() { SetConsoleCtrlHandler(handler, true); }
}
'@
[SysApiConsoleControl]::Install()

function Write-Launcher([string] $message) {
    [Console]::Out.WriteLine("[launcher] $message")
}

function Join-CommandLine([string[]] $arguments) {
    ($arguments | ForEach-Object {
        if ($_ -eq '' -or $_ -match '[\s"]') { '"' + ($_ -replace '(\\*)"', '$1$1\"' -replace '(\\+)$', '$1$1') + '"' } else { $_ }
    }) -join ' '
}

function Invoke-Agent([string] $executable, [string[]] $arguments, [hashtable] $environment = @{}) {
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $executable
    $startInfo.Arguments = Join-CommandLine $arguments
    $startInfo.UseShellExecute = $false
    $startInfo.WorkingDirectory = (Get-Location).Path
    $startInfo.EnvironmentVariables['SYSAPI_LAUNCHER_PID'] = "$PID"
    foreach ($name in $environment.Keys) {
        $startInfo.EnvironmentVariables[$name] = $environment[$name]
    }
    $process = [System.Diagnostics.Process]::Start($startInfo)
    $process.WaitForExit()
    return $process.ExitCode
}

function Invoke-Probe([string] $executable) {
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $executable
    $startInfo.Arguments = '--native-probe'
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    try {
        $process = [System.Diagnostics.Process]::Start($startInfo)
        $stderr = $process.StandardError.ReadToEndAsync()
        $output = $process.StandardOutput.ReadToEnd() + $stderr.Result
        $process.WaitForExit()
        return @{ ExitCode = $process.ExitCode; Output = $output.Trim() }
    } catch {
        return @{ ExitCode = -1; Output = "$_" }
    }
}

function Invoke-Jvm([hashtable] $environment = @{}) {
    $java = Join-Path $appHome 'jre\bin\java.exe'
    if (-not (Test-Path $java)) {
        $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java.exe' }
    }
    $jar = Join-Path $appHome 'lib\sysapi-all.jar'
    if (-not (Test-Path $jar)) {
        $jar = Join-Path $appHome 'sysapi.exe'
    }
    $javaOpts = if ($env:JAVA_OPTS) { $env:JAVA_OPTS -split '\s+' | Where-Object { $_ } } else { @() }
    exit (Invoke-Agent $java (@($jvmArgs) + @($javaOpts) + @('-jar', $jar) + @($script:agentArgs)) $environment)
}

function Disable-Native([string] $reason, [string] $details) {
    New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
    Set-Content -Path $marker -Value @($version, $reason, $details)
    Write-Launcher "runtime=jvm reason=$reason, native stays off for $version (delete $marker to try it again)"
}

$script:agentArgs = @($args)

switch ($runtime) {
    'jvm' {
        Write-Launcher 'runtime=jvm reason=configured'
        Invoke-Jvm
    }
    { $_ -in 'auto', 'native' } { }
    default {
        Write-Launcher "$runtimeSource must be auto, native or jvm, not '$runtime'"
        exit 64
    }
}

$binary = Join-Path $appHome 'native\windows-amd64\sysapi.exe'
if (-not [Environment]::Is64BitOperatingSystem -or -not (Test-Path $binary)) {
    if ($runtime -eq 'native') {
        Write-Launcher "$runtimeSource is native but there is no native binary at $binary"
        exit 1
    }
    Write-Launcher 'runtime=jvm reason=no native binary for this platform'
    Invoke-Jvm
}

if ($runtime -eq 'native') {
    Write-Launcher 'runtime=native reason=configured'
    exit (Invoke-Agent $binary (@($nativeArgs) + @($script:agentArgs)))
}

if ((Test-Path $marker) -and ((Get-Content $marker -TotalCount 1) -eq $version)) {
    Write-Launcher "runtime=jvm reason=$((Get-Content $marker)[1]), recorded in $marker"
    Invoke-Jvm
}

$probeResult = Invoke-Probe $binary
$probe = $probeResult.Output
$probeExitCode = $probeResult.ExitCode
if ($probeExitCode -ne 0) {
    Write-Launcher "the native binary cannot run on this host (exit code $probeExitCode): $probe"
    Disable-Native 'platform' "exit code $probeExitCode $probe"
    Invoke-Jvm
}
if (-not $probe.Contains("sysapi $version native")) {
    Disable-Native 'version-mismatch' "expected sysapi $version native, the binary said: $probe"
    Invoke-Jvm
}

New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
Remove-Item -Force -ErrorAction SilentlyContinue $readyFile
Write-Launcher 'runtime=native'
$status = Invoke-Agent $binary (@($nativeArgs) + @($script:agentArgs)) @{ SYSAPI_READY_FILE = $readyFile }

if ([SysApiConsoleControl]::Stopping -or (Test-Path $readyFile) -or $status -eq 0) {
    exit $status
}
if ($status -eq $nativeLimitationExitCode) {
    Disable-Native 'native-limitation' "the native binary exited with $status during startup"
    Invoke-Jvm
}
if ($status -lt 0) {
    Disable-Native 'native-crash' ('the native binary crashed during startup with 0x{0:X8}' -f $status)
    Invoke-Jvm
}

Write-Launcher "runtime=jvm reason=the native binary exited with $status during startup, retrying on the JVM to tell whether that was native-specific"
Invoke-Jvm @{ SYSAPI_NATIVE_DISABLED_FILE = (Join-Path (Get-Location).Path $marker) }
