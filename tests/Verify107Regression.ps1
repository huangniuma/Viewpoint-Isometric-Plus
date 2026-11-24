param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string]$StartAt='')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$tested=[ordered]@{
 checkpoint='107';rendererSHA256=(Get-FileHash "$p/build/VPIsometricLighting.jar").Hash
 managedSHA256=(Get-FileHash "$p/build/ViewpointIsometricPlusAddon.jar").Hash
 recordedDuringSerialRegression=$true;regressionLog='research/build107-regression.log'
}
$tested | ConvertTo-Json | Set-Content -LiteralPath "$p/research/build107-regression-artifact.json" -Encoding utf8
$checks=[ordered]@{
 'weaving'='VerifyWeaving.ps1';'stability'='Verify107.ps1';'focused'='Verify106.ps1';'furniture'='VerifyFurniture075.ps1';'culling'='Verify105.ps1';'cache'='Verify105Cache.ps1';'lightweight'='Verify082.ps1';'mobile'='Verify083.ps1';'performance'='Verify104.ps1';'crowns'='Verify103.ps1';'plants'='Verify072.ps1';'sources'='Verify073.ps1';
 'state'='Verify080.ps1';'interaction'='Verify084.ps1';'click'='Verify086.ps1';'upscale'='Verify096.ps1';'tables'='Verify088.ps1';'trees'='Verify095.ps1';'characters'='Verify094.ps1';
 'snow'='VerifySnow092.ps1';'settings'='VerifySettings078.ps1';'motion'='Verify102Motion.ps1'
}
$started=$StartAt -eq ''
foreach($entry in $checks.GetEnumerator()) {
 if(!$started){$started=$entry.Key -eq $StartAt;if(!$started){continue}}
 $arguments=@{GameDirectory=$GameDirectory}
 if($entry.Key -in @('plants','sources','lightweight','mobile')){$arguments.Packs=@('default','library','vivid','somber')}
 if($entry.Key -in @('tables','characters','snow')){$arguments.Packs=@('default','somber')}
 & (Join-Path $PSScriptRoot $entry.Value) @arguments *> "$p/research/build107-$($entry.Key).log"
 if($LASTEXITCODE -ne 0){throw "107 regression failed: $($entry.Key); see retained log"}
 if((Get-FileHash "$p/build/VPIsometricLighting.jar").Hash -ne $tested.rendererSHA256 -or (Get-FileHash "$p/build/ViewpointIsometricPlusAddon.jar").Hash -ne $tested.managedSHA256){throw 'Staged artifact changed during107 regression'}
 Write-Host "107 PASS $($entry.Key)"
}
Write-Host '107 serial regression checks passed.'
