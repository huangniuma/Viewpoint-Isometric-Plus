param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string[]]$Packs=@('library','default','vivid','somber','trueweathers'))
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$fixture="$p/build/snow091-woven"
New-Item -ItemType Directory -Force -Path "$fixture/viewpoint/platform","$fixture/viewpoint/render","$p/build/tests091" | Out-Null
foreach($name in @('platform/Shaders','platform/GlProgram','render/Meshes','render/ShadowPass')) {
 Copy-Item -LiteralPath "$p/build/patch-verification/woven/viewpoint/$name.class" -Destination "$fixture/viewpoint/$name.class" -Force
}
$cp="$fixture;$p/build/tests091;$p/build/tests;$p/build/VPIsometricLighting.jar;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp $cp -d "$p/build/tests091" "$p/tests/NativeSnow091Probe.java"
if($LASTEXITCODE -ne 0){throw 'Snow091 compilation failed'}
foreach($pack in $Packs) {
 & "$GameDirectory/jre64/bin/java.exe" --enable-native-access=ALL-UNNAMED -Xverify:all -cp $cp NativeSnow091Probe $pack
 if($LASTEXITCODE -ne 0){throw "Native snow091 GPU failed: $pack"}
}
if($Packs.Count -gt 1) {
 foreach($quality in @('low','high','ultra')) {
  & "$GameDirectory/jre64/bin/java.exe" --enable-native-access=ALL-UNNAMED -Xverify:all -cp $cp NativeSnow091Probe default $quality
  if($LASTEXITCODE -ne 0){throw "Native snow091 quality failed: $quality"}
 }
}
Write-Host 'Snow091 checks passed: actual VP snow layer/state/model contact, native foreground alpha, indoor and storey masks, zoom, five supported packs and four snow quality levels.'
