param([string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$fixture = Join-Path $projectDirectory 'build\preparation048-woven'
$testClasses = Join-Path $projectDirectory 'build\tests'
$classes = Join-Path $projectDirectory 'build\classes'
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$buddyJar = Join-Path $GameDirectory 'ZombieBuddy.jar'
$gameJar = Join-Path $GameDirectory 'projectzomboid.jar'
$java = Join-Path $GameDirectory 'jre64\bin\java.exe'
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
foreach ($name in @('AheadPlan','Shaders')) {
    $target = Join-Path $fixture ("viewpoint/platform/$name.class")
    New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
    Copy-Item -LiteralPath (Join-Path $projectDirectory "build/patch-verification/woven/viewpoint/platform/$name.class") -Destination $target -Force
}
$runtimePath = "$fixture;$testClasses;$classes;$vpJar;$buddyJar;$gameJar;$GameDirectory"
& java -jar (Join-Path $projectDirectory 'build\tools026\ecj-3.44.0.jar') --release 17 -encoding UTF-8 -cp "$classes;$vpJar;$buddyJar;$gameJar" -d $testClasses (Join-Path $PSScriptRoot 'ShaderPreparationProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'Shader preparation probe compilation failed' }
# Personal settings and cache inventory are read-only probe inputs.
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath ShaderPreparationProbe 'C:\Users\Win10\Zomboid\viewpoint-live.properties' 'C:\Users\Win10\Zomboid\viewpoint-programs\programs.list' woven
if ($LASTEXITCODE -ne 0) { throw 'Actual shader preparation/cache-key regression failed' }
