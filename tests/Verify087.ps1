param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$stage="$p/build/VPIsometricLighting.jar"
$runtime="$p/build/tests087;$p/build/tests084;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path "$p/build/tests087" | Out-Null
$sources=@('GameAccess087Probe','Session087Probe','NativeView087Probe') | ForEach-Object {Join-Path $PSScriptRoot ($_+'.java')}
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d "$p/build/tests087" @sources
if($LASTEXITCODE -ne 0){throw '087 probe compilation failed'}
$java="$GameDirectory/jre64/bin/java.exe"
$negative=& $java --enable-native-access=ALL-UNNAMED '-Dsession087.baseline=true' -Xverify:all -cp "$p/build/checkpoints/086/VPIsometricLighting.jar;$runtime" Session087Probe 2>&1
$negative | Write-Output
if($LASTEXITCODE -ne 0 -or ($negative -join "`n") -notmatch 'Previous086 negative control PASS:'){throw '086 stale-source negative control failed'}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" Session087Probe
if($LASTEXITCODE -ne 0){throw '087 session/client regression failed'}
$baseline=& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/checkpoints/086/VPIsometricLighting.jar;$runtime" NativeView087Probe 2>&1
if($LASTEXITCODE -ne 0){throw '086 visibility oracle failed'}
$current=& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeView087Probe 2>&1
if($LASTEXITCODE -ne 0){throw '087 sparse visibility failed'}
$before=@($baseline | Where-Object {"$_" -like 'VIEW087 *'});$after=@($current | Where-Object {"$_" -like 'VIEW087 *'})
if($before.Count -ne 1 -or $after.Count -ne 1 -or "$before" -ne "$after"){throw 'Visibility pixels changed from accepted086'}
$after | Write-Output
Write-Host 'Visibility087 parity PASS: byte-identical native visibility/blackout/topology snapshot versus086.'
foreach($pass in 1..3){
 foreach($kind in @('baseline','current')){
  $artifact=if($kind -eq 'baseline'){"$p/build/checkpoints/086/VPIsometricLighting.jar"}else{$stage}
  & $java -Xverify:all -cp "$artifact;$runtime" GameAccess087Probe > "$p/research/compat087/bench-$kind-$pass.log"
  if($LASTEXITCODE -ne 0){throw "Reflection access semantics or benchmark failed: $kind pass$pass"}
 }
}
& python -X utf8 "$PSScriptRoot/Analyze087.py"
if($LASTEXITCODE -ne 0){throw '087 benchmark analysis failed'}
Write-Host 'Compatibility087 checks passed: network-client/local-player boundaries, session/streamed-source safety, visibility parity and measured shared-access optimization.'
