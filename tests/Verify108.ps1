param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string[]]$Packs=@('default','library','vivid','somber'))
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$stage="$p/build/VPIsometricLighting.jar"
$runtime="$p/build/tests108;$p/build/tests088;$p/build/tests073;$p/build/tests072;$p/build/tests071;$p/build/tests;$p/build/tests068;$p/build/tests070;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path "$p/build/tests108" | Out-Null
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d "$p/build/tests108" "$p/tests/NativePerformance108Probe.java" "$p/tests/PerformanceWeave108.java"
if($LASTEXITCODE -ne 0){throw '108 performance probe compilation failed'}
$java="$GameDirectory/jre64/bin/java.exe"
$woven="$p/build/perf108-woven"
& $java -Xverify:all -cp "$stage;$runtime" PerformanceWeave108 $stage $woven
if($LASTEXITCODE -ne 0){throw '108 GPU draw timer fixture failed'}
foreach($pack in $Packs) {
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$woven;$stage;$runtime" NativePerformance108Probe $pack "$p/build/checkpoints/107/VPIsometricLighting.jar"
 if($LASTEXITCODE -ne 0){throw "108 GPU performance/parity failed: $pack"}
}
Write-Host 'Performance108 GPU checks passed.'
