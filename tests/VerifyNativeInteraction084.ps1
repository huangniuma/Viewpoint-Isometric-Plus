param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$projectDirectory=Split-Path $PSScriptRoot -Parent
$java="$GameDirectory/jre64/bin/java.exe"
$vpJar='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$gameJar="$GameDirectory/projectzomboid.jar"
$stage="$projectDirectory/build/VPIsometricLighting.jar"
$testClasses="$projectDirectory/build/tests084-native"
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$runtimePath="$testClasses;$projectDirectory/build/tests;$stage;$vpJar;$GameDirectory/ZombieBuddy.jar;$gameJar;$GameDirectory"
$sources=@('WalkToLuaProbe','PlacementLuaProbe','ViewpointCursorLuaProbe','NativeCursorInputProbe','NativeCursorCaptureOwnerProbe','NativeCursorPreviewProbe','NativeContainerHighlightProbe','RotationInteractionProbe') | ForEach-Object {Join-Path $PSScriptRoot ($_+'.java')}
& java -jar "$projectDirectory/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp $runtimePath -d $testClasses @sources
if($LASTEXITCODE -ne 0){throw '084 existing interaction probe compilation failed'}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath RotationInteractionProbe
if($LASTEXITCODE -ne 0){throw '084 existing rotation interaction regression failed'}
$luaFixture = Join-Path $projectDirectory 'build\lua-fixture'
New-Item -ItemType Directory -Force -Path $luaFixture | Out-Null
Copy-Item -LiteralPath (Join-Path $GameDirectory 'stdlib.lua') -Destination (Join-Path $luaFixture 'stdlib.lua') -Force
Push-Location -LiteralPath $luaFixture
try {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath WalkToLuaProbe $GameDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Native walk-to Lua regression failed' }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath PlacementLuaProbe $GameDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Native item placement Lua regression failed' }
    $vpBuildLua = Join-Path (Split-Path (Split-Path (Split-Path $vpJar -Parent) -Parent) -Parent) 'lua\client\Viewpoint_Build.lua'
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$runtimePath;$GameDirectory" ViewpointCursorLuaProbe $GameDirectory $vpBuildLua
    if ($LASTEXITCODE -ne 0) { throw 'Actual VP native cursor Lua wrapper regression failed' }
    $cursorInputFixture = Join-Path $projectDirectory 'build\cursor044-input-woven'
    $cursorPreviewFixture = Join-Path $projectDirectory 'build\cursor044-preview-woven'
    foreach ($entry in @(@('input',$cursorInputFixture), @('preview',$cursorPreviewFixture))) {
        & python -X utf8 (Join-Path $PSScriptRoot 'PrepareCursor044.py') $gameJar (Join-Path $projectDirectory 'build\patch-verification\woven') $entry[1] $entry[0]
        if ($LASTEXITCODE -ne 0) { throw "Native cursor fixture setup failed: $($entry[0])" }
    }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$cursorInputFixture;$runtimePath;$GameDirectory" NativeCursorInputProbe $GameDirectory woven
    if ($LASTEXITCODE -ne 0) { throw 'Actual native cursor confirmation/input ownership regression failed' }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$cursorInputFixture;$runtimePath;$GameDirectory" NativeCursorCaptureOwnerProbe $GameDirectory $vpBuildLua baseline044
    if ($LASTEXITCODE -ne 0) { throw 'Unpatched VP crosshair controller negative control failed' }
    $cursorDriverFixture = Join-Path $projectDirectory 'build\cursor045-driver-woven'
    New-Item -ItemType Directory -Force -Path (Join-Path $cursorDriverFixture 'viewpoint\building') | Out-Null
    Copy-Item -LiteralPath (Join-Path $projectDirectory 'build\patch-verification\woven\viewpoint\building\BuildDriver.class') -Destination (Join-Path $cursorDriverFixture 'viewpoint\building\BuildDriver.class') -Force
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$cursorDriverFixture;$cursorInputFixture;$runtimePath;$GameDirectory" NativeCursorCaptureOwnerProbe $GameDirectory $vpBuildLua
    if ($LASTEXITCODE -ne 0) { throw 'Actual native Attack/Click binding and VP capture controller ownership regression failed' }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$cursorPreviewFixture;$runtimePath;$GameDirectory" NativeCursorPreviewProbe $GameDirectory $vpBuildLua
    if ($LASTEXITCODE -ne 0) { throw 'Actual native cursor rotated preview/ownership regression failed' }
    $containerFixture = Join-Path $projectDirectory 'build\container046-woven'
    foreach ($name in @('render/TargetOutline', 'interact/LootMenu')) {
        $path = Join-Path $containerFixture ("viewpoint/$name.class")
        New-Item -ItemType Directory -Force -Path (Split-Path $path) | Out-Null
        Copy-Item -LiteralPath (Join-Path $projectDirectory "build/patch-verification/woven/viewpoint/$name.class") -Destination $path -Force
    }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$containerFixture;$runtimePath;$GameDirectory" NativeContainerHighlightProbe
    if ($LASTEXITCODE -ne 0) { throw 'Native inventory selection and model highlight GPU regression failed' }
} finally { Pop-Location }
Write-Host 'Native interaction084 regression PASS: rotation, walk-to, item and furniture input/preview ownership, context wrappers and container highlight.'
