param([string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$java = Join-Path $GameDirectory 'jre64/bin/java.exe'
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$stage = Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$previous = Join-Path $projectDirectory 'build/checkpoints/064/VPIsometricLighting.jar'
$testClasses = Join-Path $projectDirectory 'build/tests065'
$runtime = "$testClasses;$(Join-Path $projectDirectory 'build/tests');$vpJar;$(Join-Path $GameDirectory 'ZombieBuddy.jar');$(Join-Path $GameDirectory 'projectzomboid.jar');$GameDirectory"
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$sources = @('UpperObjects064Probe.java','UpperObjects065Probe.java') | ForEach-Object { Join-Path $PSScriptRoot $_ }
& java -jar (Join-Path $projectDirectory 'build/tools026/ecj-3.44.0.jar') --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $testClasses @sources
if ($LASTEXITCODE -ne 0) { throw '065 probe compilation failed' }
Push-Location $projectDirectory
try {
    foreach ($artifact in @(@($previous,'baseline064'),@($stage,''))) {
        & $java --enable-native-access=ALL-UNNAMED '-Dnet.bytebuddy.experimental=true' -Xverify:all -cp "$($artifact[0]);$runtime" UpperObjects065Probe $artifact[1]
        if ($LASTEXITCODE -ne 0) { throw '065 upper hiding regression failed' }
    }
} finally { Pop-Location }
Write-Host 'Native upper hiding regression 065 PASS: current-game prepass order and ordinary upper object classification; 064 negative control and 065 native final-consumer comparison.'
