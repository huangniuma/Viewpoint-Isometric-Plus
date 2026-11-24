param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string[]]$Packs=@('default','vivid','somber','trueweathers'))
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$runtime="$p/build/tests094;$p/build/tests;$p/build/tests084;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
$stage="$p/build/VPIsometricLighting.jar";$fixture="$p/build/character094-woven"
New-Item -ItemType Directory -Force -Path "$p/build/tests094","$fixture/viewpoint/models","$fixture/viewpoint/platform","$fixture/viewpoint/render" | Out-Null
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d "$p/build/tests094" "$p/tests/CharacterVisibility094Probe.java" "$p/tests/CharacterWeave094.java"
if($LASTEXITCODE -ne 0){throw '094 compilation failed'}
foreach($name in @('models/Characters','platform/Shaders','platform/GlProgram','render/ShadowPass','render/Meshes')) {
 Copy-Item -LiteralPath "$p/build/patch-verification/woven/viewpoint/$name.class" -Destination "$fixture/viewpoint/$name.class" -Force
}
$java="$GameDirectory/jre64/bin/java.exe"
# 093 eligibility advice on its historical class, without the new flag advice.
$oldFixture="$p/build/character094-old";New-Item -ItemType Directory -Force -Path "$oldFixture/viewpoint/models" | Out-Null
& $java -Xverify:all -cp "$stage;$runtime" CharacterWeave094 "$p/build/checkpoints/093/VPIsometricLighting.jar" $oldFixture
if($LASTEXITCODE -ne 0){throw '093 baseline weave failed'}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$oldFixture;$p/build/checkpoints/093/VPIsometricLighting.jar;$runtime" CharacterVisibility094Probe baseline093
if($LASTEXITCODE -ne 0){throw '093 negative control failed'}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$stage;$runtime" CharacterVisibility094Probe $Packs[0]
if($LASTEXITCODE -ne 0){throw '094 first GPU pack failed'}
foreach($pack in $Packs | Select-Object -Skip 1) {
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$stage;$runtime" CharacterVisibility094Probe $pack
 if($LASTEXITCODE -ne 0){throw "094 GPU failed: $pack"}
}
Write-Host 'Character094 checks passed: native fade/eligibility, real skinned color/shadow geometry, four view rotations and client-player boundaries.'
