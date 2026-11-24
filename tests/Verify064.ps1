param([string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$java = Join-Path $GameDirectory 'jre64/bin/java.exe'
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$twJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3813908957\mods\ViewpointTrueWeathers\42\media\java\client\ViewpointTrueWeathers.jar'
$stage = Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$previous = Join-Path $projectDirectory 'build/checkpoints/063/VPIsometricLighting.jar'
$testClasses = Join-Path $projectDirectory 'build/tests064'
$runtime = "$testClasses;$(Join-Path $projectDirectory 'build/tests');$vpJar;$twJar;$(Join-Path $GameDirectory 'ZombieBuddy.jar');$(Join-Path $GameDirectory 'projectzomboid.jar');$GameDirectory"
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$sources = @('NativeDepth064Probe.java','NativeBuilding064Probe.java','NativeFloor064Probe.java','UpperObjects064Probe.java') | ForEach-Object { Join-Path $PSScriptRoot $_ }
& java -jar (Join-Path $projectDirectory 'build/tools026/ecj-3.44.0.jar') --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $testClasses @sources
if ($LASTEXITCODE -ne 0) { throw '064 focused probe compilation failed' }
Push-Location $projectDirectory
try {
    foreach ($artifact in @(@($previous,'baseline063'),@($stage,''))) {
        $cp = "$($artifact[0]);$runtime"
        & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $cp NativeDepth064Probe $GameDirectory $artifact[1]
        if ($LASTEXITCODE -ne 0) { throw 'Native emitted depth/camera oracle failed' }
        & $java --enable-native-access=ALL-UNNAMED '-Dnet.bytebuddy.experimental=true' -Xverify:all -cp $cp UpperObjects064Probe $artifact[1]
        if ($LASTEXITCODE -ne 0) { throw 'Native upper object ownership regression failed' }
    }
    $fixture = Join-Path $projectDirectory 'build/building064-woven'
    foreach ($name in @('render/Meshes','render/ShadowPass','platform/GlProgram','platform/Shaders')) {
        $destination = Join-Path $fixture ("viewpoint/$name.class")
        New-Item -ItemType Directory -Force -Path (Split-Path $destination) | Out-Null
        Copy-Item -LiteralPath (Join-Path $projectDirectory "build/patch-verification/woven/viewpoint/$name.class") -Destination $destination -Force
    }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$previous;$runtime" NativeBuilding064Probe baseline063
    if ($LASTEXITCODE -ne 0) { throw '063 missing native physical canopy negative control failed' }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$stage;$runtime" NativeBuilding064Probe
    if ($LASTEXITCODE -ne 0) { throw 'Native/3D physical canopy parity failed' }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$previous;$runtime" NativeFloor064Probe library baseline063
    if ($LASTEXITCODE -ne 0) { throw '063 ground footprint negative control failed' }
    foreach ($pack in @('library','default','vivid','somber','trueweathers')) {
        & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$stage;$runtime" NativeFloor064Probe $pack
        if ($LASTEXITCODE -ne 0) { throw "Native physical floor receiver regression failed: $pack" }
    }
} finally { Pop-Location }
Write-Host 'Native building and upper objects regression 064 PASS: native final consumers and exact VP ownership; emitted native depth oracle; physical floor/ceiling canopy parity, original ground contribution, five packs and pinned063 negative controls.'
