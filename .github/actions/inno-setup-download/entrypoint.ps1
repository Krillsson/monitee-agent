Set-Location $Env:TEMP

Write-Host "InnoSetup: Downloading InnoSetup $($env:IS_VERSION)"
$tag_version = ${env:IS_VERSION}.replace('.', '_')
$dl_url = "https://github.com/jrsoftware/issrc/releases/download/is-${tag_version}/innosetup-${env:IS_VERSION}.exe"
Invoke-WebRequest -URI $dl_url -OutFile inno.exe

Write-Host "InnoSetup: Installing InnoSetup silently"
Start-Process -FilePath ".\inno.exe" -ArgumentList "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/SP-" -Wait

Write-Host "InnoSetup: Adding InnoSetup to path"
Add-Content $Env:GITHUB_PATH "C:\Program Files (x86)\Inno Setup 6"
