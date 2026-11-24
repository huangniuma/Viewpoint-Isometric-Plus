param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$stage="$p/build/VPIsometricLighting.jar"
$runtime="$p/build/tests084;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path "$p/build/tests084" | Out-Null
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d "$p/build/tests084" "$PSScriptRoot/Interaction084Probe.java" "$PSScriptRoot/ObjectState084GpuProbe.java"
if($LASTEXITCODE -ne 0){throw '084 probe compilation failed'}
$java="$GameDirectory/jre64/bin/java.exe"
$fixture="$p/build/interaction084-woven"
New-Item -ItemType Directory -Force -Path "$fixture/viewpoint/interact" | Out-Null
Copy-Item -LiteralPath "$p/build/patch-verification/woven/viewpoint/interact/MouseTargets.class" -Destination "$fixture/viewpoint/interact/MouseTargets.class" -Force
foreach($probe in @('Interaction084Probe','ObjectState084GpuProbe')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$stage;$runtime" $probe
    if($LASTEXITCODE -ne 0){throw "084 regression failed: $probe"}
}
Write-Host 'Interaction084 checks passed: continuous beam, native alpha steps, exact colour-fragment ownership and GPU state preservation.'
