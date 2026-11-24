param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
& "$PSScriptRoot/Verify100.ps1" -GameDirectory $GameDirectory *> "$p/research/build102-light.log"
if($LASTEXITCODE -ne 0){throw '102 tree light failed'}
Write-Host '102 PASS selected tree lighting and roots'
& "$PSScriptRoot/Verify095.ps1" -GameDirectory $GameDirectory *> "$p/research/build102-trees.log"
if($LASTEXITCODE -ne 0){throw '102 crown ownership failed'}
Write-Host '102 PASS four-pack crown ownership'
& "$PSScriptRoot/VerifyTree075.ps1" -GameDirectory $GameDirectory -Packs @('default','library','vivid','somber') *> "$p/research/build102-tree-style.log"
if($LASTEXITCODE -ne 0){throw '102 tree style/fade failed'}
Write-Host '102 PASS tree layers and fade'
& "$PSScriptRoot/Verify089.ps1" -GameDirectory $GameDirectory -Packs @('default','library','vivid','somber') *> "$p/research/build102-weather.log"
if($LASTEXITCODE -ne 0){throw '102 tree weather failed'}
Write-Host '102 PASS tree weather'
Write-Host '102 focused checks passed.'
