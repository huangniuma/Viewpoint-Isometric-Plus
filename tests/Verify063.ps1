param([string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$java = Join-Path $GameDirectory 'jre64/bin/java.exe'
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$stage = Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$previous = Join-Path $projectDirectory 'build/checkpoints/062/VPIsometricLighting.jar'
$testClasses = Join-Path $projectDirectory 'build/tests063'
$runtime = "$testClasses;$(Join-Path $projectDirectory 'build/tests');$vpJar;$(Join-Path $GameDirectory 'ZombieBuddy.jar');$(Join-Path $GameDirectory 'projectzomboid.jar');$GameDirectory"
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$sources = @('UpperObjects063Probe.java','PackedObjects063Probe.java','DeviceText061Probe.java','DeviceText063Probe.java','ModelCutawayProbe.java') | ForEach-Object { Join-Path $PSScriptRoot $_ }
& java -jar (Join-Path $projectDirectory 'build/tools026/ecj-3.44.0.jar') --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $testClasses @sources
if ($LASTEXITCODE -ne 0) { throw '063 regression probe compilation failed' }
Push-Location $projectDirectory
try {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$previous;$runtime" UpperObjects063Probe baseline062
    if ($LASTEXITCODE -ne 0) { throw '062 mixed upper object negative control failed' }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" UpperObjects063Probe
    if ($LASTEXITCODE -ne 0) { throw 'Upper mixed object regression failed' }
    $packedBefore = Join-Path $projectDirectory 'build/packed063-before'
    $packedAfter = Join-Path $projectDirectory 'build/packed063-after'
    foreach ($entry in @(@($vpJar,$packedBefore),@((Join-Path $projectDirectory 'build/patch-verification/woven/viewpoint/render/PackDraws.class'),$packedAfter))) {
        & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" PackedObjects063Probe prepare $entry[0] $entry[1]
        if ($LASTEXITCODE -ne 0) { throw 'Packed issue-only interception setup failed' }
    }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$packedBefore;$previous;$runtime" PackedObjects063Probe baseline062
    if ($LASTEXITCODE -ne 0) { throw '062 packed upper negative control failed' }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$packedAfter;$stage;$runtime" PackedObjects063Probe
    if ($LASTEXITCODE -ne 0) { throw 'Packed upper object regression failed' }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" DeviceText063Probe $previous baseline062
    if ($LASTEXITCODE -ne 0) { throw '062 moving device text negative control failed' }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" DeviceText063Probe $stage
    if ($LASTEXITCODE -ne 0) { throw 'Moving device text regression failed' }
} finally { Pop-Location }
Write-Host 'Upper objects and moving device text regression 063 PASS: native mixed-square opaque consumer, independent/baked/packed models and physical shadow retention; queued camera under concurrent input invalidation; pinned062 negative controls.'
