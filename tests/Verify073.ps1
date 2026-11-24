param([string]$GameDirectory='D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid',[string[]]$Packs=@('default','library','vivid','somber','trueweathers'))
$ErrorActionPreference='Stop'
$projectDirectory=Split-Path $PSScriptRoot -Parent
$vp='D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$tw='D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3813908957\mods\ViewpointTrueWeathers\42\media\java\client\ViewpointTrueWeathers.jar'
$stage=Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$classes=Join-Path $projectDirectory 'build/tests073'
$runtime="$classes;$projectDirectory/build/tests072;$projectDirectory/build/tests071;$projectDirectory/build/tests;$projectDirectory/build/tests068;$projectDirectory/build/tests070;$vp;$tw;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& java -jar "$projectDirectory/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $classes "$PSScriptRoot/NativeSource073Probe.java" "$PSScriptRoot/NativeSourceMetadata073Probe.java" "$PSScriptRoot/NativeSourceProducer073Probe.java" "$PSScriptRoot/NativeMetadata074.java" "$PSScriptRoot/NativeSource074Probe.java"
if($LASTEXITCODE -ne 0){throw '073 source probes compilation failed'}
$java=Join-Path $GameDirectory 'jre64/bin/java.exe'
$woven=Join-Path $projectDirectory 'build/source073-woven'
foreach($file in @('zombie/iso/objects/IsoTree.class','viewpoint/world/Cook$Cooker.class')) {
    New-Item -ItemType Directory -Force -Path (Split-Path (Join-Path $woven $file)) | Out-Null
    Copy-Item -LiteralPath (Join-Path $projectDirectory "build/patch-verification/woven/$file") -Destination (Join-Path $woven $file) -Force
}
& $java -Xverify:all -cp "$woven;$stage;$runtime" NativeSourceProducer073Probe
if($LASTEXITCODE -ne 0){throw '073 actual woven source producers failed'}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeSourceMetadata073Probe $GameDirectory
if($LASTEXITCODE -ne 0){throw '073 actual native metadata failed'}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$projectDirectory/build/checkpoints/072/VPIsometricLighting.jar;$stage;$runtime" NativeSource073Probe default baseline072
if($LASTEXITCODE -ne 0){throw '072 missing-source negative failed'}
foreach($pack in $Packs) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeSource073Probe $pack
    if($LASTEXITCODE -ne 0){throw "073 native sources failed: $pack"}
}

foreach($pack in $Packs) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeSource074Probe $pack
    if($LASTEXITCODE -ne 0){throw "074 alpha/physical source receivers failed: $pack"}
}
