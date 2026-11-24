param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$classes="$p/build/tests097";$fixture="$p/build/framegen097-woven";$stage="$p/build/VPIsometricLighting.jar"
$runtime="$classes;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $classes "$p/tests/FrameGen097Probe.java" "$p/tests/FrameGenQueue097Probe.java"
if($LASTEXITCODE -ne 0){throw '097 fixture compilation failed'}
foreach($name in @('Hooks','render/VendorInputs','render/FrameGenPass','upscale/UpscaleNeeds','platform/LiveSettings$Toggle','platform/GlProgram','platform/Shaders')) {
 $destination="$fixture/viewpoint/$name.class";New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
 Copy-Item -LiteralPath "$p/build/patch-verification/woven/viewpoint/$name.class" -Destination $destination -Force
}
Push-Location $p
try {
 & "$GameDirectory/jre64/bin/java.exe" --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/checkpoints/096/VPIsometricLighting.jar;$runtime" viewpoint.render.FrameGen097Probe baseline096
 if($LASTEXITCODE -ne 0){throw '096 frame generation negative failed'}
 & "$GameDirectory/jre64/bin/java.exe" --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$stage;$runtime" FrameGenQueue097Probe
 if($LASTEXITCODE -ne 0){throw '097 HUD/native queue fixture failed'}
 & "$GameDirectory/jre64/bin/java.exe" --enable-native-access=ALL-UNNAMED '-Dviewpoint.natives=C:/Users/Win10/Zomboid/viewpoint-natives' -Xverify:all -cp "$fixture;$stage;$runtime" viewpoint.render.FrameGen097Probe
 if($LASTEXITCODE -ne 0){throw '097 native frame generation integration failed'}
 foreach($size in @(@(1920,1080),@(3440,1440))) {
  & "$GameDirectory/jre64/bin/java.exe" --enable-native-access=ALL-UNNAMED '-Dviewpoint.natives=C:/Users/Win10/Zomboid/viewpoint-natives' '-Dframegen097.nativeOnly=true' "-Dframegen097.width=$($size[0])" "-Dframegen097.height=$($size[1])" -Xverify:all -cp "$fixture;$stage;$runtime" viewpoint.render.FrameGen097Probe
  if($LASTEXITCODE -ne 0){throw "097 native resolution FG failed: $size"}
 }
}finally{Pop-Location}
