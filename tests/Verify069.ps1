param([string]$GameDirectory='D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference='Stop'
$projectDirectory=Split-Path $PSScriptRoot -Parent
$vp='D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$tw='D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3813908957\mods\ViewpointTrueWeathers\42\media\java\client\ViewpointTrueWeathers.jar'
$classes=Join-Path $projectDirectory 'build/tests069'
$stage=Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$runtime="$classes;$projectDirectory/build/tests;$projectDirectory/build/tests068;$vp;$tw;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources=@('NativeRoof069CandidateProbe.java','NativeRelight069ReceiverProbe.java','NativeRelight069CaptureProbe.java','NativeRelightMask069Probe.java','NativeRelightSlots069Probe.java') | ForEach-Object {Join-Path $PSScriptRoot $_}
& java -jar "$projectDirectory/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $classes @sources
if($LASTEXITCODE -ne 0){throw '069 probes compilation failed'}
$java=Join-Path $GameDirectory 'jre64/bin/java.exe'
& $java -Xverify:all -cp "$stage;$runtime" NativeRelightMask069Probe
if($LASTEXITCODE -ne 0){throw 'Native visibility mask failed'}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeRelightSlots069Probe
if($LASTEXITCODE -ne 0){throw 'Native relight attachment ownership failed'}
foreach($pack in @('default','library','vivid','somber','trueweathers')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeRoof069CandidateProbe $pack
    if($LASTEXITCODE -ne 0){throw "Roof/owner-floor receiver failed $pack"}
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeRelight069ReceiverProbe $pack
    if($LASTEXITCODE -ne 0){throw "Prelit native relight consumer failed $pack"}
}
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeRelight069CaptureProbe $GameDirectory "$projectDirectory/research/build069-pzo-capture" rgba8
if($LASTEXITCODE -ne 0){throw 'Actual installed PZO prelight capture failed'}
Write-Host '069 shadow regression PASS: roofs, owner light floor, prelit floor reconstruction, native sight/fade mask, real PZO chunk/filter/sway variants; five packs. Controlled fixtures, live scene pending.'
