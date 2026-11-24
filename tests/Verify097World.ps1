param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$classes="$p/build/tests097-world";$fixture="$p/build/framegen097-world";$stage="$p/build/VPIsometricLighting.jar"
$runtime="$classes;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $classes "$p/tests/NativeContainerHighlightProbe.java"
if($LASTEXITCODE -ne 0){throw '097 full world fixture compilation failed'}
foreach($name in @('render/VendorInputs','render/FrameGenPass','render/TemporalPass','render/TargetOutline','interact/LootMenu','platform/LiveSettings$Toggle','platform/GlProgram','platform/Shaders')) {
 $destination="$fixture/viewpoint/$name.class";New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
 Copy-Item -LiteralPath "$p/build/patch-verification/woven/viewpoint/$name.class" -Destination $destination -Force
}
Push-Location "$p/build/lua-fixture"
try {
 foreach($pack in @('default','library','vivid','somber')) {
  & "$GameDirectory/jre64/bin/java.exe" --enable-native-access=ALL-UNNAMED '-Dviewpoint.natives=C:/Users/Win10/Zomboid/viewpoint-natives' '-Dframegen097=true' '-Dhighlight096.scale=50' "-Dhighlight096.pack=$pack" -Xverify:all -cp "$fixture;$stage;$runtime" NativeContainerHighlightProbe
  if($LASTEXITCODE -ne 0){throw "097 actual FSR/FG world failed: $pack"}
 }
}finally{Pop-Location}
