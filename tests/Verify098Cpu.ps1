param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
& python -X utf8 "$p/research/perf098-cpu-review/run-classification.py" baseline097
if($LASTEXITCODE -ne 0){throw '098 baseline native classification failed'}
& python -X utf8 "$p/research/perf098-cpu-review/run-classification.py"
if($LASTEXITCODE -ne 0){throw '098 native classification reuse/lifecycle failed'}
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$stage="$p/build/VPIsometricLighting.jar"
$runtime="$p/build/tests098;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path "$p/build/tests098" | Out-Null
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d "$p/build/tests098" "$p/tests/NativeView087Probe.java"
if($LASTEXITCODE -ne 0){throw '098 native view fixture compilation failed'}
$java="$GameDirectory/jre64/bin/java.exe"
$before=& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/checkpoints/097/VPIsometricLighting.jar;$runtime" NativeView087Probe 2>&1
if($LASTEXITCODE -ne 0){throw '098 native view baseline failed'}
$after=& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeView087Probe 2>&1
if($LASTEXITCODE -ne 0){throw '098 native view union failed'}
$a=@($before | Where-Object {"$_" -like 'VIEW087 *'});$b=@($after | Where-Object {"$_" -like 'VIEW087 *'})
if($a.Count -ne 1 -or $b.Count -ne 1 -or "$a" -ne "$b"){throw '098 visibility data changed'}
$b | Write-Output
Write-Host 'Visibility098 parity PASS: identical native blackout, floors, halos and endpoint bytes versus097.'
& python -X utf8 "$p/research/perf098-cpu-review/run-benchmark.py"
if($LASTEXITCODE -ne 0){throw '098 final artifact CPU benchmark failed'}
Write-Host 'Performance098 CPU checks passed.'
