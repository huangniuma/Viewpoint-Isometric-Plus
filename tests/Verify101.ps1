param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$stage="$p/build/VPIsometricLighting.jar";$old="$p/build/checkpoints/100/VPIsometricLighting.jar"
$classes="$p/build/tests101";$fixture="$p/build/picture101"
$runtime="$classes;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
$java="$GameDirectory/jre64/bin/java.exe"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $classes "$p/tests/PictureSource101Probe.java"
if($LASTEXITCODE -ne 0){throw 'Picture101 compile failed'}
foreach($name in @('viewpoint/render/KeptPicture','zombie/savefile/SavefileThumbnail$CopyTextureDrawer')) {
 $destination="$fixture/$name.class";New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
 Copy-Item -LiteralPath "$p/build/patch-verification/woven/$name.class" -Destination $destination -Force
}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$old;$runtime" PictureSource101Probe baseline100
if($LASTEXITCODE -ne 0){throw 'Previous100 picture negative failed'}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$stage;$runtime" PictureSource101Probe
if($LASTEXITCODE -ne 0){throw 'Picture101 regression failed'}
& python "$p/research/perf101-cpu-review/run-tree-sort.py" --integrated-parity
if($LASTEXITCODE -ne 0){throw 'Tree101 integrated parity failed'}
Write-Host 'Picture and tree101 checks passed.'
