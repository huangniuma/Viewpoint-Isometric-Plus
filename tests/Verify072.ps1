param([string]$GameDirectory='D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid',[string[]]$Packs=@('default','library','vivid','somber','trueweathers'))
$ErrorActionPreference='Stop'
$projectDirectory=Split-Path $PSScriptRoot -Parent
$vp='D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$tw='D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3813908957\mods\ViewpointTrueWeathers\42\media\java\client\ViewpointTrueWeathers.jar'
$stage=Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$pinned=Join-Path $projectDirectory 'build/checkpoints/071/VPIsometricLighting.jar'
$classes=Join-Path $projectDirectory 'build/tests072'
$runtime="$classes;$projectDirectory/build/tests071;$projectDirectory/build/tests;$projectDirectory/build/tests068;$projectDirectory/build/tests070;$vp;$tw;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& java -jar "$projectDirectory/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $classes "$PSScriptRoot/NativePlants072Probe.java" "$PSScriptRoot/NativePackedPlants072Probe.java"
if($LASTEXITCODE -ne 0){throw '072 plant probes compilation failed'}
$java=Join-Path $GameDirectory 'jre64/bin/java.exe'
$woven=Join-Path $projectDirectory 'build/plants072-woven'
$cook='viewpoint/world/Cook$Cooker.class'
New-Item -ItemType Directory -Force -Path (Join-Path $woven 'viewpoint/world') | Out-Null
Copy-Item -LiteralPath (Join-Path $projectDirectory "build/patch-verification/woven/$cook") -Destination (Join-Path $woven $cook) -Force
foreach($pack in $Packs) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$pinned;$stage;$runtime" NativePlants072Probe $pack $classes baseline071
    if($LASTEXITCODE -ne 0){throw "071 plant negative/lamp reference failed: $pack"}
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativePlants072Probe $pack $classes
    if($LASTEXITCODE -ne 0){throw "072 plant receiver failed: $pack"}
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$pinned;$stage;$runtime" NativePackedPlants072Probe $pack $classes baseline071
    if($LASTEXITCODE -ne 0){throw "071 actual packed plant negative/reference failed: $pack"}
    & $java --enable-native-access=ALL-UNNAMED '-Dplants072.wovenCook=true' -Xverify:all -cp "$woven;$stage;$runtime" NativePackedPlants072Probe $pack $classes
    if($LASTEXITCODE -ne 0){throw "072 actual packed bush/hedge receiver failed: $pack"}
}
Write-Host '072 plant reception regression PASS: actual VP high tree/multipart crown/two-plane grass-flag bush, real installed packed bush and hedge OBJ/MTL/alpha through production-woven Cook, 071 seam negatives, real regenerated external-only maps after releasing CascadeLayers, five packs and above-ground cameras, packed physical normals/lamp bytes identical to071 and procedural continuous-surface selected-lamp reference, untagged model exclusion, original alpha/depth/stencil; live visual acceptance pending.'
