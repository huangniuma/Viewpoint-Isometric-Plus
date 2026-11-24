param([string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$java = Join-Path $GameDirectory 'jre64/bin/java.exe'
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$twJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3813908957\mods\ViewpointTrueWeathers\42\media\java\client\ViewpointTrueWeathers.jar'
$stage = Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$testClasses = Join-Path $projectDirectory 'build/tests059'
$runtime = "$testClasses;$(Join-Path $projectDirectory 'build/tests');$vpJar;$twJar;$(Join-Path $GameDirectory 'ZombieBuddy.jar');$(Join-Path $GameDirectory 'projectzomboid.jar');$GameDirectory"
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
& java -jar (Join-Path $projectDirectory 'build/tools026/ecj-3.44.0.jar') --release 17 -encoding UTF-8 -cp "$stage;$runtime" -d $testClasses (Join-Path $PSScriptRoot 'TrueWeathersRuntime059Probe.java') (Join-Path $PSScriptRoot 'ShaderCompileProbe.java')
if ($LASTEXITCODE -ne 0) { throw '059 cross-mod probe compilation failed' }
Push-Location $projectDirectory
try {
    foreach ($order in @('tw-first','addon-first')) {
        $folder = Join-Path $projectDirectory "build/tw059-$order"
        & $java -Xverify:all -cp "$stage;$runtime" TrueWeathersRuntime059Probe weave $folder $order
        if ($LASTEXITCODE -ne 0) { throw "Actual cross-mod weave failed: $order" }
        foreach ($pack in @('library','default','vivid','somber','trueweathers')) {
            $old = Join-Path $projectDirectory 'build/checkpoints/058/VPIsometricLighting.jar'
            $expected = if ($order -eq 'tw-first' -or $pack -eq 'library') { 'expected-error' } else { 'expected-success' }
            & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$folder;$old;$runtime" TrueWeathersRuntime059Probe old $pack $expected
            if ($LASTEXITCODE -ne 0) { throw "058 actual TW failure negative control failed: $order $pack" }
            & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$folder;$stage;$runtime" TrueWeathersRuntime059Probe fixed $pack $vpJar (Join-Path $projectDirectory 'build/tw059-shaders')
            if ($LASTEXITCODE -ne 0) { throw "Actual TW + addon shader/GPU regression failed: $order $pack" }
        }
    }
} finally { Pop-Location }
Write-Host 'True Weathers runtime regression 059 PASS: actual TW Java advice, both transformer orders, five packs, pinned058 failure, filter GPU reference and actual VP shader compile/link.'
