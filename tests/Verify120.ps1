param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$out=Join-Path $project 'research/poolpit120-final'
$stage=Join-Path $project 'build/VPIsometricLighting.jar'
$managed=Join-Path $project 'build/ViewpointIsometricPlusAddon.jar'
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$buddy='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3619862853/mods/ZombieBuddy/libs/ZombieBuddy.jar'
$pool='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3816856700/mods/yeahbitch/42/media/java/client/PoolPitVP.jar'
New-Item -ItemType Directory -Force -Path $out,"$out/test-classes" | Out-Null
$rendererHash=(Get-FileHash -LiteralPath $stage).Hash
$managedHash=(Get-FileHash -LiteralPath $managed).Hash
function Assert-Artifact {
    if((Get-FileHash -LiteralPath $stage).Hash -ne $rendererHash -or (Get-FileHash -LiteralPath $managed).Hash -ne $managedHash){throw '120 artifact changed during verification'}
}
$deps="$vp;$buddy;$GameDirectory/projectzomboid.jar;$GameDirectory"
$runtime="$stage;$out/test-classes;$deps"
$java="$GameDirectory/jre64/bin/java.exe"
@{checkpoint='120';version='0.2.42';rendererSHA256=$rendererHash;managedSHA256=$managedHash;poolPitSHA256=(Get-FileHash -LiteralPath $pool).Hash;scope='Independent CPU/hidden GL; no game attach, save or settings writes';liveVisualAcceptance=$false} | ConvertTo-Json | Set-Content -LiteralPath "$out/regression-artifact.json" -Encoding UTF8
& java -jar "$project/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp $runtime -d "$out/test-classes" "$PSScriptRoot/PoolPitNative120Probe.java" "$PSScriptRoot/PoolPitMode120Probe.java" *> "$out/compile.log"
if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/compile.log";throw '120 probes compilation failed'}
foreach($present in @('false','true')) {
    $cp=if($present -eq 'true'){"$runtime;$pool"}else{$runtime}
    & $java -Xverify:all -cp $cp PoolPitMode120Probe $present *> "$out/optional-$present.log"
    if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/optional-$present.log";throw '120 optional dependency mode failed'}
    Get-Content -LiteralPath "$out/optional-$present.log" -Tail 1
}
# Stage precedes all probe directories: no locally compiled production class can mask the release JAR.
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$runtime;$pool" PoolPitNative120Probe *> "$out/native-water.log"
if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/native-water.log" -Tail 30;throw '120 native water GPU failed'}
Get-Content -LiteralPath "$out/native-water.log" -Tail 1
Assert-Artifact
$checks=[ordered]@{weaving='VerifyWeaving.ps1';settings='VerifySettings078.ps1';camera='Verify118.ps1';furniture='Verify119.ps1';fade='VerifyFurniture075.ps1';interaction='Verify084.ps1';click='Verify086.ps1';sampling='Verify079.ps1'}
foreach($check in $checks.GetEnumerator()) {
    if($check.Key -eq 'sampling') {
        & "$PSScriptRoot/$($check.Value)" -GameDirectory $GameDirectory -Packs @('default','library','vivid','somber') *> "$out/$($check.Key).log"
    } else {
        & "$PSScriptRoot/$($check.Value)" -GameDirectory $GameDirectory *> "$out/$($check.Key).log"
    }
    if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/$($check.Key).log" -Tail 35;throw "120 regression failed: $($check.Key)"}
    Assert-Artifact
    Write-Host "120 PASS $($check.Key)"
}
& "$project/research/pzo117-texture/run.ps1" -Artifact $stage -GameDirectory $GameDirectory *> "$out/texture.log"
if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/texture.log" -Tail 30;throw '120 native texture parity failed'}
Assert-Artifact
Write-Host '120 PASS texture'
foreach($arm in @('iso-first','pool-first')) {
    & "$project/research/poolpit120-shader/Run.ps1" -Arm $arm *> "$out/shader-$arm.log"
    if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/shader-$arm.log" -Tail 30;throw "120 shader hook order failed: $arm"}
    Copy-Item -LiteralPath "$project/research/poolpit120-shader/$arm.log" -Destination "$out/shader-$arm-full.log" -Force
    Assert-Artifact
    Write-Host "120 PASS shader-$arm"
}
& "$project/research/poolpit120-provenance/Run.ps1" *> "$out/provenance.log"
if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/provenance.log" -Tail 30;throw '120 actual native provenance failed'}
Assert-Artifact
Write-Host '120 PASS provenance'
Write-Host '120 scoped regression PASS: optional modes, real PoolPit GPU, native ownership/occlusion, both shader hook orders, existing native sampling and interactions. In-game appearance requires separate acceptance.'
