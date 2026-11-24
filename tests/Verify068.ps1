param(
    [string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid',
    [string[]]$Packs = @('library','default','vivid','somber','trueweathers'),
    [switch]$CompileOnly,
    [switch]$SkipNegative
)
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$java = Join-Path $GameDirectory 'jre64/bin/java.exe'
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$twJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3813908957\mods\ViewpointTrueWeathers\42\media\java\client\ViewpointTrueWeathers.jar'
$stage = Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$previous = Join-Path $projectDirectory 'build/checkpoints/067/VPIsometricLighting.jar'
$testClasses = Join-Path $projectDirectory 'build/tests068'
$runtime = "$testClasses;$(Join-Path $projectDirectory 'build/tests');$(Join-Path $projectDirectory 'build/tests060');$vpJar;$twJar;$(Join-Path $GameDirectory 'ZombieBuddy.jar');$(Join-Path $GameDirectory 'projectzomboid.jar');$GameDirectory"
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
& java -jar (Join-Path $projectDirectory 'build/tools026/ecj-3.44.0.jar') --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $testClasses (Join-Path $PSScriptRoot 'NativeShadow068Probe.java') (Join-Path $PSScriptRoot 'NativeMetadata074.java')
if ($LASTEXITCODE -ne 0) { throw '068 native receiver/lighting probe compilation failed' }
if ($CompileOnly) { Write-Host '068 native receiver/lighting probe compiled.'; return }
Push-Location $projectDirectory
try {
    if (!$SkipNegative) {
        # Current lighting helper expands selected resource functions for the independent
        # oracle; preceding067 classes still own the actual compositor/map consumer.
        & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$previous;$stage;$runtime" NativeShadow068Probe library baseline067
        if ($LASTEXITCODE -ne 0) { throw '067 native receiver/lighting negative controls failed' }
    }
    foreach ($pack in $Packs) {
        & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeShadow068Probe $pack
        if ($LASTEXITCODE -ne 0) { throw "068 native receiver/lighting GPU regression failed: $pack" }
    }
} finally { Pop-Location }
$negativeSummary = if ($SkipNegative) { 'negative controls skipped for this invocation' } else { 'pinned067 negative controls reproduced' }
Write-Host "068 native receiver/lighting regression PASS ($($Packs -join ', ')): actual VP maps and selected physical shade reference, competing outlines, mixed levels, indoor floor RGB, room/sky distinction and two lamps; alpha/depth/stencil preserved; $negativeSummary. Controlled fixtures, no live map."
