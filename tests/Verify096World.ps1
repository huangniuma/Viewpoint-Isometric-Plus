param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$stage="$p/build/VPIsometricLighting.jar"
$classes="$p/build/tests096-world";$fixture="$p/build/upscale096-world"
$runtime="$classes;$p/build/tests;$p/build/tests084;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $classes "$p/tests/NativeContainerHighlightProbe.java" "$p/tests/MouseLatency086Probe.java"
if($LASTEXITCODE -ne 0){throw '096 world fixture compilation failed'}
foreach($name in @('render/VendorInputs','render/TemporalPass','render/TargetOutline','interact/LootMenu','platform/GlProgram','platform/Shaders')) {
 $destination="$fixture/viewpoint/$name.class"
 New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
 Copy-Item -LiteralPath "$p/build/patch-verification/woven/viewpoint/$name.class" -Destination $destination -Force
}
$java="$GameDirectory/jre64/bin/java.exe"
Push-Location "$p/build/lua-fixture"
try {
 foreach($scale in @(25,33,50,67,100)) {
  & $java --enable-native-access=ALL-UNNAMED '-Dviewpoint.natives=C:/Users/Win10/Zomboid/viewpoint-natives' "-Dhighlight096.scale=$scale" -Xverify:all -cp "$fixture;$stage;$runtime" NativeContainerHighlightProbe
  if($LASTEXITCODE -ne 0){throw "096 FSR world/highlight failed: scale=$scale exit=$LASTEXITCODE"}
  foreach($jitter in @('false','true')) {
   & $java --enable-native-access=ALL-UNNAMED "-Dclick096.scale=$scale" "-Dclick096.jitter=$jitter" -Xverify:all -cp "$stage;$runtime" MouseLatency086Probe
   if($LASTEXITCODE -ne 0){throw "096 scaled picking failed: scale=$scale jitter=$jitter"}
  }
 }
 foreach($pack in @('library','vivid','somber')) {
  & $java --enable-native-access=ALL-UNNAMED '-Dviewpoint.natives=C:/Users/Win10/Zomboid/viewpoint-natives' '-Dhighlight096.scale=50' "-Dhighlight096.pack=$pack" -Xverify:all -cp "$fixture;$stage;$runtime" NativeContainerHighlightProbe
  if($LASTEXITCODE -ne 0){throw "096 pack FSR/highlight failed: $pack"}
 }
} finally {Pop-Location}
Write-Host 'Upscale096 world PASS: actual FSR world begin/finish, object/corpse output highlight and four camera yaws for click dispatch.'
