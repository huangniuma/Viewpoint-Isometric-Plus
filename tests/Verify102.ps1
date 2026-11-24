param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$classes="$p/build/tests102";New-Item -ItemType Directory -Force -Path $classes | Out-Null
$runtime="$classes;$p/build/tests097;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$p/build/VPIsometricLighting.jar;$runtime" -d $classes "$p/tests/NativeGeneration102Probe.java"
if($LASTEXITCODE -ne 0){throw '102 native producer fixture compile failed'}
foreach($mode in @('baseline101','current')) {
 $stage=if($mode -eq 'baseline101'){"$p/build/checkpoints/101/VPIsometricLighting.jar"}else{"$p/build/VPIsometricLighting.jar"}
 & "$GameDirectory/jre64/bin/java.exe" --enable-native-access=ALL-UNNAMED '-Dviewpoint.natives=C:/Users/Win10/Zomboid/viewpoint-natives' -Xverify:all -cp "$p/build/framegen097-woven;$stage;$runtime" viewpoint.render.NativeGeneration102Probe $mode
 if($LASTEXITCODE -ne 0){throw "102 actual native producer failed: $mode"}
}
