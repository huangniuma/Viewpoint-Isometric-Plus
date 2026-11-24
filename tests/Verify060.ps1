param([string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$java = Join-Path $GameDirectory 'jre64/bin/java.exe'
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$twJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3813908957\mods\ViewpointTrueWeathers\42\media\java\client\ViewpointTrueWeathers.jar'
$stage = Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$testClasses = Join-Path $projectDirectory 'build/tests060'
$runtime = "$testClasses;$(Join-Path $projectDirectory 'build/tests');$vpJar;$twJar;$(Join-Path $GameDirectory 'ZombieBuddy.jar');$(Join-Path $GameDirectory 'projectzomboid.jar');$GameDirectory"
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
& java -jar (Join-Path $projectDirectory 'build/tools026/ecj-3.44.0.jar') --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $testClasses (Join-Path $PSScriptRoot 'NativeLamp060Probe.java')
if ($LASTEXITCODE -ne 0) { throw '060 lamp probe compilation failed' }
Push-Location $projectDirectory
try {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$(Join-Path $projectDirectory 'build/checkpoints/059/VPIsometricLighting.jar');$runtime" NativeLamp060Probe library baseline059
    if ($LASTEXITCODE -ne 0) { throw '059 missing lamp shadow negative control failed' }
    foreach ($pack in @('library','default','vivid','somber','trueweathers')) {
        & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeLamp060Probe $pack live
        if ($LASTEXITCODE -ne 0) { throw "Actual VP lamp GPU regression failed: $pack" }
    }
    foreach ($order in @('tw-first','addon-first')) {
        $folder = Join-Path $projectDirectory "build/tw059-$order"
        & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$folder;$stage;$runtime" NativeLamp060Probe trueweathers live
        if ($LASTEXITCODE -ne 0) { throw "Actual TW lamp GPU regression failed: $order" }
    }
} finally { Pop-Location }
Write-Host 'Native lamp regression 060 PASS: real VP cube maps, light atlas and native physical receivers; floor/wall/furniture/rigid/skinned/vehicle and skinned caster; night, indoor, outdoor isolation, movement, removed caster, extinguished/off identity; texture/sampler and RGBA/depth/stencil preservation; five live packs and actual TW advice in both orders; pinned059 negative control.'
