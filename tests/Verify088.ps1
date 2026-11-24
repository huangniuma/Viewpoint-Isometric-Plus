param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string[]]$Packs=@('default','library','vivid','somber','trueweathers'))
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$tw='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3813908957/mods/ViewpointTrueWeathers/42/media/java/client/ViewpointTrueWeathers.jar'
$runtime="$p/build/tests088;$p/build/tests073;$p/build/tests072;$p/build/tests071;$p/build/tests;$p/build/tests068;$p/build/tests070;$vp;$tw;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
$stage="$p/build/VPIsometricLighting.jar"
New-Item -ItemType Directory -Force -Path "$p/build/tests088" | Out-Null
$sources=@('NativeTable088Probe','NativeTableReceiver088Probe','NativeSourceLifetime088Probe') | ForEach-Object {Join-Path $PSScriptRoot ($_+'.java')}
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d "$p/build/tests088" @sources
if($LASTEXITCODE -ne 0){throw '088 probe compilation failed'}
$java="$GameDirectory/jre64/bin/java.exe"
foreach($probe in @('NativeTableReceiver088Probe','NativeSourceLifetime088Probe')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" $probe
    if($LASTEXITCODE -ne 0){throw "088 regression failed: $probe"}
}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/checkpoints/087/VPIsometricLighting.jar;$runtime" NativeSourceLifetime088Probe baseline087
if($LASTEXITCODE -ne 0){throw '087 source lifetime negative failed'}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/checkpoints/087/VPIsometricLighting.jar;$runtime" NativeTable088Probe default baseline087
if($LASTEXITCODE -ne 0){throw '087 tabletop negative failed'}
foreach($pack in $Packs) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeTable088Probe $pack
    if($LASTEXITCODE -ne 0){throw "088 real table regression failed: $pack"}
}
Write-Host 'Tables088 checks passed: installed furniture meshes, physical/external shadows, source ownership and upper/lower planes, cached-source lifetime, five shader packs and087 negative controls.'
