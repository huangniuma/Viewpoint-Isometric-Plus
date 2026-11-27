param(
    [string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid',
    [string]$ViewpointJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar',
    [string]$ZombieBuddyJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3619862853\mods\ZombieBuddy\libs\ZombieBuddy.jar'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectDirectory = Split-Path -Parent $PSScriptRoot
$classDirectory = Join-Path $projectDirectory 'build\classes'
$verificationDirectory = Join-Path $projectDirectory 'build\patch-verification'
$probeDirectory = Join-Path $verificationDirectory 'probe'
$gameJar = Join-Path $GameDirectory 'projectzomboid.jar'
$javaRuntime = Join-Path $GameDirectory 'jre64\bin\java.exe'
foreach ($file in @($gameJar, $ViewpointJar, $ZombieBuddyJar, $javaRuntime)) {
    if (!(Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing input: $file" }
}
if (!(Test-Path -LiteralPath (Join-Path $classDirectory 'vpiso\Main.class'))) { throw 'Run build.ps1 first.' }
New-Item -ItemType Directory -Force -Path $probeDirectory | Out-Null
& java -jar (Join-Path $projectDirectory 'build/tools026/ecj-3.44.0.jar') --release 17 -encoding UTF-8 -cp "$ZombieBuddyJar;$classDirectory;$ViewpointJar;$gameJar" -d $probeDirectory (Join-Path $PSScriptRoot 'PatchWeavingProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'Patch verification probe compilation failed' }
# Actual game classes and expanded dependency directory are used; compile-stubs is deliberately absent.
$optionalReflections = @(
    'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3835034413\mods\ProjectReflectionsNightly\42\media\java\client\ProjectReflections.jar',
    'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3835034413\mods\ProjectReflectionsV2\42\media\java\client\ProjectReflectionsV2.jar'
)
foreach ($file in $optionalReflections) {
    if (!(Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing optional-integration verification input: $file" }
}
$trueWeathers = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3813908957\mods\ViewpointTrueWeathers\42\media\java\client\ViewpointTrueWeathers.jar'
$optionalAbsent = if (Test-Path -LiteralPath $trueWeathers) { $optionalReflections += $trueWeathers; '' } else { 'vptw.AmbientDraw' }
# These integrations are optional too. Resolved targets are still woven and verified.
$optionalAbsent += ',pzopt.Darkness,pzopt.Sway,pzopt.EntityShadow,pzopt.Shaders,pzopt.BakeRecord,pzopt.TileRecord'
$runtimePath = "$probeDirectory;$classDirectory;$ViewpointJar;$ZombieBuddyJar;$GameDirectory;$gameJar;" + ($optionalReflections -join ';')
& $javaRuntime '-Xverify:all' '-Dnet.bytebuddy.experimental=true' "-Dvpiso.optionalAbsent=$optionalAbsent" "-Dvpiso.testOverrides=$GameDirectory\pzopt\overrides" -cp $runtimePath PatchWeavingProbe $classDirectory $verificationDirectory 2>&1 |
    Tee-Object -FilePath (Join-Path $verificationDirectory 'result.txt')
if ($LASTEXITCODE -ne 0) { throw 'Offline patch weaving/JVM verification failed; see build/patch-verification/result.txt' }
Write-Host 'Offline verification passed. Game launch and rendered visuals require a separate test.'
