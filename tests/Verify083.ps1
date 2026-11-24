param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string[]]$Packs=@('default','library','vivid','somber','trueweathers'),[switch]$SkipNegative)
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$tw='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3813908957/mods/ViewpointTrueWeathers/42/media/java/client/ViewpointTrueWeathers.jar'
$runtime="$p/build/tests083;$p/build/tests082;$p/build/tests;$p/build/tests068;$p/build/tests070;$p/build/tests071;$p/build/tests072;$p/build/tests073;$vp;$tw;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
$stage="$p/build/VPIsometricLighting.jar"
New-Item -ItemType Directory -Force -Path "$p/build/tests083" | Out-Null
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d "$p/build/tests083" "$PSScriptRoot/NativeMobileShadow083Probe.java"
if($LASTEXITCODE -ne 0){throw '083 probe compilation failed'}
$java="$GameDirectory/jre64/bin/java.exe"
if(!$SkipNegative) {
 foreach($kind in @('flashlight','headlight')) {
 $negative=& $java --enable-native-access=ALL-UNNAMED '-Dmobile083.unwoven=true' -Xverify:all -cp "$p/build/checkpoints/082/VPIsometricLighting.jar;$runtime" NativeMobileShadow083Probe default $kind 2>&1
 $negative | Write-Output
 if($LASTEXITCODE -eq 0 -or ($negative -join "`n") -notmatch "Missing native $kind shadows") {throw "082 did not reproduce missing $kind shadows"}
 }
 Write-Host 'Previous082 mobile shadow negative control PASS.'
}
$woven="$p/build/mobile083-woven"
New-Item -ItemType Directory -Force -Path $woven | Out-Null
Copy-Item -LiteralPath "$p/build/patch-verification/woven/viewpoint" -Destination $woven -Recurse -Force
foreach($pack in $Packs) {
 & $java --enable-native-access=ALL-UNNAMED '-Dnet.bytebuddy.experimental=true' -Xverify:all -cp "$woven;$stage;$runtime" NativeMobileShadow083Probe $pack
 if($LASTEXITCODE -ne 0){throw "Native mobile shadow regression failed: $pack"}
}
Write-Host 'Mobile083 checks passed: native flashlight/headlight shadow production and reception, supported packs, switching and state preservation.'
