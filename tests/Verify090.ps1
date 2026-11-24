param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string[]]$Packs=@('default','library','vivid','somber','trueweathers'))
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$stage="$p/build/VPIsometricLighting.jar"
$classes="$p/build/tests090"
$runtime="$classes;$p/build/tests089;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $classes "$p/tests/NativeRainBoundary090Probe.java"
if($LASTEXITCODE -ne 0){throw '090 captured-camera probe compilation failed'}
$java="$GameDirectory/jre64/bin/java.exe"
$fixture="$p/build/tree089-woven"
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$p/build/checkpoints/089/VPIsometricLighting.jar;$runtime" NativeRainBoundary090Probe baseline089 default
if($LASTEXITCODE -ne 0){throw '089 actual floor-boundary leak negative control failed'}
foreach($pack in $Packs) {
 & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$fixture;$stage;$runtime" NativeRainBoundary090Probe $pack
 if($LASTEXITCODE -ne 0){throw "090 actual captured floor regression failed: $pack"}
}
Write-Host 'Rain090 checks passed: actual remote-eye zoom/jitter matrices and floor depths, basement/upper-floor translation, native sheltered/outdoor/missing state, five packs and089 failure control.'
