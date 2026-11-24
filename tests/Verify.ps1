param([string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectDirectory = Split-Path $PSScriptRoot -Parent
$classes = Join-Path $projectDirectory 'build\classes'
$testClasses = Join-Path $projectDirectory 'build\tests'
$stubs = Join-Path $projectDirectory 'build\compile-stubs'
$gameJar = Join-Path $GameDirectory 'projectzomboid.jar'
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$buddyJar = Join-Path $GameDirectory 'ZombieBuddy.jar'
$java = Join-Path $GameDirectory 'jre64\bin\java.exe'
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$sources = @((Join-Path $PSScriptRoot 'CameraProbe.java'), (Join-Path $PSScriptRoot 'ShaderCompileProbe.java'), (Join-Path $PSScriptRoot 'LinkageProbe.java'), (Join-Path $PSScriptRoot 'ProfileProbe.java'), (Join-Path $PSScriptRoot 'OverlayProbe.java'), (Join-Path $PSScriptRoot 'NativeViewProbe.java'), (Join-Path $PSScriptRoot 'WallCutawayProbe.java'), (Join-Path $PSScriptRoot 'WalkToLuaProbe.java'))
$sources += Join-Path $PSScriptRoot 'TreeCutawayProbe.java'
$sources += Join-Path $PSScriptRoot 'NativeLifecycleProbe.java'
$sources += Join-Path $PSScriptRoot 'NativeDepthProbe.java'
$sources += Join-Path $PSScriptRoot 'NativeVisibility053Probe.java'
$sources += Join-Path $PSScriptRoot 'NativeTreeProducerProbe.java'
$sources += Join-Path $PSScriptRoot 'NativeTreeOverlap053Probe.java'
$sources += Join-Path $PSScriptRoot 'SettingsProbe.java'
$sources += Join-Path $PSScriptRoot 'NativeStyleProbe.java'
$sources += Join-Path $PSScriptRoot 'NativeShadowMapsProbe.java'
$sources += Join-Path $PSScriptRoot 'NativeOverlayProbe.java'
$sources += Join-Path $PSScriptRoot 'ModelEffectsProbe.java'
$sources += Join-Path $PSScriptRoot 'NativeStylePipelineProbe.java'
$sources += Join-Path $PSScriptRoot 'PlacementLuaProbe.java'
$sources += Join-Path $PSScriptRoot 'WaterShaderProbe.java'
$sources += Join-Path $PSScriptRoot 'NativeVPWaterProbe.java'
$sources += Join-Path $PSScriptRoot 'NativeWaterPipelineProbe.java'
$sources += Join-Path $PSScriptRoot 'NativeWaterProbe.java'
$sources += Join-Path $PSScriptRoot 'NativeWaterEdgeProbe.java'
$sources += Join-Path $PSScriptRoot 'ModelWaterMaterialProbe.java'
$sources += Join-Path $PSScriptRoot 'RotationDragProbe.java'
$sources += Join-Path $PSScriptRoot 'RotationCameraProbe.java'
$sources += Join-Path $PSScriptRoot 'RotationInteractionProbe.java'
$sources += Join-Path $PSScriptRoot 'RotationLayersProbe.java'
$sources += Join-Path $PSScriptRoot 'RotationWaterProbe.java'
$sources += Join-Path $PSScriptRoot 'RotationWallProbe.java'
$sources += Join-Path $PSScriptRoot 'NativeGroundProbe.java'
$sources += Join-Path $PSScriptRoot 'NativeMetadata074.java'
$sources += Join-Path $PSScriptRoot 'NativeOutputProbe.java'
$sources += Join-Path $PSScriptRoot 'NativeCaptureProbe.java'
$sources += Join-Path $PSScriptRoot 'PzoGroundShaderProbe.java'
$sources += Join-Path $PSScriptRoot 'PzoPixelLight026Probe.java'
$sources += Join-Path $PSScriptRoot 'NativeSpriteShadowsProbe.java'
$sources += Join-Path $PSScriptRoot 'NativeWallReceiverProbe.java'
$sources += Join-Path $PSScriptRoot 'PatchWeavingProbe.java'
$sources += Join-Path $PSScriptRoot 'SkeletonCompatibilityProbe.java'
$sources += Join-Path $PSScriptRoot 'NativeShadowReliefProbe.java'
$sources += Join-Path $PSScriptRoot 'NativeSurfaceReceiverProbe.java'
$sources += Join-Path $PSScriptRoot 'ModelShadowBasisProbe.java'
$sources += Join-Path $PSScriptRoot 'ModelCutawayProbe.java'
$sources += Join-Path $PSScriptRoot 'ModelShadowCutawayProbe.java'
$sources += Join-Path $PSScriptRoot 'ModelShadowMotionProbe.java'
$sources += Join-Path $PSScriptRoot 'ModelMainProbe.java'
$sources += Join-Path $PSScriptRoot 'ModelVisionProbe.java'
$sources += Join-Path $PSScriptRoot 'RotationTemporalProbe.java'
$sources += Join-Path $PSScriptRoot 'ModelVisionMemoryProbe.java'
$sources += Join-Path $PSScriptRoot 'ModelRoomOcclusionProbe.java'
$sources += Join-Path $PSScriptRoot 'ModelTranslucentDistanceProbe.java'
$sources += Join-Path $PSScriptRoot 'ModelBakedRoofOcclusionProbe.java'
$sources += Join-Path $PSScriptRoot 'ModelSolarScopeProbe.java'
$sources += Join-Path $PSScriptRoot 'NativeCursorInputProbe.java'
$sources += Join-Path $PSScriptRoot 'NativeCursorPreviewProbe.java'
$sources += Join-Path $PSScriptRoot 'ViewpointCursorLuaProbe.java'
$sources += Join-Path $PSScriptRoot 'NativeCursorCaptureOwnerProbe.java'
$sources += Join-Path $PSScriptRoot 'TestShaderPacks.java'
& java -jar 'C:\Users\Win10\Zomboid\Workshop\VP原版视角光影\build\tools026\ecj-3.44.0.jar' --release 17 -encoding UTF-8 -cp "$stubs;$classes;$vpJar;$buddyJar;$gameJar" -d $testClasses @sources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
$runtimePath = "$testClasses;$classes;$vpJar;$buddyJar;$gameJar"
& java -jar 'C:\Users\Win10\Zomboid\Workshop\VP原版视角光影\build\tools026\ecj-3.44.0.jar' --release 17 -encoding UTF-8 -cp $runtimePath -d $testClasses (Join-Path $PSScriptRoot 'NativeContainerHighlightProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'Native container highlight probe compilation against shipped classes failed' }
& (Join-Path $PSScriptRoot 'VerifyPreparation048.ps1') -GameDirectory $GameDirectory
$waterOffsetFixture = Join-Path $projectDirectory 'build\water041-woven'
& python -X utf8 (Join-Path $PSScriptRoot 'PrepareWater041.py') $gameJar (Join-Path $projectDirectory 'build\patch-verification\woven') $waterOffsetFixture
if ($LASTEXITCODE -ne 0) { throw 'Native water projection fixture setup failed' }
foreach ($waterMode in @('native','library')) {
    $waterArgs = @('offset041')
    if ($waterMode -ne 'native') { $waterArgs += $waterMode }
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$waterOffsetFixture;$runtimePath;$GameDirectory" NativeWaterEdgeProbe @waterArgs
    if ($LASTEXITCODE -ne 0) { throw "Large-map offset/zoom water regression failed: $waterMode" }
}
$memoryFixture = Join-Path $projectDirectory 'build\memory040-woven'
New-Item -ItemType Directory -Force -Path (Join-Path $memoryFixture 'pzopt') | Out-Null
Copy-Item -LiteralPath (Join-Path $projectDirectory 'build\patch-verification\woven\pzopt\Darkness.class') -Destination (Join-Path $memoryFixture 'pzopt\Darkness.class') -Force
& $java --enable-native-access=ALL-UNNAMED '-Dnet.bytebuddy.experimental=true' -Xverify:all -cp "$memoryFixture;$runtimePath;$GameDirectory" ModelVisionMemoryProbe $GameDirectory
if ($LASTEXITCODE -ne 0) { throw 'Actual PZO memory/native visibility color compatibility regression failed' }
$waterFixture = Join-Path $projectDirectory 'build\water039-woven'
$waterBaselineFixture = Join-Path $projectDirectory 'build\water039-baseline-woven'
foreach ($fixture in @($waterFixture,$waterBaselineFixture)) {
    foreach ($name in @('render/Meshes','render/ShadowPass','platform/GlProgram','platform/Shaders')) {
        $destination = Join-Path $fixture ("viewpoint/$name.class")
        New-Item -ItemType Directory -Force -Path (Split-Path $destination) | Out-Null
        Copy-Item -LiteralPath (Join-Path $projectDirectory "build/patch-verification/woven/viewpoint/$name.class") -Destination $destination -Force
    }
}
Copy-Item -LiteralPath (Join-Path $projectDirectory 'build/patch-verification/woven/viewpoint/render/TemporalPass.class') -Destination (Join-Path $waterFixture 'viewpoint/render/TemporalPass.class') -Force
$previousMapWaterJar = Join-Path $projectDirectory 'build\checkpoints\040\VPIsometricLighting.jar'
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$previousMapWaterJar;$waterFixture;$runtimePath;$GameDirectory" NativeWaterEdgeProbe baseline040-offset
if ($LASTEXITCODE -ne 0) { throw 'Pinned040 map-coordinate water negative control failed' }
$previousFloorJar = Join-Path $projectDirectory 'build\checkpoints\039\VPIsometricLighting.jar'
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$previousFloorJar;$waterFixture;$runtimePath;$GameDirectory" NativeWaterEdgeProbe baseline039-pull
if ($LASTEXITCODE -ne 0) { throw 'Pinned039 actual solid-floor water intersections negative control failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$waterFixture;$runtimePath;$GameDirectory" NativeWaterEdgeProbe pull040
if ($LASTEXITCODE -ne 0) { throw 'Actual floor depth/native shore distance and movement regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$waterFixture;$runtimePath;$GameDirectory" NativeWaterEdgeProbe pull040 library
if ($LASTEXITCODE -ne 0) { throw 'Actual floor depth/VP shore distance and movement regression failed' }
$previousShoreJar = Join-Path $projectDirectory 'build\checkpoints\038\VPIsometricLighting.jar'
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$previousShoreJar;$waterBaselineFixture;$runtimePath;$GameDirectory" NativeWaterEdgeProbe baseline038
if ($LASTEXITCODE -ne 0) { throw 'Pinned038 actual mesh shoreline negative control failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$waterFixture;$runtimePath;$GameDirectory" RotationTemporalProbe
if ($LASTEXITCODE -ne 0) { throw 'Actual temporal camera scope/history regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$waterFixture;$runtimePath;$GameDirectory" NativeWaterEdgeProbe model039
if ($LASTEXITCODE -ne 0) { throw 'Actual mesh native shoreline zero-motion regression failed' }
foreach ($pack in @('library','default','vivid')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$waterFixture;$runtimePath;$GameDirectory" NativeWaterEdgeProbe model039 $pack
    if ($LASTEXITCODE -ne 0) { throw "Actual mesh VP water shoreline zero-motion regression failed: $pack" }
}
$previousWaterJar = Join-Path $projectDirectory 'build\checkpoints\036\VPIsometricLighting.jar'
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$previousWaterJar;$runtimePath;$GameDirectory" NativeVPWaterProbe library baseline036
if ($LASTEXITCODE -ne 0) { throw 'Previous-artifact water MRT negative control failed' }
$previousDoorJar = Join-Path $projectDirectory 'build\checkpoints\035\VPIsometricLighting.jar'
& $java --enable-native-access=ALL-UNNAMED '-Dnet.bytebuddy.experimental=true' -Xverify:all -cp "$previousDoorJar;$runtimePath;$GameDirectory" ModelCutawayProbe baseline035
if ($LASTEXITCODE -ne 0) { throw 'Previous-artifact upper door negative control failed' }
$previousUpperShadowJar = Join-Path $projectDirectory 'build\checkpoints\037\VPIsometricLighting.jar'
& $java --enable-native-access=ALL-UNNAMED '-Dnet.bytebuddy.experimental=true' -Xverify:all -cp "$previousUpperShadowJar;$runtimePath;$GameDirectory" ModelCutawayProbe baseline037
if ($LASTEXITCODE -ne 0) { throw 'Previous-artifact independent upper shadow negative control failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$previousUpperShadowJar;$runtimePath;$GameDirectory" ModelShadowCutawayProbe baseline037
if ($LASTEXITCODE -ne 0) { throw 'Previous-artifact mesh upper shadow negative control failed' }
$previousRoofJar = Join-Path $projectDirectory 'build\checkpoints\041\VPIsometricLighting.jar'
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$previousRoofJar;$runtimePath;$GameDirectory" ModelShadowCutawayProbe baseline041
if ($LASTEXITCODE -ne 0) { throw 'Pinned041 physical roof deletion negative control failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$previousRoofJar;$runtimePath;$GameDirectory" ModelRoomOcclusionProbe baseline041
if ($LASTEXITCODE -ne 0) { throw 'Pinned041 closed-room direct-light leak negative control failed' }
$previousBakedRoofJar = Join-Path $projectDirectory 'build\checkpoints\042\VPIsometricLighting.jar'
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$previousBakedRoofJar;$runtimePath;$GameDirectory" ModelBakedRoofOcclusionProbe baseline042
if ($LASTEXITCODE -ne 0) { throw 'Pinned042 physical baked slab omission negative control failed' }
$upperFixture = Join-Path $projectDirectory 'build\shadow043-woven'
$mainFixture = Join-Path $projectDirectory 'build\main038-woven'
foreach ($name in @('render/Meshes','render/ShadowPass','platform/GlProgram','platform/Shaders')) {
    $destination = Join-Path $mainFixture ("viewpoint/$name.class")
    New-Item -ItemType Directory -Force -Path (Split-Path $destination) | Out-Null
    Copy-Item -LiteralPath (Join-Path $projectDirectory "build/patch-verification/woven/viewpoint/$name.class") -Destination $destination -Force
}
$upperDestination = Join-Path $upperFixture 'viewpoint/render/Meshes.class'
New-Item -ItemType Directory -Force -Path (Split-Path $upperDestination) | Out-Null
Copy-Item -LiteralPath (Join-Path $projectDirectory 'build/patch-verification/woven/viewpoint/render/Meshes.class') -Destination $upperDestination -Force
& $java --enable-native-access=ALL-UNNAMED '-Dnet.bytebuddy.experimental=true' -Xverify:all -cp "$previousUpperShadowJar;$mainFixture;$runtimePath;$GameDirectory" ModelVisionProbe baseline037
if ($LASTEXITCODE -ne 0) { throw 'Previous-artifact model/native memory depth negative control failed' }
& $java --enable-native-access=ALL-UNNAMED '-Dnet.bytebuddy.experimental=true' -Xverify:all -cp "$mainFixture;$runtimePath;$GameDirectory" ModelVisionProbe
if ($LASTEXITCODE -ne 0) { throw 'Model/native memory depth GPU regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$upperFixture;$runtimePath;$GameDirectory" ModelShadowCutawayProbe
if ($LASTEXITCODE -ne 0) { throw 'Physical roof occlusion through native camera cutaways regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$upperFixture;$runtimePath;$GameDirectory" ModelRoomOcclusionProbe
if ($LASTEXITCODE -ne 0) { throw 'Closed-room sun/moon occlusion and physical opening regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$upperFixture;$runtimePath;$GameDirectory" ModelBakedRoofOcclusionProbe
if ($LASTEXITCODE -ne 0) { throw 'Actual physical baked floor/generated ceiling occlusion regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$upperFixture;$runtimePath;$GameDirectory" ModelSolarScopeProbe
if ($LASTEXITCODE -ne 0) { throw 'Woven physical solar bucket scope isolation/exception regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$runtimePath;$GameDirectory" ModelShadowMotionProbe
if ($LASTEXITCODE -ne 0) { throw 'World-space terrain shadow camera motion regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$mainFixture;$runtimePath;$GameDirectory" ModelMainProbe
if ($LASTEXITCODE -ne 0) { throw 'Main gbuffer/depth/sun moving scene regression failed' }
& $java --enable-native-access=ALL-UNNAMED '-Dnet.bytebuddy.experimental=true' -Xverify:all -cp "$runtimePath;$GameDirectory" ModelCutawayProbe
if ($LASTEXITCODE -ne 0) { throw 'Native independent model cutaway regression failed' }
$previousShadowJar = Join-Path $projectDirectory 'build\checkpoints\034\VPIsometricLighting.jar'
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$previousShadowJar;$runtimePath;$GameDirectory" ModelShadowBasisProbe baseline034
if ($LASTEXITCODE -ne 0) { throw 'Previous-artifact shadow cache negative control failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$runtimePath;$GameDirectory" ModelShadowBasisProbe
if ($LASTEXITCODE -ne 0) { throw 'Model terrain shadow cache regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$runtimePath;$GameDirectory" NativeSurfaceReceiverProbe (Join-Path $projectDirectory 'build\checkpoints\017\VPIsometricLighting.jar')
if ($LASTEXITCODE -ne 0) { throw 'Native geometry surface shadow regression failed' }
& $java --enable-native-access=ALL-UNNAMED '-Dnet.bytebuddy.experimental=true' -Xverify:all -cp "$runtimePath;$GameDirectory" SkeletonCompatibilityProbe $vpJar $classes (Join-Path $projectDirectory 'build\checkpoints\031\VPIsometricLighting.jar')
if ($LASTEXITCODE -ne 0) { throw 'VP native skeleton compatibility regression failed' }
& $java --enable-native-access=ALL-UNNAMED '-Dnet.bytebuddy.experimental=true' -Xverify:all -cp "$runtimePath;$GameDirectory" NativeShadowReliefProbe
if ($LASTEXITCODE -ne 0) { throw 'Native flat-terrain shadow capture regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeOutputProbe $GameDirectory
if ($LASTEXITCODE -ne 0) { throw 'Native original MRT output regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeCaptureProbe
if ($LASTEXITCODE -ne 0) { throw 'Native color capture regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeGroundProbe $GameDirectory (Join-Path $projectDirectory 'research\build018-shadows\lighting_outdoor_01_16.png')
if ($LASTEXITCODE -ne 0) { throw 'Native ground/foreground ownership regression failed' }
& $java --enable-native-access=ALL-UNNAMED '-Dnet.bytebuddy.experimental=true' -Xverify:all -cp $runtimePath NativeWallReceiverProbe $GameDirectory
if ($LASTEXITCODE -ne 0) { throw 'Native exterior wall shadow receiver regression failed' }
& $java --enable-native-access=ALL-UNNAMED '-Dnet.bytebuddy.experimental=true' -Xverify:all -cp $runtimePath NativeSpriteShadowsProbe (Join-Path $projectDirectory 'research\build018-shadows')
if ($LASTEXITCODE -ne 0) { throw 'Native fixed shadow replacement/cache regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$runtimePath;$GameDirectory" PzoGroundShaderProbe $GameDirectory (Join-Path $projectDirectory 'research\build026-pzo-generated')
if ($LASTEXITCODE -ne 0) { throw 'Actual PZO generated native shader verification failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$runtimePath;$GameDirectory" PzoPixelLight026Probe $GameDirectory (Join-Path $projectDirectory 'research\build026-pixel-source-fixed')
if ($LASTEXITCODE -ne 0) { throw 'PZO PixelLight chunk compile/link/depth regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath CameraProbe
if ($LASTEXITCODE -ne 0) { throw 'Camera verification failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath RotationDragProbe
if ($LASTEXITCODE -ne 0) { throw 'Middle-mouse drag/cursor regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath RotationCameraProbe
if ($LASTEXITCODE -ne 0) { throw 'Rotation camera verification failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath RotationInteractionProbe
if ($LASTEXITCODE -ne 0) { throw 'Rotation interaction verification failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath RotationLayersProbe
if ($LASTEXITCODE -ne 0) { throw 'Rotation layers verification failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath RotationWaterProbe $GameDirectory
if ($LASTEXITCODE -ne 0) { throw 'Rotated native water verification failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeWaterEdgeProbe
if ($LASTEXITCODE -ne 0) { throw 'Native shoreline temporal depth/coverage regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath RotationWallProbe
if ($LASTEXITCODE -ne 0) { throw 'Rotated wall verification failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath ShaderCompileProbe $vpJar (Join-Path $projectDirectory 'build\shader-check')
if ($LASTEXITCODE -ne 0) { throw 'Shader compilation failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath LinkageProbe $classes
if ($LASTEXITCODE -ne 0) { throw 'Runtime linkage verification failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeLifecycleProbe
if ($LASTEXITCODE -ne 0) { throw 'Native cache lifecycle regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath ProfileProbe
if ($LASTEXITCODE -ne 0) { throw 'Compatible-profile regression failed' }
$settingsFixture = Join-Path $projectDirectory 'build\settings-fixture017'
foreach ($scenario in @('write-off', 'read-off', 'read-on')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath SettingsProbe $settingsFixture $scenario
    if ($LASTEXITCODE -ne 0) { throw "VP settings persistence regression failed: $scenario" }
}
$addonUiFixture = Join-Path $projectDirectory 'build\options077-fixture'
$addonUiClass = Join-Path $addonUiFixture 'viewpoint\platform'
New-Item -ItemType Directory -Force -Path $addonUiClass | Out-Null
Copy-Item -LiteralPath (Join-Path $projectDirectory 'build\patch-verification\woven\viewpoint\platform\SettingsPages.class') -Destination $addonUiClass -Force
& $java --enable-native-access=ALL-UNNAMED "-Djava.library.path=$GameDirectory" -Xverify:all -cp "$addonUiFixture;$runtimePath" OverlayProbe
if ($LASTEXITCODE -ne 0) { throw 'Visible-overlay/button regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeStyleProbe
if ($LASTEXITCODE -ne 0) { throw 'Native color-style GPU regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeStylePipelineProbe
if ($LASTEXITCODE -ne 0) { throw 'Standalone native style full-route regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeOverlayProbe
if ($LASTEXITCODE -ne 0) { throw 'Native picture compositor GPU regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeShadowMapsProbe
if ($LASTEXITCODE -ne 0) { throw 'VP map-only shadow producer regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath ModelEffectsProbe
if ($LASTEXITCODE -ne 0) { throw 'VP 3D model effect controls regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$previousRoofJar;$runtimePath;$GameDirectory" ModelTranslucentDistanceProbe baseline041
if ($LASTEXITCODE -ne 0) { throw 'Pinned041 translucent fog-blue negative control failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$runtimePath;$GameDirectory" ModelTranslucentDistanceProbe
if ($LASTEXITCODE -ne 0) { throw 'Actual translucent producer/post ray-distance and fog regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath WaterShaderProbe
if ($LASTEXITCODE -ne 0) { throw 'VP isometric water GPU regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeVPWaterProbe
if ($LASTEXITCODE -ne 0) { throw 'Independent native VP water GPU regression failed' }
foreach ($pack in @('vivid','default')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeVPWaterProbe $pack
    if ($LASTEXITCODE -ne 0) { throw "Independent water pack regression failed: $pack" }
}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeVPWaterProbe default tony
if ($LASTEXITCODE -ne 0) { throw 'Independent water Tony LUT regression failed' }
& (Join-Path $PSScriptRoot 'VerifyWater054.ps1') -GameDirectory $GameDirectory
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeWaterPipelineProbe
if ($LASTEXITCODE -ne 0) { throw 'Independent water hook/queue/drawer regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeWaterProbe
if ($LASTEXITCODE -ne 0) { throw 'Original water body/shore producer regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath ModelWaterMaterialProbe
if ($LASTEXITCODE -ne 0) { throw 'Native model-water ownership/depth regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeViewProbe
if ($LASTEXITCODE -ne 0) { throw 'Native visibility/GPU regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$(Join-Path $projectDirectory 'build\checkpoints\052\VPIsometricLighting.jar');$runtimePath" NativeVisibility053Probe baseline052
if ($LASTEXITCODE -ne 0) { throw 'Pinned 052 native blackout flicker negative control failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeVisibility053Probe
if ($LASTEXITCODE -ne 0) { throw 'Native reconstructed-floor blackout stability failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeDepthProbe
if ($LASTEXITCODE -ne 0) { throw 'Native depth mapping/GPU regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath WallCutawayProbe $GameDirectory
if ($LASTEXITCODE -ne 0) { throw 'Native wall cutaway/GPU regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath TreeCutawayProbe $GameDirectory
if ($LASTEXITCODE -ne 0) { throw 'Native tree cutaway/GPU regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeTreeProducerProbe
if ($LASTEXITCODE -ne 0) { throw 'Native tree producer regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$(Join-Path $projectDirectory 'build\checkpoints\052\VPIsometricLighting.jar');$runtimePath" NativeTreeOverlap053Probe baseline052
if ($LASTEXITCODE -ne 0) { throw 'Pinned 052 crown overlap negative control failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath NativeTreeOverlap053Probe
if ($LASTEXITCODE -ne 0) { throw 'Native translucent crown overlap failed' }
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
& (Join-Path $PSScriptRoot 'VerifyBuiltins047.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'VerifyTrueWeathers048.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'VerifyModel049.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'VerifyGrass052.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'Verify055.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'Verify056.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'Verify057.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'Verify058.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'Verify059.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'Verify060.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'Verify061.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'Verify062.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'Verify063.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'Verify064.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'Verify065.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'Verify066.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'VerifyReflections067.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'Verify068.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'Verify069.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'Verify070.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'Verify071.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'Verify072.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'Verify073.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'VerifyFurniture075.ps1') -GameDirectory $GameDirectory
& (Join-Path $PSScriptRoot 'VerifyTree075.ps1') -GameDirectory $GameDirectory
Write-Host 'All addon 077 checks passed.'
