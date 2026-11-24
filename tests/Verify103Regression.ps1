param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string]$StartAt='')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$checks=[ordered]@{
 'weaving'='VerifyWeaving.ps1';'crowns'='Verify103.ps1';'plants'='Verify072.ps1';'sources'='Verify073.ps1';
 'state'='Verify080.ps1';'tables'='Verify088.ps1';'trees'='Verify095.ps1';'characters'='Verify094.ps1';
 'snow'='VerifySnow092.ps1';'settings'='VerifySettings078.ps1';'motion'='Verify102Motion.ps1'
}
$started=$StartAt -eq ''
foreach($entry in $checks.GetEnumerator()) {
 if(!$started){$started=$entry.Key -eq $StartAt;if(!$started){continue}}
 $arguments=@{GameDirectory=$GameDirectory}
 if($entry.Key -in @('plants','sources')){$arguments.Packs=@('default','library','vivid','somber')}
 if($entry.Key -in @('tables','characters','snow')){$arguments.Packs=@('default','somber')}
 & (Join-Path $PSScriptRoot $entry.Value) @arguments *> "$p/research/build103-$($entry.Key).log"
 if($LASTEXITCODE -ne 0){throw "103 regression failed: $($entry.Key); see retained log"}
 Write-Host "103 PASS $($entry.Key)"
}
Write-Host '103 serial regression checks passed.'
