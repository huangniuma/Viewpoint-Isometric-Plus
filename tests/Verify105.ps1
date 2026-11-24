param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string[]]$Packs=@('default','library','vivid','somber'))
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$classes="$p/build/tests105";New-Item -ItemType Directory -Force -Path $classes | Out-Null
$runtime="$classes;$p/build/tests082;$p/build/tests072;$p/build/tests071;$p/build/tests068;$p/build/tests070;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
$sources=@("$p/tests/ReceiverCull105Probe.java","$p/tests/ReceiverCull105Weave.java","$p/tests/LightweightShadow082Probe.java")
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$p/build/VPIsometricLighting.jar;$runtime" -d $classes @sources
if($LASTEXITCODE -ne 0){throw '105 focused compilation failed'}
$java="$GameDirectory/jre64/bin/java.exe"
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/VPIsometricLighting.jar;$runtime" ReceiverCull105Weave "$classes/query"
if($LASTEXITCODE -ne 0){throw '105 test-only receiver query weaving failed'}
foreach($pack in $Packs) {
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$classes/query;$p/build/patch-verification/woven;$p/build/VPIsometricLighting.jar;$runtime" ReceiverCull105Probe $pack
 if($LASTEXITCODE -ne 0){throw "105 receiver cull parity failed: $pack"}
}
Write-Host 'Receiver105 checks passed.'
