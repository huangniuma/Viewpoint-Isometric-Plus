param([string]$GameDirectory='D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference='Stop'
$projectDirectory=Split-Path $PSScriptRoot -Parent
$vp='D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$tw='D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3813908957\mods\ViewpointTrueWeathers\42\media\java\client\ViewpointTrueWeathers.jar'
$stage=Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$classes=Join-Path $projectDirectory 'build/tests070'
$runtime="$classes;$projectDirectory/build/tests;$projectDirectory/build/tests068;$vp;$tw;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& java -jar "$projectDirectory/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $classes "$PSScriptRoot/NativeSolarRoom070Probe.java" "$PSScriptRoot/NativeCanopy070Probe.java"
if($LASTEXITCODE -ne 0){throw '070 probes compilation failed'}
$java=Join-Path $GameDirectory 'jre64/bin/java.exe'
& $java -Xverify:all -cp "$stage;$runtime" NativeSolarRoom070Probe
if($LASTEXITCODE -ne 0){throw '070 native room-mask producer failed'}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$projectDirectory/build/checkpoints/069/VPIsometricLighting.jar;$stage;$runtime" NativeCanopy070Probe default baseline069
if($LASTEXITCODE -ne 0){throw '069 roofed-yard clipping negative control failed'}
foreach($pack in @('default','library','vivid','somber','trueweathers')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeCanopy070Probe $pack
    if($LASTEXITCODE -ne 0){throw "070 canopy/room reception failed: $pack"}
}
Write-Host '070 canopy receiver regression PASS: same-frame native room classification, real VP solar maps and independent selected-shade reference; roofed roomless ground and foreground, tall owner floor, enclosed/unknown protection, all-effects off, native sight, alpha/depth/stencil and five packs. Live visual acceptance pending.'
