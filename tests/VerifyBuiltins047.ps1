param([string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid',
      [string[]]$Packs = @('library','default','vivid','somber'))
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$buddyJar = Join-Path $GameDirectory 'ZombieBuddy.jar'
$gameJar = Join-Path $GameDirectory 'projectzomboid.jar'
$java = Join-Path $GameDirectory 'jre64\bin\java.exe'
$fixture = Join-Path $projectDirectory 'build\builtin047-shaders-woven'
$classes = Join-Path $projectDirectory 'build\classes'
$testClasses = Join-Path $projectDirectory 'build\tests'
$runtimePath = "$fixture;$testClasses;$classes;$vpJar;$buddyJar;$gameJar;$GameDirectory"
$woven = Join-Path $projectDirectory 'build\patch-verification\woven'
foreach ($name in @('platform/Shaders','platform/GlProgram','render/Meshes','render/ShadowPass')) {
    $source = Join-Path $woven ("viewpoint/$name.class")
    if (Test-Path -LiteralPath $source) {
        $target = Join-Path $fixture ("viewpoint/$name.class")
        New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
        Copy-Item -LiteralPath $source -Destination $target -Force
    }
}
$sources = @('TestShaderPacks','ShaderCompileProbe','ModelEffectsProbe','NativeShadowMapsProbe','ModelMainProbe','ModelTranslucentDistanceProbe','NativeVPWaterProbe','ProfileProbe') | ForEach-Object { Join-Path $PSScriptRoot ($_ + '.java') }
& java -jar (Join-Path $projectDirectory 'build\tools026\ecj-3.44.0.jar') --release 17 -encoding UTF-8 -cp "$testClasses;$classes;$vpJar;$buddyJar;$gameJar" -d $testClasses @sources
if ($LASTEXITCODE -ne 0) { throw 'Built-in probe compilation failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath ProfileProbe
if ($LASTEXITCODE -ne 0) { throw 'Built-in settings regression failed' }
foreach ($pack in $Packs) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath ShaderCompileProbe $vpJar (Join-Path $projectDirectory 'build\builtin047-shaders') $pack
    if ($LASTEXITCODE -ne 0) { throw "Built-in shader compile/link failed: $pack" }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath ModelEffectsProbe $pack
    if ($LASTEXITCODE -ne 0) { throw "Built-in model controls failed: $pack" }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeShadowMapsProbe $pack
    if ($LASTEXITCODE -ne 0) { throw "Built-in native shadow maps failed: $pack" }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath ModelMainProbe $pack
    if ($LASTEXITCODE -ne 0) { throw "Built-in full renderer failed: $pack" }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath ModelTranslucentDistanceProbe $pack
    if ($LASTEXITCODE -ne 0) { throw "Built-in translucent distances failed: $pack" }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeVPWaterProbe $pack
    if ($LASTEXITCODE -ne 0) { throw "Built-in independent water failed: $pack" }
}
if (($Packs -join ',') -eq 'library,default,vivid,somber') {
    Write-Host 'Built-in pack regression 047 PASS: Library, Default, Vivid, Somber.'
} else { Write-Host ('Shader pack regression PASS: ' + ($Packs -join ', ')) }
