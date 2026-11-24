param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string[]]$Packs=@('default','library','vivid','somber','trueweathers'))
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$stage="$p/build/VPIsometricLighting.jar"
$runtime="$p/build/tests085;$p/build/tests084;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path "$p/build/tests085" | Out-Null
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d "$p/build/tests085" "$PSScriptRoot/PickAssist085GpuProbe.java" "$PSScriptRoot/ObjectPick085Probe.java"
if($LASTEXITCODE -ne 0){throw '085 probe compilation failed'}
$java="$GameDirectory/jre64/bin/java.exe"
$fixture="$p/build/pick085-shaders-woven"
foreach($name in @('platform/Shaders','platform/GlProgram','render/Meshes','interact/MouseTargets')) {
 $destination="$fixture/viewpoint/$name.class"
 New-Item -ItemType Directory -Force -Path (Split-Path $destination) | Out-Null
 Copy-Item -LiteralPath "$p/build/patch-verification/woven/viewpoint/$name.class" -Destination $destination -Force
}
$negative=& $java --enable-native-access=ALL-UNNAMED '-Dpick085.baseline=true' -Xverify:all -cp "$fixture;$p/build/checkpoints/084/VPIsometricLighting.jar;$runtime" ObjectPick085Probe default 2>&1
$negative | Write-Output
if($LASTEXITCODE -eq 0 -or ($negative -join "`n") -notmatch 'Actual renderer lost faded interaction source'){throw '084 did not reproduce view-faded source loss'}
Write-Host 'Previous084 interaction negative control PASS: actual VP renderer loses faded target.'
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$stage;$runtime" PickAssist085GpuProbe
if($LASTEXITCODE -ne 0){throw '085 assisted occlusion/colour regression failed'}
foreach($pack in $Packs){
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$stage;$runtime" ObjectPick085Probe $pack
 if($LASTEXITCODE -ne 0){throw "085 actual render pick failed: $pack"}
}
Write-Host ('Interaction085 checks passed: bounded visible-target assist, stable view fading, unchanged colours; five zoom levels/four yaw angles; packs='+($Packs -join ','))
