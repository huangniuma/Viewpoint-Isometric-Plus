param([string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
# Reuse the same actual woven loader and GPU regressions for the external pack.
& (Join-Path $PSScriptRoot 'VerifyBuiltins047.ps1') -GameDirectory $GameDirectory -Packs @('trueweathers')
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$runtimePath = "$(Join-Path $projectDirectory 'build\builtin047-shaders-woven');$(Join-Path $projectDirectory 'build\tests');$(Join-Path $projectDirectory 'build\classes');$vpJar;$(Join-Path $GameDirectory 'ZombieBuddy.jar');$(Join-Path $GameDirectory 'projectzomboid.jar');$GameDirectory"
& java -jar (Join-Path $projectDirectory 'build\tools026\ecj-3.44.0.jar') --release 17 -encoding UTF-8 -cp $runtimePath -d (Join-Path $projectDirectory 'build\tests') (Join-Path $PSScriptRoot 'TrueWeathersProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'True Weathers probe compilation failed' }
& (Join-Path $GameDirectory 'jre64\bin\java.exe') --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath TrueWeathersProbe
if ($LASTEXITCODE -ne 0) { throw 'True Weathers custom passes/style/profile regression failed' }
Write-Host 'True Weathers regression 048 PASS: external loader, native/model shadows, custom weather passes, style controls, relink, water and translucent distances.'
