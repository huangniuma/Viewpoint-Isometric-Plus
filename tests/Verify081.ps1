param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string[]]$Packs=@('default','library','vivid','somber','trueweathers'))
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$tw='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3813908957/mods/ViewpointTrueWeathers/42/media/java/client/ViewpointTrueWeathers.jar'
$runtime="$p/build/tests081;$p/build/patch-verification/probe;$p/build/tests080;$p/build/tests079;$p/build/tests;$p/build/tests068;$p/build/tests070;$p/build/tests071;$p/build/tests072;$p/build/tests073;$vp;$tw;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
$stage="$p/build/VPIsometricLighting.jar"
$sources=@('NativeBootstrap081Probe.java','NativeFloorValidity081Probe.java','NativeFloorSampling081Probe.java','NativeFloorShadow081Probe.java') | ForEach-Object {Join-Path $PSScriptRoot $_}
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d "$p/build/tests081" @sources
if($LASTEXITCODE -ne 0){throw '081 probe compilation failed'}
$java="$GameDirectory/jre64/bin/java.exe"
$negative=& $java --enable-native-access=ALL-UNNAMED -cp "$p/build/checkpoints/080/VPIsometricLighting.jar;$runtime" NativeFloorShadow081Probe default 2>&1
if($LASTEXITCODE -eq 0 -or ($negative -join "`n") -notmatch 'Overlapping foreground skipped floor shadow') {throw '080 did not reproduce overlapping-owner floor shadow gap'}
$negative | Write-Output
Write-Host 'Previous080 floor shadow negative control PASS.'
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeBootstrap081Probe "$p/build/patch-verification/woven" $GameDirectory
if($LASTEXITCODE -ne 0){throw 'Early default capture regression failed'}
foreach($probe in @('NativeFloorValidity081Probe','NativeFloorSampling081Probe')) {
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" $probe $GameDirectory
 if($LASTEXITCODE -ne 0){throw "Floor capture regression failed: $probe"}
}
foreach($pack in $Packs) {
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeFloorShadow081Probe $pack
 if($LASTEXITCODE -ne 0){throw "Floor compositor regression failed: $pack"}
}
Write-Host 'White edges081 checks passed: early default hook, isolated floor moments, native chunk filters/state, selected-pack shadows and previous artifact negative control.'
