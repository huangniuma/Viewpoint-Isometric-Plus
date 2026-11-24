param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$stage="$p/build/VPIsometricLighting.jar";$base="$p/build/checkpoints/109/VPIsometricLighting.jar"
$runtime="$p/build/tests110;$p/build/tests108;$p/build/tests088;$p/build/tests073;$p/build/tests072;$p/build/tests071;$p/build/tests;$p/build/tests068;$p/build/tests070;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d "$p/build/tests110" "$p/tests/NativeParity110Probe.java" "$p/tests/Spot110Probe.java"
if($LASTEXITCODE -ne 0){throw '110 parity probes failed compilation'}
$java="$GameDirectory/jre64/bin/java.exe"
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" Spot110Probe $base
if($LASTEXITCODE -ne 0){throw '110 spot parity failed'}
foreach($pack in @('default','library','vivid','somber')) {
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeParity110Probe $pack $base
 if($LASTEXITCODE -ne 0){throw "110 native compositor parity failed $pack"}
}
Write-Host 'Compatibility110 GPU checks passed.'
