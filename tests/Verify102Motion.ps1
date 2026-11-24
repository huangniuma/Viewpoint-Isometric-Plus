param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$classes="$p/build/tests102-motion";New-Item -ItemType Directory -Force -Path $classes | Out-Null
$runtime="$p/build/framegen097-world;$p/build/VPIsometricLighting.jar;$classes;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp $runtime -d $classes "$p/tests/NativeMotion102Probe.java"
if($LASTEXITCODE -ne 0){throw '102 motion fixture compile failed'}
foreach($pack in @('default','library','vivid','somber')) {
 & "$GameDirectory/jre64/bin/java.exe" --enable-native-access=ALL-UNNAMED "-Dmotion102.pack=$pack" -Xverify:all -cp $runtime NativeMotion102Probe
 if($LASTEXITCODE -ne 0){throw "102 native motion failed: $pack"}
}
