param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string]$CandidateJar='')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
if(!$CandidateJar){$CandidateJar="$project/build/VPIsometricLighting.jar"}
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$buddy='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3619862853/mods/ZombieBuddy/libs/ZombieBuddy.jar'
$classes="$project/research/bug113-stream-trial/focused-classes"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$runtime="$classes;$CandidateJar;$vp;$buddy;$GameDirectory/projectzomboid.jar;$GameDirectory"
& java -jar "$project/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp $runtime -d $classes "$project/tests/FrameStream113Probe.java" "$project/tests/PatchWeavingProbe.java"
if($LASTEXITCODE -ne 0){throw '113 focused compilation failed'}
& "$GameDirectory/jre64/bin/java.exe" --enable-native-access=ALL-UNNAMED "-Dorg.lwjgl.librarypath=$project/.lwjgl/3.4.1-snapshot/x64" -Xverify:all -cp $runtime FrameStream113Probe
if($LASTEXITCODE -ne 0){throw '113 FrameStream GPU regression failed'}
