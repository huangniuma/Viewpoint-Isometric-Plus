param([string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$java = Join-Path $GameDirectory 'jre64/bin/java.exe'
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$stage = Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$previous = Join-Path $projectDirectory 'build/checkpoints/065/VPIsometricLighting.jar'
$testClasses = Join-Path $projectDirectory 'build/tests066'
$runtime = "$testClasses;$(Join-Path $projectDirectory 'build/tests');$vpJar;$(Join-Path $GameDirectory 'ZombieBuddy.jar');$(Join-Path $GameDirectory 'projectzomboid.jar');$GameDirectory"
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$sources = @('UpperObjects063Probe.java','UpperObjects064Probe.java','UpperOwnership066Probe.java','PackedSource066Probe.java','PackedObjects063Probe.java') | ForEach-Object { Join-Path $PSScriptRoot $_ }
& java -jar (Join-Path $projectDirectory 'build/tools026/ecj-3.44.0.jar') --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $testClasses @sources
if ($LASTEXITCODE -ne 0) { throw '066 probe compilation failed' }
Push-Location $projectDirectory
try {
    foreach ($artifact in @(@($previous,'baseline065'),@($stage,''))) {
        & $java --enable-native-access=ALL-UNNAMED '-Dnet.bytebuddy.experimental=true' -Xverify:all -cp "$($artifact[0]);$runtime" UpperOwnership066Probe $artifact[1]
        if ($LASTEXITCODE -ne 0) { throw '066 upper ownership regression failed' }
    }
    $packedFixture = Join-Path $projectDirectory 'build/packed066-woven'
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" PackedObjects063Probe prepare (Join-Path $projectDirectory 'build/patch-verification/woven/viewpoint/render/PackDraws.class') $packedFixture
    if ($LASTEXITCODE -ne 0) { throw '066 packed issue interception setup failed' }
    foreach ($artifact in @(@($previous,'baseline065'),@($stage,''))) {
        & $java --enable-native-access=ALL-UNNAMED '-Dnet.bytebuddy.experimental=true' -Xverify:all -cp "$packedFixture;$($artifact[0]);$runtime" PackedSource066Probe $artifact[1]
        if ($LASTEXITCODE -ne 0) { throw '066 packed source hiding regression failed' }
    }
} finally { Pop-Location }
Write-Host 'Native exact ownership regression 066 PASS; pinned065 negative control and staged real Cook comparison; packed source and opposite-square regression passed.'
