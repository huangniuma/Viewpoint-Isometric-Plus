param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string[]]$Packs=@('default','vivid','somber','trueweathers'))
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$fixture="$p/build/snow092-woven"
New-Item -ItemType Directory -Force -Path "$fixture/viewpoint/platform","$fixture/viewpoint/render","$p/build/tests092" | Out-Null
foreach($name in @('platform/Shaders','platform/GlProgram','render/Meshes','render/ShadowPass')) {
 Copy-Item -LiteralPath "$p/build/patch-verification/woven/viewpoint/$name.class" -Destination "$fixture/viewpoint/$name.class" -Force
}
$runtime="$p/build/tests092;$p/build/tests091;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
$stage="$p/build/VPIsometricLighting.jar"
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d "$p/build/tests092" "$p/tests/NativeSnow092Probe.java" "$p/tests/NativeSnowBurial092Probe.java" "$p/tests/NativeSnowOwnership092Probe.java"
if($LASTEXITCODE -ne 0){throw 'Snow092 probe compilation failed'}
$java="$GameDirectory/jre64/bin/java.exe"
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$stage;$runtime" NativeSnowOwnership092Probe
if($LASTEXITCODE -ne 0){throw 'Snow092 native sprite ownership failed'}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$p/build/checkpoints/091/VPIsometricLighting.jar;$stage;$runtime" NativeSnow092Probe default baseline091
if($LASTEXITCODE -ne 0){throw '091 native backing cutoff negative control failed'}
foreach($pack in $Packs) {
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$stage;$runtime" NativeSnow092Probe $pack
 if($LASTEXITCODE -ne 0){throw "Snow092 padded native framebuffer failed: $pack"}
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$stage;$runtime" NativeSnowBurial092Probe $pack
 if($LASTEXITCODE -ne 0){throw "Snow092 physical burial failed: $pack"}
}
Write-Host 'Snow092 checks passed: 091 cutoff reproduced; native backing allocation and subviewport masks, top/bottom coverage, zoom, opaque/alpha foreground and storey rejection.'
