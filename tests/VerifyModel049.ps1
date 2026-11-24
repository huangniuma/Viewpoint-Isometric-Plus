param([string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$java = Join-Path $GameDirectory 'jre64\bin\java.exe'
$runtimePath = "$(Join-Path $projectDirectory 'build\builtin047-shaders-woven');$(Join-Path $projectDirectory 'build\tests');$(Join-Path $projectDirectory 'build\classes');$vpJar;$(Join-Path $GameDirectory 'ZombieBuddy.jar');$(Join-Path $GameDirectory 'projectzomboid.jar');$GameDirectory"
$testClasses = Join-Path $projectDirectory 'build\tests'
& java -jar (Join-Path $projectDirectory 'build\tools026\ecj-3.44.0.jar') --release 17 -encoding UTF-8 -cp $runtimePath -d $testClasses (Join-Path $PSScriptRoot 'ModelContactProbe.java') (Join-Path $PSScriptRoot 'ModelStartupProbe.java')
if($LASTEXITCODE -ne 0){throw '049 probe compilation failed'}
$old = Join-Path $projectDirectory 'build\checkpoints\048\VPIsometricLighting.jar'
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$old;$runtimePath" ModelContactProbe default 3 12 baseline
if($LASTEXITCODE -ne 0){throw '048 contact negative control failed'}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$old;$runtimePath" ModelStartupProbe library baseline
if($LASTEXITCODE -ne 0){throw '048 cold output negative control failed'}
foreach($pack in @('default','vivid','somber','trueweathers')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath ModelContactProbe $pack 3 12
    if($LASTEXITCODE -ne 0){throw "049 tall contact shell failed: $pack"}
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath ModelContactProbe $pack .4 3
    if($LASTEXITCODE -ne 0){throw "049 small real contact failed: $pack"}
}
foreach($pack in @('library','default','vivid','somber','trueweathers')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath ModelStartupProbe $pack
    if($LASTEXITCODE -ne 0){throw "049 cold output failed: $pack"}
}
Write-Host 'Model regression 049 PASS: contact depth shell, retained small contacts, stationary stability, ordinary/native/foreign isolation, no-PZO cold visible output, 048 negative controls.'
