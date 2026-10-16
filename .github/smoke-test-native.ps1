# Starts the native Windows binary and fails unless it serves a health check.
# usage: smoke-test-native.ps1 <binary>
param([Parameter(Mandatory = $true)][string] $Binary)

$process = Start-Process -FilePath $Binary -RedirectStandardOutput smoke-test.log -RedirectStandardError smoke-test.err -PassThru -NoNewWindow
try {
    $deadline = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $deadline) {
        if ($process.HasExited) {
            Write-Output "The agent exited with $($process.ExitCode) before it became healthy."
            Get-Content smoke-test.log, smoke-test.err
            exit 1
        }
        try {
            $health = Invoke-RestMethod -Uri 'http://localhost:8080/actuator/health' -TimeoutSec 5
            if (Select-String -Path smoke-test.log -Pattern 'Started SysAPIApplicationKt' -Quiet) {
                Write-Output "Healthy: $($health.status)"
                exit 0
            }
        } catch { }
        Start-Sleep -Seconds 2
    }
    Write-Output 'The agent did not become healthy within 120s.'
    Get-Content smoke-test.log, smoke-test.err
    exit 1
} finally {
    Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
}
