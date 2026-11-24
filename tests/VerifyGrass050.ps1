param([string]$GameDirectory='D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference='Stop'
$projectDirectory=Split-Path $PSScriptRoot -Parent
$vpJar='D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$java=Join-Path $GameDirectory 'jre64\bin\java.exe'
$fixture=Join-Path $projectDirectory 'build\grass050-woven'
foreach($name in @('platform/Shaders','platform/GlProgram','render/Meshes','render/ShadowPass','render/GrassPass')) {
    $target=Join-Path $fixture ("viewpoint/$name.class")
    New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
    Copy-Item -LiteralPath (Join-Path $projectDirectory "build/patch-verification/woven/viewpoint/$name.class") -Destination $target -Force
}
$runtimePath="$fixture;$(Join-Path $projectDirectory 'build\tests');$(Join-Path $projectDirectory 'build\classes');$vpJar;$(Join-Path $GameDirectory 'ZombieBuddy.jar');$(Join-Path $GameDirectory 'projectzomboid.jar');$GameDirectory"
& java -jar (Join-Path $projectDirectory 'build\tools026\ecj-3.44.0.jar') --release 17 -encoding UTF-8 -cp $runtimePath -d (Join-Path $projectDirectory 'build\tests') (Join-Path $PSScriptRoot 'NativeGrassProbe.java') (Join-Path $PSScriptRoot 'NativeGrassPipelineProbe.java')
if($LASTEXITCODE -ne 0){throw 'Grass fixture compilation failed'}
foreach($pack in @('library','default','vivid','somber','trueweathers')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeGrassProbe $pack
    if($LASTEXITCODE -ne 0){throw "Native/model grass GPU failed: $pack"}
}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeGrassPipelineProbe
if($LASTEXITCODE -ne 0){throw 'Independent grass pipeline failed'}
Write-Host 'Grass regression 050 PASS: five supported packs, native/model paths, actual compute, painted cards, queued independent control, depth/blackout, incremental updates, time/wind and ordinary VP isolation.'
