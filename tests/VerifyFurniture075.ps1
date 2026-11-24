param([string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$java = Join-Path $GameDirectory 'jre64/bin/java.exe'
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$stage = Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$previous = Join-Path $projectDirectory 'build/checkpoints/074/VPIsometricLighting.jar'
$testClasses = Join-Path $projectDirectory 'build/tests075'
$runtime = "$testClasses;$(Join-Path $projectDirectory 'build/tests');$vpJar;$(Join-Path $GameDirectory 'ZombieBuddy.jar');$(Join-Path $GameDirectory 'projectzomboid.jar');$GameDirectory"
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$sources = @('UpperObjects063Probe.java','UpperObjects064Probe.java','Furniture075Probe.java','PackedSource066Probe.java','PackedObjects063Probe.java') | ForEach-Object { Join-Path $PSScriptRoot $_ }
& java -jar (Join-Path $projectDirectory 'build/tools026/ecj-3.44.0.jar') --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $testClasses @sources
if ($LASTEXITCODE -ne 0) { throw '075 furniture probe compilation failed' }
Push-Location $projectDirectory
try {
    foreach ($artifact in @(@($previous,'baseline074'),@($stage,''))) {
        & $java --enable-native-access=ALL-UNNAMED '-Dnet.bytebuddy.experimental=true' -Xverify:all -cp "$($artifact[0]);$runtime" Furniture075Probe $artifact[1]
        if ($LASTEXITCODE -ne 0) { throw '075 furniture hiding regression failed' }
    }
} finally { Pop-Location }
$packedFixture = Join-Path $projectDirectory 'build/packed066-woven'
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" PackedObjects063Probe prepare (Join-Path $projectDirectory 'build/patch-verification/woven/viewpoint/render/PackDraws.class') $packedFixture
if ($LASTEXITCODE -ne 0) { throw '075 packed consumer fixture failed' }
foreach ($artifact in @(@($previous,'baseline074'),@($stage,''))) {
    # The current woven consumer also calls newer optional helpers. Keep the pinned
    # visibility implementation first, and supply only absent helper classes second.
    & $java --enable-native-access=ALL-UNNAMED '-Dnet.bytebuddy.experimental=true' -Xverify:all -cp "$packedFixture;$($artifact[0]);$stage;$runtime" PackedSource066Probe same075 $artifact[1]
    if ($LASTEXITCODE -ne 0) { throw '075 same-floor packed native visibility failed' }
}
Write-Host 'Furniture075 native parity PASS: same-floor mesh, independent models, packed instances; native final alpha and None, visible neighbours and shadow geometry retained; pinned074 negatives.' 

