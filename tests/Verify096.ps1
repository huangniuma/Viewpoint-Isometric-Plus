param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$stage="$p/build/VPIsometricLighting.jar"
$classes="$p/build/tests096";$fixture="$p/build/upscale096-woven"
$runtime="$classes;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $classes "$p/tests/Upscale096Probe.java"
if($LASTEXITCODE -ne 0){throw '096 fixture compilation failed'}
foreach($name in @('render/VendorInputs','render/TemporalPass','platform/GlProgram','platform/Shaders')) {
 $destination="$fixture/viewpoint/$name.class"
 New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
 Copy-Item -LiteralPath "$p/build/patch-verification/woven/viewpoint/$name.class" -Destination $destination -Force
}
$java="$GameDirectory/jre64/bin/java.exe"
Push-Location $p
try {
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/checkpoints/095/VPIsometricLighting.jar;$runtime" viewpoint.render.Upscale096Probe baseline095
 if($LASTEXITCODE -ne 0){throw '095 negative control failed'}
 $negative=& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/checkpoints/095/VPIsometricLighting.jar;$runtime" viewpoint.render.Upscale096Probe vision095 2>&1
 $negative | Write-Output
 if($LASTEXITCODE -eq 0 -or ($negative -join "`n") -notmatch 'AssertionError'){throw '095 scaled visibility negative control did not fail'}
 Write-Host 'Previous095 scaled visibility negative PASS: temporal depth not aligned in the unrotated native output.'
 & $java --enable-native-access=ALL-UNNAMED '-Dviewpoint.natives=C:/Users/Win10/Zomboid/viewpoint-natives' -Xverify:all -cp "$fixture;$stage;$runtime" viewpoint.render.Upscale096Probe
 if($LASTEXITCODE -ne 0){throw '096 GPU/native FSR integration failed'}
} finally {Pop-Location}
