param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$packs=@('default','library','vivid','somber')
# Focused light, crown and weather checks run first and are logged separately.
$checks=[ordered]@{
 'cpu'='Verify099Cpu.ps1'; 'furniture'='VerifyFurniture075.ps1'; 'framegen'='Verify097.ps1'; 'framegen-world'='Verify097World.ps1';
 'upscale'='Verify096.ps1'; 'world'='Verify096World.ps1'; 'water'='Verify096Water.ps1';
 'settings'='VerifySettings078.ps1'; 'state'='Verify080.ps1'; 'interaction'='Verify084.ps1';
 'click'='Verify086.ps1'; 'tree-layers'='Verify058.ps1'; 'characters'='Verify094.ps1';
 'tables'='Verify088.ps1'; 'boundary'='Verify090.ps1'; 'snow'='VerifySnow091.ps1'; 'padding'='VerifySnow092.ps1'
}
foreach($entry in $checks.GetEnumerator()) {
 $arguments=@{GameDirectory=$GameDirectory}
 if($entry.Key -in @('characters','tables','boundary','snow','padding')){$arguments.Packs=$packs}
 if($entry.Key -in @('characters','padding')){$arguments.Packs=@('default','vivid','somber')}
 & (Join-Path $PSScriptRoot $entry.Value) @arguments *> "$p/research/build102-$($entry.Key).log"
 if($LASTEXITCODE -ne 0){throw "102 regression failed: $($entry.Key), see retained log"}
 Write-Host "102 PASS $($entry.Key)"
}
Write-Host '102 serial regression checks passed.'
