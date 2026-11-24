param([string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$java = Join-Path $GameDirectory 'jre64\bin\java.exe'
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$testClasses = Join-Path $projectDirectory 'build/tests055'
$stage = Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$previous = Join-Path $projectDirectory 'build/checkpoints/054/VPIsometricLighting.jar'
$runtime = "$testClasses;$(Join-Path $projectDirectory 'build/tests');$vpJar;$(Join-Path $GameDirectory 'projectzomboid.jar')"
$sources = @('NativeVPWaterProbe.java','NativeTreeDepth055Probe.java','NativeTreeRender055Probe.java','NativeWaterFlow055Probe.java') | ForEach-Object { Join-Path $PSScriptRoot $_ }
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
& java -jar (Join-Path $projectDirectory 'build/tools026/ecj-3.44.0.jar') --release 17 -encoding UTF-8 -cp "$stage;$runtime" -d $testClasses @sources
if ($LASTEXITCODE -ne 0) { throw '055 test compilation failed' }
foreach ($pack in @('library','default','vivid','somber','trueweathers')) {
    if ($pack -notin @('library','vivid')) {
        & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$previous;$runtime" NativeVPWaterProbe $pack baseline-state055
        if ($LASTEXITCODE -ne 0) { throw "054 water producer negative control failed: $pack" }
    }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeVPWaterProbe $pack state055
    if ($LASTEXITCODE -ne 0) { throw "055 water producer state regression failed: $pack" }
}
foreach ($neighbor in @('false','true')) {
    if ($neighbor -eq 'false') {
        & $java --enable-native-access=ALL-UNNAMED "-Dtree055.third=$neighbor" -Xverify:all -cp "$previous;$runtime" NativeTreeDepth055Probe baseline054
        if ($LASTEXITCODE -ne 0) { throw '054 same-row tree negative control failed' }
    }
    & $java --enable-native-access=ALL-UNNAMED "-Dtree055.third=$neighbor" -Xverify:all -cp "$stage;$runtime" NativeTreeDepth055Probe
    if ($LASTEXITCODE -ne 0) { throw "055 same-row tree regression failed: neighbor=$neighbor" }
}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$previous;$runtime;$GameDirectory" NativeTreeRender055Probe baseline054
if ($LASTEXITCODE -ne 0) { throw '054 actual native tree consumer negative control failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime;$GameDirectory" NativeTreeRender055Probe
if ($LASTEXITCODE -ne 0) { throw '055 actual native tree consumer regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$previous;$runtime" NativeWaterFlow055Probe baseline054
if ($LASTEXITCODE -ne 0) { throw '054 reported pond/lake negative control failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeWaterFlow055Probe
if ($LASTEXITCODE -ne 0) { throw '055 actual official water flow comparison failed' }
$fixture = Join-Path $projectDirectory 'build/water054-options-woven'
foreach ($pack in @('library','default','vivid','somber','trueweathers')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$stage;$runtime" NativeVPWaterProbe $pack state055
    if ($LASTEXITCODE -ne 0) { throw "055 woven water producer state regression failed: $pack" }
    Write-Host "Woven water producer state 055 PASS: $pack"
}
Write-Host 'Water and crown regression 055 PASS: original producer-state negative controls, official flow GPU oracle, same-row crown depth and actual native stencil/outline consumer.'
