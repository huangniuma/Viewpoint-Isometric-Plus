param([string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$java = Join-Path $GameDirectory 'jre64\bin\java.exe'
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$buddyJar = Join-Path $GameDirectory 'ZombieBuddy.jar'
$testClasses = Join-Path $projectDirectory 'build/tests057'
$stage = Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$previous = Join-Path $projectDirectory 'build/checkpoints/056/VPIsometricLighting.jar'
$runtime = "$testClasses;$(Join-Path $projectDirectory 'build/tests');$vpJar;$buddyJar;$(Join-Path $GameDirectory 'projectzomboid.jar');$GameDirectory"
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
& java -jar (Join-Path $projectDirectory 'build/tools026/ecj-3.44.0.jar') --release 17 -encoding UTF-8 -cp "$stage;$runtime" -d $testClasses (Join-Path $PSScriptRoot 'NativeVehicleHints057Probe.java')
if ($LASTEXITCODE -ne 0) { throw '057 vehicle hint probe compilation failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeVehicleHints057Probe $previous
if ($LASTEXITCODE -ne 0) { throw 'Pinned056 missing vehicle hint producer negative control failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeVehicleHints057Probe $stage
if ($LASTEXITCODE -ne 0) { throw 'Actual native vehicle hints and isolated rotated-line consumer regression failed' }
Push-Location $projectDirectory
try {
    & python -X utf8 (Join-Path $PSScriptRoot 'Audit057.py')
    if ($LASTEXITCODE -ne 0) { throw '057 diagnostic sampler artifact cleanup failed' }
} finally { Pop-Location }
Write-Host 'Vehicle hints regression 057 PASS: actual native producer, fixed-view native queue, isolated rotated lines, native UI/alpha/script gates, balanced pool and unchanged unrelated/fading lines. Screen GPU sink and candidate selection are controlled.'
