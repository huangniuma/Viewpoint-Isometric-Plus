param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$stage="$p/build/VPIsometricLighting.jar"
$runtime="$p/build/tests086;$p/build/tests084;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
$fixture="$p/build/click086-fixture"
New-Item -ItemType Directory -Force -Path "$p/build/tests086" | Out-Null
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d "$p/build/tests086" "$PSScriptRoot/MouseLatency086Probe.java" "$PSScriptRoot/NativeClick086Probe.java"
if($LASTEXITCODE -ne 0){throw '086 compilation failed'}
& python -X utf8 "$PSScriptRoot/PrepareClick086.py" "$GameDirectory/projectzomboid.jar" "$p/build/patch-verification/woven" $fixture
if($LASTEXITCODE -ne 0){throw '086 signed package fixture failed'}
$java="$GameDirectory/jre64/bin/java.exe"
$negative=& $java --enable-native-access=ALL-UNNAMED '-Dclick086.baseline=true' -Xverify:all -cp "$p/build/checkpoints/085/VPIsometricLighting.jar;$runtime" MouseLatency086Probe 2>&1
$negative | Write-Output
if($LASTEXITCODE -eq 0 -or ($negative -join "`n") -notmatch 'Query used queued capture cursor'){throw '085 did not reproduce queued pointer bug'}
Write-Host 'Previous085 negative control PASS: queued render query uses old mouse position.'
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$stage;$runtime" MouseLatency086Probe
if($LASTEXITCODE -ne 0){throw '086 cross-drawer GPU latency regression failed'}
Push-Location "$p/build/lua-fixture"
try{
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$stage;$runtime" NativeClick086Probe $GameDirectory
 if($LASTEXITCODE -ne 0){throw '086 native click/Lua action regression failed'}
}finally{Pop-Location}
Write-Host 'Click086 checks passed: actual input pixel, cross-drawer collection, native dispatch and original Lua click rules.'
