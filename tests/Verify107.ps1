param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$classes="$p/build/tests107";New-Item -ItemType Directory -Force -Path $classes | Out-Null
$runtime="$classes;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
$sources=@("$p/tests/SourceRetire107Probe.java","$p/tests/RangeOrder107Probe.java","$p/tests/Range107GpuProbe.java","$p/tests/CommandQueue107Weave.java","$p/tests/CommandQueue107Probe.java","$p/research/perf107-allocation/Range107Probe.java")
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$p/build/VPIsometricLighting.jar;$runtime" -d $classes @sources
if($LASTEXITCODE -ne 0){throw '107 focused compilation failed'}
$java="$GameDirectory/jre64/bin/java.exe"
foreach($mode in @('baseline','candidate')) {
 $stage=if($mode -eq 'baseline'){"$p/build/checkpoints/106/VPIsometricLighting.jar"}else{"$p/build/VPIsometricLighting.jar"}
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" SourceRetire107Probe > "$classes/retire-$mode.txt"
 if($LASTEXITCODE -ne 0){throw "107 retirement failed: $mode"}
 Get-Content "$classes/retire-$mode.txt"
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" Range107GpuProbe $mode > "$classes/range-$mode.txt"
 if($LASTEXITCODE -ne 0){throw "107 GPU range failed: $mode"}
 Get-Content "$classes/range-$mode.txt"
 $woven="$classes/commands-$mode"
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" CommandQueue107Weave $woven
 if($LASTEXITCODE -ne 0){throw "107 test-only command weave failed: $mode"}
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$woven;$stage;$runtime" CommandQueue107Probe $mode > "$classes/commands-$mode.txt"
 if($LASTEXITCODE -ne 0){throw "107 immutable command failed: $mode"}
 Get-Content "$classes/commands-$mode.txt"
}
foreach($name in @('retire','range')) {
 if((Get-FileHash "$classes/$name-baseline.txt").Hash -ne (Get-FileHash "$classes/$name-candidate.txt").Hash){throw "107 $name exact output mismatch"}
}
$a=Get-Content "$classes/commands-baseline.txt" | Where-Object {$_ -like 'COMMAND107 PASS *'}
$b=Get-Content "$classes/commands-candidate.txt" | Where-Object {$_ -like 'COMMAND107 PASS *'}
if($a -ne $b){throw '107 command payload changed'}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/VPIsometricLighting.jar;$runtime" RangeOrder107Probe
if($LASTEXITCODE -ne 0){throw '107 independent signed/stable key order failed'}
Write-Host 'Stability107 checks passed: actual deferred retirement queue/table equality, GPU range words and immutable delayed draw commands; no live hitch claim.'
