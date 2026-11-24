param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$classes="$p/build/tests103";New-Item -ItemType Directory -Force -Path $classes | Out-Null
$runtime="$classes;$p/build/tests072;$p/build/tests071;$p/build/tests;$p/build/tests068;$p/build/tests070;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$p/build/VPIsometricLighting.jar;$runtime" -d $classes "$p/tests/NativeCrown103Probe.java"
if($LASTEXITCODE -ne 0){throw '103 overlap fixture compilation failed'}
$java="$GameDirectory/jre64/bin/java.exe"
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/checkpoints/102/VPIsometricLighting.jar;$runtime" NativeCrown103Probe default baseline102
if($LASTEXITCODE -ne 0){throw '102 interior-card negative failed'}
foreach($pack in @('default','library','vivid','somber')) {
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/VPIsometricLighting.jar;$runtime" NativeCrown103Probe $pack
 if($LASTEXITCODE -ne 0){throw "103 continuous overlap failed: $pack"}
}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/VPIsometricLighting.jar;$runtime" NativeSurfaceReceiverProbe
if($LASTEXITCODE -ne 0){throw '103 model/vehicle/external receiver regression failed'}
Write-Host 'Crown103 checks passed: two overlapping owners, reversed submission, camera/sun changes, 102 negative, external model/character/vehicle reception and native alpha/depth/stencil.'
