param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string]$StartAt='')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$out=Join-Path $project 'research/bug113-final'
$renderer=(Get-FileHash -LiteralPath "$project/build/VPIsometricLighting.jar").Hash
$managed=(Get-FileHash -LiteralPath "$project/build/ViewpointIsometricPlusAddon.jar").Hash
[ordered]@{checkpoint='113';version='0.2.35';rendererSHA256=$renderer;managedSHA256=$managed}|ConvertTo-Json|Set-Content -LiteralPath "$out/regression-artifact.json" -Encoding utf8
$checks=[ordered]@{focused='Verify113.ps1';interaction='Verify084.ps1';click='Verify086.ps1';fading='Verify094.ps1';motion='Verify102Motion.ps1';crowns='Verify103.ps1';settings='VerifySettings078.ps1'}
$started=$StartAt -eq ''
foreach($check in $checks.GetEnumerator()){
 if(!$started){$started=$check.Key -eq $StartAt;if(!$started){continue}}
 $arguments=@{GameDirectory=$GameDirectory}
 if($check.Key -eq 'fading'){$arguments.Packs=@('default','library','vivid','somber')}
 & (Join-Path $PSScriptRoot $check.Value) @arguments *> "$out/$($check.Key).log"
 if($LASTEXITCODE -ne 0){throw "113 regression failed: $($check.Key)"}
 if((Get-FileHash -LiteralPath "$project/build/VPIsometricLighting.jar").Hash -ne $renderer -or (Get-FileHash -LiteralPath "$project/build/ViewpointIsometricPlusAddon.jar").Hash -ne $managed){throw '113 artifact changed during regression'}
 Write-Host "113 PASS $($check.Key)"
}
Write-Host '113 focused and existing-function serial regression passed.'
