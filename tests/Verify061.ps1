param([string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$java = Join-Path $GameDirectory 'jre64/bin/java.exe'
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$stage = Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$previous = Join-Path $projectDirectory 'build/checkpoints/060/VPIsometricLighting.jar'
$testClasses = Join-Path $projectDirectory 'build/tests061'
$runtime = "$testClasses;$(Join-Path $projectDirectory 'build/tests');$vpJar;$(Join-Path $GameDirectory 'ZombieBuddy.jar');$(Join-Path $GameDirectory 'projectzomboid.jar');$GameDirectory"
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
& java -jar (Join-Path $projectDirectory 'build/tools026/ecj-3.44.0.jar') --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $testClasses (Join-Path $PSScriptRoot 'ModelUpper061Probe.java') (Join-Path $PSScriptRoot 'DeviceText061Probe.java') (Join-Path $PSScriptRoot 'ModelCutawayProbe.java')
if ($LASTEXITCODE -ne 0) { throw '061 regression probe compilation failed' }
Push-Location $projectDirectory
try {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$previous;$runtime" ModelUpper061Probe baseline060
    if ($LASTEXITCODE -ne 0) { throw '060 upper square mask negative control failed' }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" ModelUpper061Probe
    if ($LASTEXITCODE -ne 0) { throw 'Upper square mask regression failed' }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" DeviceText061Probe $previous baseline060
    if ($LASTEXITCODE -ne 0) { throw '060 device text negative control failed' }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" DeviceText061Probe $stage
    if ($LASTEXITCODE -ne 0) { throw 'Rotated device text regression failed' }
} finally { Pop-Location }
Write-Host 'Upper objects and device text regression 061 PASS: actual native square masks and model producer, baked mesh plans, physical shadow retention, stairs; actual native subtitle producer and woven chat batching, eight angles/three zooms/two heights/radio/TV and unrelated-owner/view/player gates; pinned060 failures.'
