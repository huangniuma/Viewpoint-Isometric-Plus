param([string]$GameDirectory='D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference='Stop'
$projectDirectory=Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'VerifyGrass051.ps1') -GameDirectory $GameDirectory
$vpJar='D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$java=Join-Path $GameDirectory 'jre64\bin\java.exe'
$runtimePath="$(Join-Path $projectDirectory 'build\grass050-woven');$(Join-Path $projectDirectory 'build\tests');$(Join-Path $projectDirectory 'build\classes');$vpJar;$(Join-Path $GameDirectory 'ZombieBuddy.jar');$(Join-Path $GameDirectory 'projectzomboid.jar');$GameDirectory"
& java -jar (Join-Path $projectDirectory 'build\tools026\ecj-3.44.0.jar') --release 17 -encoding UTF-8 -cp $runtimePath -d (Join-Path $projectDirectory 'build\tests') (Join-Path $PSScriptRoot 'NativeGrassReceiverProbe.java')
if($LASTEXITCODE -ne 0){throw 'Grass receiver fixture compilation failed'}
foreach($pack in @('library','default','vivid','somber','trueweathers')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeGrassReceiverProbe $pack current $GameDirectory
    if($LASTEXITCODE -ne 0){throw "Native grass shadows/foreground failed: $pack"}
}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeGrassReceiverProbe library (Join-Path $projectDirectory 'build\checkpoints\051\VPIsometricLighting.jar') $GameDirectory
if($LASTEXITCODE -ne 0){throw 'Pinned 051 grass receiver negative control failed'}
Write-Host 'Grass receiver regression 052 PASS: five supported packs, actual VP shadow maps, actual native tile/chunk masks, production drawer, equal-depth foreground, wall fades, alpha edges and pinned 051 negative control.'
