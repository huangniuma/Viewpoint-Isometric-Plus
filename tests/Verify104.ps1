param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$classes="$p/build/tests104";New-Item -ItemType Directory -Force -Path $classes | Out-Null
$runtime="$classes;$p/build/tests075;$p/build/tests072;$p/build/tests071;$p/build/tests068;$p/build/tests070;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
$sources=@("$p/tests/NativeDraw104Probe.java","$p/tests/NativeOwnerUpload104Probe.java","$p/research/perf104-cpu-review/NativeOwner104Probe.java","$p/research/perf104-cpu-review/OwnerSamples104Probe.java")
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$p/build/VPIsometricLighting.jar;$runtime" -d $classes @sources
if($LASTEXITCODE -ne 0){throw '104 focused compilation failed'}
$java="$GameDirectory/jre64/bin/java.exe"
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/VPIsometricLighting.jar;$runtime" NativeDraw104Probe "$p/build/checkpoints/103/VPIsometricLighting.jar"
if($LASTEXITCODE -ne 0){throw '104 per-draw GL parity failed'}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/VPIsometricLighting.jar;$runtime" NativeOwnerUpload104Probe
if($LASTEXITCODE -ne 0){throw '104 sparse owner upload failed'}
foreach($mode in @('baseline','candidate')) {
 $stage=if($mode -eq 'baseline'){"$p/build/checkpoints/103/VPIsometricLighting.jar"}else{"$p/build/VPIsometricLighting.jar"}
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeOwner104Probe "$classes/native-$mode.txt"
 if($LASTEXITCODE -ne 0){throw "104 native owner consumer failed: $mode"}
}
if((Get-FileHash "$classes/native-baseline.txt").Hash -ne (Get-FileHash "$classes/native-candidate.txt").Hash){throw '104 native IDs/alpha/order changed'}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/VPIsometricLighting.jar;$runtime" OwnerSamples104Probe parity
if($LASTEXITCODE -ne 0){throw '104 collection owner parity failed'}
Write-Host 'Performance104 checks passed: exact native source IDs/alpha/order, original GL state across256 cases and sparse upload versus full-table GPU equality.'
