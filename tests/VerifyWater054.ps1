param([string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$java = Join-Path $GameDirectory 'jre64\bin\java.exe'
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$runtime = "$(Join-Path $projectDirectory 'build/tests');$vpJar;$(Join-Path $GameDirectory 'projectzomboid.jar')"
$previous = Join-Path $projectDirectory 'build/checkpoints/053/VPIsometricLighting.jar'
$current = Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
foreach ($pack in @('default','vivid','somber','trueweathers')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$previous;$runtime" NativeVPWaterProbe $pack baseline053-live
    if ($LASTEXITCODE -ne 0) { throw "Pinned053 live water negative control failed: $pack" }
}
foreach ($pack in @('library','default','vivid','somber','trueweathers')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$current;$runtime" NativeVPWaterProbe $pack live054
    if ($LASTEXITCODE -ne 0) { throw "Live water option regression failed: $pack" }
}
$fixture = Join-Path $projectDirectory 'build/water054-options-woven'
foreach ($name in @('Shaders','GlProgram')) {
    $destination = Join-Path $fixture ("viewpoint/platform/$name.class")
    New-Item -ItemType Directory -Force -Path (Split-Path $destination) | Out-Null
    Copy-Item -LiteralPath (Join-Path $projectDirectory "build/patch-verification/woven/viewpoint/platform/$name.class") -Destination $destination -Force
}
foreach ($pack in @('library','default','vivid','somber','trueweathers')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$current;$runtime" NativeVPWaterProbe $pack live054
    if ($LASTEXITCODE -ne 0) { throw "Woven live water option regression failed: $pack" }
    Write-Host "Woven live water options 054 PASS: $pack"
}
Write-Host 'Live water regression 054 PASS: five packs, runtime numeric options, original water pipeline and pinned053 compile failures.'
