param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string]$StartAt='')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$renderer=(Get-FileHash "$p/build/VPIsometricLighting.jar").Hash
$managed=(Get-FileHash "$p/build/ViewpointIsometricPlusAddon.jar").Hash
[ordered]@{checkpoint='112';version='0.2.34';rendererSHA256=$renderer;managedSHA256=$managed} | ConvertTo-Json | Set-Content -LiteralPath "$p/research/perf112/regression-artifact.json" -Encoding utf8
$checks=[ordered]@{
    focused='Verify112.ps1';alpha='Verify106.ps1';furniture='VerifyFurniture075.ps1';interaction='Verify084.ps1';click='Verify086.ps1';stability='Verify107.ps1';
    tables='Verify088.ps1';sources='Verify073.ps1';crowns='Verify103.ps1';lightweight='Verify082.ps1';mobile='Verify083.ps1';
    shader='Verify110Shader.ps1';treeShader='Verify111Shader.ps1';upscale='Verify096.ps1';motion='Verify102Motion.ps1';settings='VerifySettings078.ps1'
}
$started=$StartAt -eq ''
foreach($entry in $checks.GetEnumerator()) {
    if(!$started){$started=$entry.Key -eq $StartAt;if(!$started){continue}}
    $arguments=@{GameDirectory=$GameDirectory}
    if($entry.Key -in @('tables','sources','lightweight','mobile')){$arguments.Packs=@('default','library','vivid','somber')}
    if($entry.Key -eq 'shader'){$arguments.Output='research/perf112/native-effects-expanded'}
    if($entry.Key -eq 'treeShader'){$arguments.Output='research/perf112/tree-shader';$arguments.RenderParity=$true}
    & (Join-Path $PSScriptRoot $entry.Value) @arguments *> "$p/research/perf112/$($entry.Key).log"
    if($LASTEXITCODE -ne 0){throw "112 regression failed: $($entry.Key); see retained log"}
    if((Get-FileHash "$p/build/VPIsometricLighting.jar").Hash -ne $renderer -or (Get-FileHash "$p/build/ViewpointIsometricPlusAddon.jar").Hash -ne $managed){throw 'Staged artifact changed during112 regression'}
    Write-Host "112 PASS $($entry.Key)"
}
& python "$p/research/nvidia110-tools/validate_spot_bounds.py" --baseline "$p/research/nvidia110/expanded109-selected" --candidate "$p/research/perf112/native-effects-expanded" --output "$p/research/perf112/spot-bounds" *> "$p/research/perf112/spot-bounds.log"
if($LASTEXITCODE -ne 0){throw '112 retained NVIDIA index fix regression failed'}
Write-Host '112 PASS strictBounds'
Write-Host 'Performance112 serial regression checks passed.'
