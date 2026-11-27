param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$out="$project/research/pzo117-final"
New-Item -ItemType Directory -Force -Path $out | Out-Null
$renderer=(Get-FileHash "$project/build/VPIsometricLighting.jar").Hash
$managed=(Get-FileHash "$project/build/ViewpointIsometricPlusAddon.jar").Hash
[ordered]@{checkpoint='117';version='0.2.39';rendererSHA256=$renderer;managedSHA256=$managed;livePerformanceMeasured=$false} | ConvertTo-Json | Set-Content -LiteralPath "$out/regression-artifact.json" -Encoding utf8
$checks=[ordered]@{weaving='VerifyWeaving.ps1';alpha='Verify106.ps1';furniture='VerifyFurniture075.ps1';interaction='Verify084.ps1';click='Verify086.ps1';stream='Verify113.ps1';wall='Verify114.ps1';crowns='Verify103.ps1';settings='VerifySettings078.ps1';preload='Verify115.ps1';sampling='Verify079.ps1'}
foreach($check in $checks.GetEnumerator()) {
    if($check.Key -eq 'sampling') {
        $packs=@('default','library','vivid','somber')
        if(Test-Path -LiteralPath 'D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3813908957/mods/ViewpointTrueWeathers/42/media/java/client/ViewpointTrueWeathers.jar'){$packs+='trueweathers'}
        & (Join-Path $PSScriptRoot $check.Value) -GameDirectory $GameDirectory -Packs $packs *> "$out/$($check.Key).log"
    } else {
        & (Join-Path $PSScriptRoot $check.Value) -GameDirectory $GameDirectory *> "$out/$($check.Key).log"
    }
    if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/$($check.Key).log" -Tail 35;throw "117 regression failed: $($check.Key)"}
    if((Get-FileHash "$project/build/VPIsometricLighting.jar").Hash -ne $renderer -or (Get-FileHash "$project/build/ViewpointIsometricPlusAddon.jar").Hash -ne $managed){throw '117 artifact changed during regression'}
    Write-Host "117 PASS $($check.Key)"
}
& "$project/research/pzo117-texture/run.ps1" -Artifact "$project/build/VPIsometricLighting.jar" -GameDirectory $GameDirectory *> "$out/texture.log"
if($LASTEXITCODE -ne 0){Get-Content "$out/texture.log" -Tail 35;throw '117 texture storage regression failed'}
Write-Host '117 PASS texture'
foreach($entry in @(@('retirement','research/pzo117-retirement/run.ps1'),@('retirement-gpu','research/pzo117-retirement/gpu-run.ps1'))) {
    & (Join-Path $project $entry[1]) -GameDirectory $GameDirectory *> "$out/$($entry[0]).log"
    if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/$($entry[0]).log" -Tail 35;throw "117 retirement failed: $($entry[0])"}
    Write-Host "117 PASS $($entry[0])"
}
if((Get-FileHash "$project/build/VPIsometricLighting.jar").Hash -ne $renderer){throw '117 final artifact changed during regression'}
Write-Host '117 serial regression passed; gameplay FPS, low frames and allocation-stall gains remain unmeasured.'
