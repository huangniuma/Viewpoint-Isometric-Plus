param([string]$GameDirectory='D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference='Stop'
$projectDirectory=Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'VerifyGrass050.ps1') -GameDirectory $GameDirectory
$vpJar='D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$java=Join-Path $GameDirectory 'jre64\bin\java.exe'
$runtimePath="$(Join-Path $projectDirectory 'build\grass050-woven');$(Join-Path $projectDirectory 'build\tests');$(Join-Path $projectDirectory 'build\classes');$vpJar;$(Join-Path $GameDirectory 'ZombieBuddy.jar');$(Join-Path $GameDirectory 'projectzomboid.jar');$GameDirectory"
& java -jar (Join-Path $projectDirectory 'build\tools026\ecj-3.44.0.jar') --release 17 -encoding UTF-8 -cp $runtimePath -d (Join-Path $projectDirectory 'build\tests') (Join-Path $PSScriptRoot 'NativeGrassAppearanceProbe.java')
if($LASTEXITCODE -ne 0){throw 'Grass appearance fixture compilation failed'}
foreach($pack in @('library','default','vivid','somber','trueweathers')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeGrassAppearanceProbe $pack
    if($LASTEXITCODE -ne 0){throw "Native grass appearance failed: $pack"}
}
$baselinePath="$(Join-Path $projectDirectory 'build\checkpoints\050\VPIsometricLighting.jar');$runtimePath"
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $baselinePath NativeGrassAppearanceProbe library --expect-ground-bleed
if($LASTEXITCODE -ne 0){throw 'Pinned 050 grass appearance negative control failed'}
Write-Host 'Grass appearance regression 051 PASS: five supported packs, real grass draw with textured base, captured root light, painted cutouts, native floor depth and pinned 050 negative control.'
