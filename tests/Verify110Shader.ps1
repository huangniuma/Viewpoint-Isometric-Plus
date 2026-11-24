param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string]$Artifact='',[string]$Output='research/nvidia110/expanded')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
if(!$Artifact){$Artifact="$p/build/VPIsometricLighting.jar"}
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$runtime="$p/build/tests110;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path "$p/build/tests110" | Out-Null
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$Artifact;$runtime" -d "$p/build/tests110" "$p/tests/Shader110Probe.java"
if($LASTEXITCODE -ne 0){throw '110 shader probe compilation failed'}
& "$GameDirectory/jre64/bin/java.exe" --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$Artifact;$runtime" Shader110Probe "$p/$Output"
if($LASTEXITCODE -ne 0){throw '110 selected shader link failed'}
Write-Host 'Shader110 checks passed.'
