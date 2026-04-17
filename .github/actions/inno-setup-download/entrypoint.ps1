Set-Location $Env:TEMP

Write-Host "InnoSetup: Getting lastest innoextract release"
$extract_release = Invoke-WebRequest https://api.github.com/repos/dscharrer/innoextract/releases/latest | ConvertFrom-Json

$extract_assets = $extract_release.assets
$extract_url = ""

Write-Host "InnoSetup: Getting windows zip"
foreach ($asset in $extract_assets) {
	if ($asset.name -match '-windows\.zip$') {
		Write-Host "InnoSetup: Windows zip found name ${asset.name}"
		$extract_url = $asset.browser_download_url
	}
}

if (-not $extract_url) {
	Write-Host "InnoSetup: Windows zip not found"
	exit 1
}

Write-Host "InnoSetup: Downloading innoextract"
Invoke-WebRequest -URI $extract_url -OutFile innoextract.zip
Write-Host "InnoSetup: Extracting innoextract.zip"
Expand-Archive innoextract.zip -DestinationPath innoextract
Set-Alias -Name Inno-Extract -Value "$(Get-Location)\innoextract\innoextract.exe"

Write-Host "InnoSetup: Downloading InnoSetup executable"
$tag_version = ${env:IS_VERSION}.replace('.', '_')
$dl_url = "https://github.com/jrsoftware/issrc/releases/download/is-${tag_version}/innosetup-${env:IS_VERSION}.exe"
Invoke-WebRequest -URI $dl_url -OutFile inno.exe

Write-Host "InnoSetup: Extracting InnoSetup"
Inno-Extract inno.exe --output-dir ./ --include app
Rename-Item app -NewName inno

Write-Host "InnoSetup: Downloading encryption dll"
Invoke-WebRequest -URI https://jrsoftware.org/download.php/iscrypt.dll -OutFile .\inno\ISCrypt.dll

Write-Host "InnoSetup: Adding InnoSetup to path"
Add-Content $Env:GITHUB_PATH "$(Get-Location)\inno"
