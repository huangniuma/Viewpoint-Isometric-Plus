param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string[]]$Packs=@('default','library','vivid','somber'))
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$stage="$p/build/VPIsometricLighting.jar"
$runtime="$p/build/tests099;$p/build/tests088;$p/build/tests073;$p/build/tests072;$p/build/tests071;$p/build/tests;$p/build/tests068;$p/build/tests070;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path "$p/build/tests099" | Out-Null
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d "$p/build/tests099" "$p/tests/NativePerformance098Probe.java" "$p/tests/PerformanceWeave098.java"
if($LASTEXITCODE -ne 0){throw '099 performance probe compilation failed'}
$java="$GameDirectory/jre64/bin/java.exe"
$woven="$p/build/perf099-woven"
& $java -Xverify:all -cp "$stage;$runtime" PerformanceWeave098 $stage $woven
if($LASTEXITCODE -ne 0){throw '099 GPU draw timer fixture failed'}
foreach($pack in $Packs) {
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$woven;$stage;$runtime" NativePerformance098Probe $pack "$p/build/checkpoints/098/VPIsometricLighting.jar"
 if($LASTEXITCODE -ne 0){throw "099 GPU performance/parity failed: $pack"}
}
Write-Host 'Performance099 GPU checks passed.'
