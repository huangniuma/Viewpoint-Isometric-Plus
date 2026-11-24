param([string]$GameDirectory='D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference='Stop'
$projectDirectory=Split-Path $PSScriptRoot -Parent
$vp='D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$stage=Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$classes=Join-Path $projectDirectory 'build/tests080'
$runtime="$classes;$projectDirectory/build/tests079;$projectDirectory/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& java -jar "$projectDirectory/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $classes "$PSScriptRoot/NativeShaderState080Probe.java"
if($LASTEXITCODE -ne 0){throw 'Native shader state probe compilation failed'}
$java=Join-Path $GameDirectory 'jre64/bin/java.exe'
$negative=& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$projectDirectory/build/checkpoints/079/VPIsometricLighting.jar;$runtime" NativeShaderState080Probe 2>&1
$negative | Write-Output
if($LASTEXITCODE -eq 0 -or ($negative -join "`n") -notmatch 'Native zoom matrix skipped after drawer') {throw 'Previous packaged artifact did not reproduce lost native zoom updates'}
Write-Host 'Previous packaged artifact negative control PASS: native zoom matrix update skipped after effects drawer.'
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeShaderState080Probe
if($LASTEXITCODE -ne 0){throw 'Native shader binding/zoom matrix regression failed'}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" LinkageProbe "$projectDirectory/build/classes"
if($LASTEXITCODE -ne 0){throw 'JVM linkage verification failed'}
foreach($probe in @('NativeOutputProbe','NativeCaptureProbe')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" $probe $GameDirectory
    if($LASTEXITCODE -ne 0){throw "Native image preservation regression failed: $probe"}
}
Write-Host 'Shader state080 checks passed: native zoom update negative control, repaired drawer, original output/capture and runtime linkage.'
