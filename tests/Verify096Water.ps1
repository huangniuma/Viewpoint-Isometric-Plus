param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$stage="$p/build/VPIsometricLighting.jar";$classes="$p/build/tests096-water";$fixture="$p/build/upscale096-water"
$runtime="$classes;$stage;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp $runtime -d $classes "$p/tests/NativeWaterEdgeProbe.java" "$p/tests/RotationTemporalProbe.java"
if($LASTEXITCODE -ne 0){throw '096 water fixture compilation failed'}
& python -X utf8 "$p/tests/PrepareWater041.py" "$GameDirectory/projectzomboid.jar" "$p/build/patch-verification/woven" $fixture
if($LASTEXITCODE -ne 0){throw '096 native water package setup failed'}
Copy-Item -LiteralPath "$p/build/patch-verification/woven/viewpoint/render/VendorInputs.class" -Destination "$fixture/viewpoint/render/VendorInputs.class" -Force
$java="$GameDirectory/jre64/bin/java.exe"
Push-Location $p
try {
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$runtime" RotationTemporalProbe
 if($LASTEXITCODE -ne 0){throw '096 non-vendor temporal regression failed'}
 foreach($mode in @('false','true')) {
  & $java --enable-native-access=ALL-UNNAMED '-Dviewpoint.natives=C:/Users/Win10/Zomboid/viewpoint-natives' "-Dwater096.fsr=$mode" -Xverify:all -cp "$fixture;$runtime" NativeWaterEdgeProbe model039
  if($LASTEXITCODE -ne 0){throw "096 native shoreline regression failed: FSR=$mode"}
 }
} finally {Pop-Location}
Write-Host 'Upscale096 water PASS: native producer, actual model depth and shoreline, rotated temporal scope, full-resolution and FSR paths.'
