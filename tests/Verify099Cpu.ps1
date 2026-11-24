param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$stage="$p/build/VPIsometricLighting.jar"
$runtime="$p/build/tests099;$p/build/tests075;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path "$p/build/tests099" | Out-Null
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d "$p/build/tests099" "$p/tests/NativeCaps099Probe.java" "$p/tests/FrameRate099Probe.java" "$p/tests/NativeView087Probe.java"
if($LASTEXITCODE -ne 0){throw '099 CPU probes did not compile'}
$java="$GameDirectory/jre64/bin/java.exe"
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeCaps099Probe
if($LASTEXITCODE -ne 0){throw '099 wall capability parity failed'}
$woven="$p/build/rate099-woven/viewpoint"
New-Item -ItemType Directory -Force -Path $woven | Out-Null
Copy-Item -LiteralPath "$p/build/patch-verification/woven/viewpoint/Hooks.class" -Destination "$woven/Hooks.class" -Force
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/rate099-woven;$stage;$runtime" FrameRate099Probe
if($LASTEXITCODE -ne 0){throw '099 frame generation rate/lifecycle failed'}
$before=& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/checkpoints/098/VPIsometricLighting.jar;$runtime" NativeView087Probe 2>&1
if($LASTEXITCODE -ne 0){throw '099 native snapshot baseline failed'}
$after=& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeView087Probe 2>&1
if($LASTEXITCODE -ne 0){throw '099 native snapshot failed'}
$a=@($before | Where-Object {"$_" -like 'VIEW087 *'});$b=@($after | Where-Object {"$_" -like 'VIEW087 *'})
if($a.Count -ne 1 -or $b.Count -ne 1 -or "$a" -ne "$b"){throw '099 visibility snapshot changed'}
$b | Write-Output
Write-Host 'Visibility099 parity PASS: unchanged wall caps, native light refresh and full visibility snapshot.'
& python -X utf8 "$p/research/perf099/benchmark.py"
if($LASTEXITCODE -ne 0){throw '099 isolated CPU benchmark failed'}
Write-Host 'Performance099 CPU checks passed.'
