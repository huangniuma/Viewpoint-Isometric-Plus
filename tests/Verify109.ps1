param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$classes="$p/build/tests109";New-Item -ItemType Directory -Force -Path $classes | Out-Null
$stage="$p/build/VPIsometricLighting.jar";$base="$p/build/checkpoints/108/VPIsometricLighting.jar"
$runtime="$classes;$p/build/tests075;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -d "$p/build/tests" "$p/tests/Visibility109Bindings.java"
if($LASTEXITCODE -ne 0){throw '109 test binding adapter compilation failed'}
$fixtures=@('ModelUpper061Probe','ModelShadowCutawayProbe','ModelRoomOcclusionProbe','ModelBakedRoofOcclusionProbe','UpperObjects063Probe','PackedObjects063Probe','UpperObjects064Probe','UpperObjects065Probe','UpperOwnership066Probe','PackedSource066Probe','Furniture075Probe') | ForEach-Object {"$p/tests/$_.java"}
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d "$p/build/tests075" @fixtures
if($LASTEXITCODE -ne 0){throw '109 existing fixture adapter compilation failed'}
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $classes "$p/tests/NativeDraw109Probe.java" "$p/tests/Uniform109Weave.java" "$p/tests/NativeCaps109Probe.java"
if($LASTEXITCODE -ne 0){throw '109 focused compilation failed'}
$java="$GameDirectory/jre64/bin/java.exe"
foreach($mode in @('baseline','candidate')) {
 $jar=if($mode -eq 'baseline'){$base}else{$stage}
 & $java -Xverify:all -cp "$stage;$runtime" Uniform109Weave $jar "$classes/$mode"
 if($LASTEXITCODE -ne 0){throw '109 isolated counter weave failed'}
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$jar;$runtime" NativeCaps109Probe
 if($LASTEXITCODE -ne 0){throw "109 original caps mismatch: $mode"}
}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$classes/candidate;$stage;$runtime" NativeDraw109Probe "$classes/baseline"
if($LASTEXITCODE -ne 0){throw '109 native GL comparison failed'}
Write-Host 'Stability109 focused checks passed.'
