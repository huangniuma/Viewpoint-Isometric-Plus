param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string[]]$Packs=@('default','library','vivid','somber','trueweathers'))
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$stage="$p/build/VPIsometricLighting.jar"
$fixture="$p/build/tree089-woven"
$classes="$p/build/tests089"
$runtime="$classes;$p/build/tests075-tree;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources=@('NativeRain089Probe','NativeTreeWeather089Probe','NativeViewProbe','TestShaderPacks','NativeTreeProducerProbe','NativeVisibility053Probe','NativeTreeRender055Probe','NativeTreeRotation058Probe','PatchWeavingProbe') | ForEach-Object {Join-Path $PSScriptRoot ($_+'.java')}
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $classes @sources
if($LASTEXITCODE -ne 0){throw '089 weather probe compilation failed'}
$java="$GameDirectory/jre64/bin/java.exe"
& $java -Xverify:all -cp "$stage;$runtime" NativeTreeRotation058Probe weave "$GameDirectory/projectzomboid.jar" '-' $fixture fixed
if($LASTEXITCODE -ne 0){throw '089 native tree weave failed'}
$oldFixture="$p/build/tree089-old-woven"
& $java -Xverify:all -cp "$p/build/checkpoints/088/VPIsometricLighting.jar;$runtime" NativeTreeRotation058Probe weave "$GameDirectory/projectzomboid.jar" '-' $oldFixture fixed
if($LASTEXITCODE -ne 0){throw '089 historical tree weave failed'}
foreach($name in @('viewpoint/platform/Shaders','viewpoint/platform/GlProgram')) {
    $target=Join-Path $fixture ($name+'.class')
    New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
    Copy-Item -LiteralPath "$p/build/patch-verification/woven/$name.class" -Destination $target -Force
    $oldTarget=Join-Path $oldFixture ($name+'.class')
    New-Item -ItemType Directory -Force -Path (Split-Path $oldTarget) | Out-Null
    Copy-Item -LiteralPath "$p/build/patch-verification/woven/$name.class" -Destination $oldTarget -Force
}
foreach($probe in @('NativeRain089Probe','NativeTreeWeather089Probe')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$oldFixture;$p/build/checkpoints/088/VPIsometricLighting.jar;$runtime" $probe baseline088 default
    if($LASTEXITCODE -ne 0){throw "088 weather negative control failed: $probe"}
}
foreach($pack in $Packs) {
    foreach($probe in @('NativeRain089Probe','NativeTreeWeather089Probe')) {
        & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$stage;$runtime" $probe $pack
        if($LASTEXITCODE -ne 0){throw "089 weather regression failed: $probe $pack"}
    }
}
Write-Host 'Weather089 checks passed: native mixed-level rain/snow shelter and rain haze on native trees, actual selected shaders, rotation/jitter/stencil/depth, five packs and088 negative controls. Live weather acceptance separate.'
