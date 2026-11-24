param([string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$java = Join-Path $GameDirectory 'jre64/bin/java.exe'
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$twJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3813908957\mods\ViewpointTrueWeathers\42\media\java\client\ViewpointTrueWeathers.jar'
$stage = Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$previous = Join-Path $projectDirectory 'build/checkpoints/061/VPIsometricLighting.jar'
$testClasses = Join-Path $projectDirectory 'build/tests062'
$runtime = "$testClasses;$(Join-Path $projectDirectory 'build/tests');$vpJar;$twJar;$(Join-Path $GameDirectory 'ZombieBuddy.jar');$(Join-Path $GameDirectory 'projectzomboid.jar');$GameDirectory"
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
& java -jar (Join-Path $projectDirectory 'build/tools026/ecj-3.44.0.jar') --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $testClasses (Join-Path $PSScriptRoot 'NativeCanopy062Probe.java')
if ($LASTEXITCODE -ne 0) { throw '062 canopy probe compilation failed' }
Push-Location $projectDirectory
try {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$previous;$runtime" NativeCanopy062Probe library baseline061
    if ($LASTEXITCODE -ne 0) { throw '061 canopy clipping negative control failed' }
    foreach ($pack in @('library','default','vivid','somber','trueweathers')) {
        & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeCanopy062Probe $pack
        if ($LASTEXITCODE -ne 0) { throw "Outdoor canopy receiver regression failed: $pack" }
    }
} finally { Pop-Location }
Write-Host 'Canopy receiver regression 062 PASS: actual VP exposure producer; covered/open exterior match, enclosed interior retained, moving sun/camera, six receiver kinds and own/off/removed-caster identity; five live packs; pinned061 reproduces clipped reception.'
