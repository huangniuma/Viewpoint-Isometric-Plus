param([string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid',
      [string[]]$Packs = @('library','default','vivid','somber','trueweathers'))
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$java = Join-Path $GameDirectory 'jre64/bin/java.exe'
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$stage = Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$old = Join-Path $projectDirectory 'build/checkpoints/074/VPIsometricLighting.jar'
$testClasses = Join-Path $projectDirectory 'build/tests075-tree'
$woven = Join-Path $projectDirectory 'build/patch-verification/woven'
$runtime = "$testClasses;$(Join-Path $projectDirectory 'build/tests');$vpJar;$(Join-Path $GameDirectory 'ZombieBuddy.jar');$(Join-Path $GameDirectory 'projectzomboid.jar');$GameDirectory"
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$sources = @('NativeTreeStyle075Probe.java','NativeTreeProducerProbe.java','NativeVisibility053Probe.java','NativeTreeRender055Probe.java','NativeTreeRotation058Probe.java','TestShaderPacks.java','PatchWeavingProbe.java') | ForEach-Object { Join-Path $PSScriptRoot $_ }
& java -jar (Join-Path $projectDirectory 'build/tools026/ecj-3.44.0.jar') --release 17 -encoding UTF-8 -cp "$stage;$runtime" -d $testClasses @sources
if ($LASTEXITCODE -ne 0) { throw '075 native tree probe compilation failed' }
Push-Location $projectDirectory
try {
    # Restrict the woven override folder to the renderer classes used by this probe.
    $fixture = Join-Path $projectDirectory 'build/tree075-woven'
    & $java -Xverify:all -cp "$stage;$runtime" NativeTreeRotation058Probe weave (Join-Path $GameDirectory 'projectzomboid.jar') '-' $fixture fixed
    if ($LASTEXITCODE -ne 0) { throw '075 actual native tree camera weave failed' }
    $oldFixture = Join-Path $projectDirectory 'build/tree075-old-woven'
    & $java -Xverify:all -cp "$old;$runtime" NativeTreeRotation058Probe weave (Join-Path $GameDirectory 'projectzomboid.jar') '-' $oldFixture fixed
    if ($LASTEXITCODE -ne 0) { throw '075 historical tree camera weave failed' }
    foreach ($name in @('viewpoint/platform/Shaders','viewpoint/platform/GlProgram')) {
        $source = Join-Path $woven ($name + '.class')
        if (!(Test-Path -LiteralPath $source)) { throw "Missing freshly woven class: $source" }
        $target = Join-Path $fixture ($name + '.class')
        New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
        Copy-Item -LiteralPath $source -Destination $target -Force
        $oldTarget = Join-Path $oldFixture ($name + '.class')
        New-Item -ItemType Directory -Force -Path (Split-Path $oldTarget) | Out-Null
        Copy-Item -LiteralPath $source -Destination $oldTarget -Force
    }
    foreach ($pack in $Packs) {
        & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$oldFixture;$old;$runtime" NativeTreeStyle075Probe baseline074 $pack
        if ($LASTEXITCODE -ne 0) { throw "074 late-tree negative control failed: $pack" }
        & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$stage;$runtime" NativeTreeStyle075Probe $pack
        if ($LASTEXITCODE -ne 0) { throw "075 native tree selected-post regression failed: $pack" }
    }
} finally { Pop-Location }
Write-Host ('075 native tree GPU regression PASS: actual native consumer and selected VP finishing pipeline, 074 late-pass negative controls; packs=' + ($Packs -join ',') + '.')
