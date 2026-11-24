param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
& "$PSScriptRoot/Verify100.ps1" -GameDirectory $GameDirectory *> "$p/research/build101-light.log"
if($LASTEXITCODE -ne 0){throw '101 tree light failed'}
Write-Host '101 PASS selected tree lighting and roots'
& "$PSScriptRoot/Verify095.ps1" -GameDirectory $GameDirectory *> "$p/research/build101-trees.log"
if($LASTEXITCODE -ne 0){throw '101 crown ownership failed'}
Write-Host '101 PASS four-pack crown ownership'
& "$PSScriptRoot/VerifyTree075.ps1" -GameDirectory $GameDirectory -Packs @('default','library','vivid','somber') *> "$p/research/build101-tree-style.log"
if($LASTEXITCODE -ne 0){throw '101 tree style/fade failed'}
Write-Host '101 PASS tree layers and fade'
& "$PSScriptRoot/Verify089.ps1" -GameDirectory $GameDirectory -Packs @('default','library','vivid','somber') *> "$p/research/build101-weather.log"
if($LASTEXITCODE -ne 0){throw '101 tree weather failed'}
Write-Host '101 PASS tree weather'
Write-Host '101 focused checks passed.'
