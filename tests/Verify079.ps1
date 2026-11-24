param([string]$GameDirectory='D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference='Stop'
$projectDirectory=Split-Path $PSScriptRoot -Parent
$vp='D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$tw='D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3813908957\mods\ViewpointTrueWeathers\42\media\java\client\ViewpointTrueWeathers.jar'
$stage=Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$classes=Join-Path $projectDirectory 'build/tests079'
$runtime="$classes;$projectDirectory/build/tests;$projectDirectory/build/tests068;$projectDirectory/build/tests070;$projectDirectory/build/tests071;$projectDirectory/build/tests072;$projectDirectory/build/tests073;$vp;$tw;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources=@('NativeChunkSampling079Probe.java','NativeOwner079Probe.java','NativeSource079Probe.java') | ForEach-Object { Join-Path $PSScriptRoot $_ }
& java -jar "$projectDirectory/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $classes @sources
if($LASTEXITCODE -ne 0){throw 'Sampling/continuous-alpha probes compilation failed'}
$java=Join-Path $GameDirectory 'jre64/bin/java.exe'
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" LinkageProbe "$projectDirectory/build/classes"
if($LASTEXITCODE -ne 0){throw 'JVM linkage verification failed'}
$negative=& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$projectDirectory/build/checkpoints/078/VPIsometricLighting.jar;$runtime" NativeChunkSampling079Probe $GameDirectory 2>&1
if($LASTEXITCODE -eq 0 -or ($negative -join "`n") -notmatch 'Aux min filter not native'){throw 'Pinned previous artifact did not reproduce the sampling defect'}
Write-Host 'Previous packaged artifact negative control PASS: auxiliary sampling mismatch reproduced.'
foreach($probe in @('NativeChunkSampling079Probe','NativeOutputProbe','NativeCaptureProbe','NativeWallSources071Probe')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" $probe $GameDirectory
    if($LASTEXITCODE -ne 0){throw "Native capture/sampling regression failed: $probe"}
}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeOwner079Probe
if($LASTEXITCODE -ne 0){throw 'Continuous-alpha owner decoder failed'}
foreach($pack in @('default','library','vivid','somber','trueweathers')) {
    foreach($probe in @('NativeSource079Probe','NativeSource073Probe','NativeSource074Probe','NativeReceivers071Probe')) {
        & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" $probe $pack
        if($LASTEXITCODE -ne 0){throw "Shadow receiver regression failed: $probe $pack"}
    }
}
Write-Host 'Sampling/continuous-alpha regression PASS: native cached chunks, texture/sampler state, original RGB, wall/source interactions and five shader packs; live visual acceptance is separate.'
