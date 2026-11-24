param([string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$java = Join-Path $GameDirectory 'jre64/bin/java.exe'
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$stage = Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$testClasses = Join-Path $projectDirectory 'build/tests058'
$runtime = "$testClasses;$stage;$(Join-Path $projectDirectory 'build/tests');$vpJar;$(Join-Path $GameDirectory 'ZombieBuddy.jar');$(Join-Path $GameDirectory 'projectzomboid.jar');$GameDirectory"
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
& java -jar (Join-Path $projectDirectory 'build/tools026/ecj-3.44.0.jar') --release 17 -encoding UTF-8 -cp $runtime -d $testClasses (Join-Path $PSScriptRoot 'RotationTransition058Probe.java')
if ($LASTEXITCODE -ne 0) { throw '058 transition probe compilation failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtime RotationTransition058Probe (Join-Path $projectDirectory 'build/patch-verification/woven/viewpoint/camera/MovePose.class')
if ($LASTEXITCODE -ne 0) { throw 'Actual woven rotated entry/exit transition regression failed' }
Write-Host 'Rotated transition regression 058 PASS: actual woven MovePose, native interpolation, 1764 entry/exit cases and 66 guards.'
$sources = @('NativeTreeProducerProbe.java','NativeVisibility053Probe.java','NativeTreeRender055Probe.java','NativeTreeRotation058Probe.java') | ForEach-Object { Join-Path $PSScriptRoot $_ }
& java -jar (Join-Path $projectDirectory 'build/tools026/ecj-3.44.0.jar') --release 17 -encoding UTF-8 -cp $runtime -d $testClasses @sources
if ($LASTEXITCODE -ne 0) { throw '058 tree GPU probe compilation failed' }
Push-Location $projectDirectory
try {
    foreach ($original in @('official','pzo')) {
        $overrides = if ($original -eq 'pzo') { Join-Path $GameDirectory 'pzopt/overrides' } else { '-' }
        foreach ($mode in @('old','fixed')) {
            $folder = Join-Path $projectDirectory "build/tree058-$mode-$original"
            & $java -Xverify:all -cp $runtime NativeTreeRotation058Probe weave (Join-Path $GameDirectory 'projectzomboid.jar') $overrides $folder $mode
            if ($LASTEXITCODE -ne 0) { throw "058 tree weave failed: $original $mode" }
            & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$folder;$runtime" NativeTreeRotation058Probe $mode
            if ($LASTEXITCODE -ne 0) { throw "058 tree GPU check failed: $original $mode" }
        }
    }
} finally { Pop-Location }
Write-Host 'Tree rotation regression 058 PASS: actual woven official/PZO tree consumers, pinned057 depth negative controls, nonempty dual textures, four yaws, normal/XL, 48 moving frames and foreground depth bounds. Native VBO; controlled world/atlas.'
