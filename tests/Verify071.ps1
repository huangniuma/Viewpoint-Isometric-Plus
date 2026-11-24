param([string]$GameDirectory='D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference='Stop'
$projectDirectory=Split-Path $PSScriptRoot -Parent
$vp='D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$tw='D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3813908957\mods\ViewpointTrueWeathers\42\media\java\client\ViewpointTrueWeathers.jar'
$stage=Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$classes=Join-Path $projectDirectory 'build/tests071'
$runtime="$classes;$projectDirectory/build/tests;$projectDirectory/build/tests068;$projectDirectory/build/tests070;$vp;$tw;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& java -jar "$projectDirectory/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $classes "$PSScriptRoot/NativeReceivers071Probe.java" "$PSScriptRoot/NativeWallSources071Probe.java" "$PSScriptRoot/NativeMetadata074.java"
if($LASTEXITCODE -ne 0){throw '071 probes compilation failed'}
$java=Join-Path $GameDirectory 'jre64/bin/java.exe'
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeWallSources071Probe $GameDirectory
if($LASTEXITCODE -ne 0){throw '071 native source/cache metadata failed'}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$projectDirectory/build/checkpoints/070/VPIsometricLighting.jar;$stage;$runtime" NativeReceivers071Probe default baseline070
if($LASTEXITCODE -ne 0){throw '070 wrong receivers negative control failed'}
foreach($pack in @('default','library','vivid','somber','trueweathers')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeReceivers071Probe $pack
    if($LASTEXITCODE -ne 0){throw "071 source receivers failed: $pack"}
}
Write-Host '071 source receiver regression PASS: real crossed plant slots and source-wall receiver; five selected packs, prior negative controls, native image state preserved; live visual acceptance pending.'
