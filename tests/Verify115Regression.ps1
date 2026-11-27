param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string]$StartAt='')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$out="$project/research/preload115-final"
$renderer=(Get-FileHash "$project/build/VPIsometricLighting.jar").Hash
$managed=(Get-FileHash "$project/build/ViewpointIsometricPlusAddon.jar").Hash
[ordered]@{checkpoint='115';version='0.2.37';rendererSHA256=$renderer;managedSHA256=$managed} | ConvertTo-Json | Set-Content -LiteralPath "$out/regression-artifact.json" -Encoding utf8
$checks=[ordered]@{alpha='Verify106.ps1';furniture='VerifyFurniture075.ps1';interaction='Verify084.ps1';click='Verify086.ps1';stream='Verify113.ps1';wall='Verify114.ps1';crowns='Verify103.ps1';settings='VerifySettings078.ps1'}
$started=$StartAt -eq ''
foreach($check in $checks.GetEnumerator()) {
    if(!$started){$started=$check.Key -eq $StartAt;if(!$started){continue}}
    & (Join-Path $PSScriptRoot $check.Value) -GameDirectory $GameDirectory *> "$out/$($check.Key).log"
    if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/$($check.Key).log" -Tail 25;throw "115 regression failed: $($check.Key)"}
    if((Get-FileHash "$project/build/VPIsometricLighting.jar").Hash -ne $renderer -or (Get-FileHash "$project/build/ViewpointIsometricPlusAddon.jar").Hash -ne $managed){throw '115 artifact changed during regression'}
    Write-Host "115 PASS $($check.Key)"
}
Write-Host '115 serial regression passed; no live traversal or multiplayer FPS claim.'
