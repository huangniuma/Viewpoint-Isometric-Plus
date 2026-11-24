param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string[]]$Packs=@('default','library','vivid','somber','trueweathers'))
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$tw='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3813908957/mods/ViewpointTrueWeathers/42/media/java/client/ViewpointTrueWeathers.jar'
$runtime="$p/build/tests082;$p/build/tests;$p/build/tests068;$p/build/tests070;$p/build/tests071;$p/build/tests072;$p/build/tests073;$p/build/tests079;$vp;$tw;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
$stage="$p/build/VPIsometricLighting.jar"
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d "$p/build/tests082" "$p/tests/LightweightShadow082Probe.java"
if($LASTEXITCODE -ne 0){throw '082 probe compilation failed'}
$woven="$p/build/lightweight082-woven"
New-Item -ItemType Directory -Force $woven | Out-Null
Copy-Item -LiteralPath "$p/build/patch-verification/woven/viewpoint" -Destination $woven -Recurse -Force
$java="$GameDirectory/jre64/bin/java.exe"
foreach($pack in $Packs) {
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$woven;$stage;$runtime" LightweightShadow082Probe $pack
 if($LASTEXITCODE -ne 0){throw "Lightweight shadows regression failed: $pack"}
}
Write-Host 'Lightweight082 checks passed: selected caster geometry, native/model output, all receivers, cache switch restoration and supported shader packs.'
