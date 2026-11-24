param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$classes="$p/build/tests106";New-Item -ItemType Directory -Force -Path $classes | Out-Null
$runtime="$classes;$p/build/tests075;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
$sources=@("$p/tests/NativeAlpha106Probe.java","$p/research/perf106-visibility/ClassificationCache106Probe.java","$p/research/perf104-cpu-review/NativeOwner104Probe.java")
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$p/build/VPIsometricLighting.jar;$runtime" -d $classes @sources
if($LASTEXITCODE -ne 0){throw '106 focus compilation failed'}
$java="$GameDirectory/jre64/bin/java.exe"
foreach($mode in @('baseline','candidate')) {
 $stage=if($mode -eq 'baseline'){"$p/build/checkpoints/105/VPIsometricLighting.jar"}else{"$p/build/VPIsometricLighting.jar"}
 $out=& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeAlpha106Probe
 if($LASTEXITCODE -ne 0){throw "106 native alpha failed: $mode"}
 $out | Set-Content -LiteralPath "$classes/alpha-$mode.txt" -Encoding utf8
 $out | Write-Output
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" ClassificationCache106Probe
 if($LASTEXITCODE -ne 0){throw "106 capture classifier failed: $mode"}
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeOwner104Probe "$classes/owners-$mode.txt"
 if($LASTEXITCODE -ne 0){throw "106 real native source consumer failed: $mode"}
 $pzoRuntime="$classes;$p/build/tests075;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$p/research/perf099-cpu-review/projectzomboid-unsigned-test-only.jar;$GameDirectory"
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$GameDirectory/pzopt/overrides;$stage;$pzoRuntime" NativeOwner104Probe "$classes/pzo-owners-$mode.txt"
 if($LASTEXITCODE -ne 0){throw "106 effective PZO source consumer failed: $mode"}
}
foreach($name in @('alpha','owners','pzo-owners')) {
 if((Get-FileHash "$classes/$name-baseline.txt").Hash -ne (Get-FileHash "$classes/$name-candidate.txt").Hash){throw "106 exact $name output mismatch"}
}
Write-Host 'Performance106 checks passed: exact native alpha/internal virtual callbacks, same-capture classifier scope, native and PZO owner/alpha/ID/click order parity; no live FPS measurement.'
