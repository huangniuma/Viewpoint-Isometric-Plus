param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string[]]$Packs=@('default','library','vivid','somber'))
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$stage="$p/build/VPIsometricLighting.jar"
$runtime="$p/build/tests095;$p/build/tests075-tree;$p/build/tests058;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
$fixture="$p/build/tree095-woven";$oldFixture="$p/build/tree095-old"
New-Item -ItemType Directory -Force -Path "$p/build/tests095" | Out-Null
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d "$p/build/tests095" "$p/tests/TreeRow095Probe.java" "$p/tests/TreeOrder095Probe.java"
if($LASTEXITCODE -ne 0){throw '095 compilation failed'}
$java="$GameDirectory/jre64/bin/java.exe"
Push-Location $p
try {
 & $java -Xverify:all -cp "$stage;$runtime" TreeOrder095Probe
 if($LASTEXITCODE -ne 0){throw '095 dense-row boundary failed'}
 & $java -Xverify:all -cp "$p/build/checkpoints/094/VPIsometricLighting.jar;$runtime" NativeTreeRotation058Probe weave "$GameDirectory/projectzomboid.jar" '-' $oldFixture fixed
 if($LASTEXITCODE -ne 0){throw '095 old tree consumer weave failed'}
 & $java -Xverify:all -cp "$stage;$runtime" NativeTreeRotation058Probe weave "$GameDirectory/projectzomboid.jar" '-' $fixture fixed
 if($LASTEXITCODE -ne 0){throw '095 current tree consumer weave failed'}
 foreach($folder in @($fixture,$oldFixture)) {
  New-Item -ItemType Directory -Force -Path "$folder/viewpoint/platform" | Out-Null
  foreach($name in @('Shaders','GlProgram')){Copy-Item -LiteralPath "$p/build/patch-verification/woven/viewpoint/platform/$name.class" -Destination "$folder/viewpoint/platform/$name.class" -Force}
 }
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$oldFixture;$p/build/checkpoints/094/VPIsometricLighting.jar;$runtime" TreeRow095Probe baseline094 default
 if($LASTEXITCODE -ne 0){throw '095 old tree negative control failed'}
 foreach($pack in $Packs) {
  & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$stage;$runtime" TreeRow095Probe $pack
  if($LASTEXITCODE -ne 0){throw "095 same-row GPU failed: $pack"}
 }
}finally{Pop-Location}
Write-Host 'Tree095 checks passed: real same-row tree instability reproduced, bounded consistent depth/order, dual layers, map origins, near-quarter yaw, alpha and foreground scope.'
