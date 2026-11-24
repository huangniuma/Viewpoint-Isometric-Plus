param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string[]]$Packs=@('default','library','vivid','somber'))
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$classes="$p/build/tests105";New-Item -ItemType Directory -Force -Path $classes | Out-Null
$runtime="$classes;$p/build/tests;$p/build/tests072;$p/build/tests071;$p/build/tests068;$p/build/tests070;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$p/build/VPIsometricLighting.jar;$runtime" -d $classes "$p/tests/ShadowCache105Probe.java"
if($LASTEXITCODE -ne 0){throw '105 cache compilation failed'}
$java="$GameDirectory/jre64/bin/java.exe"
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/checkpoints/104/VPIsometricLighting.jar;$runtime" ShadowCache105Probe baseline104 library
if($LASTEXITCODE -ne 0){throw '104 cache invalidation negative failed'}
foreach($pack in $Packs) {
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/VPIsometricLighting.jar;$runtime" ShadowCache105Probe $pack
 if($LASTEXITCODE -ne 0){throw "105 shadow cache failed: $pack"}
}
Write-Host 'Cache105 checks passed.'
