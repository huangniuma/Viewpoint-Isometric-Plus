param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string]$From='cpu')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$packs=@('default','library','vivid','somber')
$checks=[ordered]@{
 'cpu'='Verify099Cpu.ps1'; 'furniture'='VerifyFurniture075.ps1'; 'framegen'='Verify097.ps1'; 'framegen-world'='Verify097World.ps1';
 'upscale'='Verify096.ps1'; 'world'='Verify096World.ps1'; 'water'='Verify096Water.ps1';
 'settings'='VerifySettings078.ps1'; 'state'='Verify080.ps1'; 'interaction'='Verify084.ps1';
 'click'='Verify086.ps1'; 'tree-layers'='Verify058.ps1'; 'trees'='Verify095.ps1';
 'tree-style'='VerifyTree075.ps1'; 'characters'='Verify094.ps1'; 'tables'='Verify088.ps1';
 'weather'='Verify089.ps1'; 'boundary'='Verify090.ps1'; 'snow'='VerifySnow091.ps1';
 'padding'='VerifySnow092.ps1'
}
if($From -notin $checks.Keys){throw "Unknown regression stage: $From"}
$started=$false
foreach($entry in $checks.GetEnumerator()) {
 if($entry.Key -eq $From){$started=$true}
 if(!$started){continue}
 $arguments=@{GameDirectory=$GameDirectory}
 if($entry.Key -in @('trees','tree-style','characters','tables','weather','boundary','snow','padding')){$arguments.Packs=$packs}
 if($entry.Key -in @('characters','padding')){$arguments.Packs=@('default','vivid','somber')}
 & (Join-Path $PSScriptRoot $entry.Value) @arguments *> "$p/research/build099-$($entry.Key).log"
 if($LASTEXITCODE -ne 0){throw "099 regression failed: $($entry.Key), see its retained log"}
 Write-Host "099 PASS $($entry.Key)"
}
Write-Host '099 serial GPU/regression checks passed.'
