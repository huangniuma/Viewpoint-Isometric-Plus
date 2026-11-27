param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
& "$project/research/preload115-testing/run.ps1" -GameDirectory $GameDirectory
if($LASTEXITCODE -ne 0){throw '115 map admission checks failed'}
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$buddy='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3619862853/mods/ZombieBuddy/libs/ZombieBuddy.jar'
$out="$project/research/preload115-final";$classes="$out/test-classes"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$runtime="$project/build/VPIsometricLighting.jar;$classes;$project/build/tests;$vp;$buddy;$GameDirectory/projectzomboid.jar;$GameDirectory"
& java -jar "$project/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp $runtime -d $classes "$project/tests/PreloadCamera115Probe.java"
if($LASTEXITCODE -ne 0){throw '115 camera compilation failed'}
& "$GameDirectory/jre64/bin/java.exe" --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtime PreloadCamera115Probe "$project/build/patch-verification/woven" *> "$out/camera.log"
if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/camera.log" -Tail 20;throw '115 camera priority failed'}
Get-Content -LiteralPath "$out/camera.log" -Tail 1
& "$project/research/preload115-scope/run.ps1" -GameDirectory $GameDirectory
if($LASTEXITCODE -ne 0){throw '115 scope restoration checks failed'}
