param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string[]]$Packs=@('default','library','vivid','somber'))
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$stage="$p/build/VPIsometricLighting.jar";$old="$p/build/checkpoints/099/VPIsometricLighting.jar"
$runtime="$p/build/tests100;$p/build/tests075-tree;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
$java="$GameDirectory/jre64/bin/java.exe"
New-Item -ItemType Directory -Force -Path "$p/build/tests100" | Out-Null
$sources=@('NativeTreeLight100Probe.java','NativeTreeStyle075Probe.java','NativeTreeRotation058Probe.java','NativeTreeRender055Probe.java','NativeTreeProducerProbe.java','NativeVisibility053Probe.java','TestShaderPacks.java','PatchWeavingProbe.java') | ForEach-Object {"$p/tests/$_"}
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d "$p/build/tests100" @sources
if($LASTEXITCODE -ne 0){throw '100 compilation failed'}
Push-Location $p
try {
 foreach($version in @('099','100')) {
  $jar=if($version -eq '099'){$old}else{$stage};$fixture="$p/build/tree100-$version"
  & $java -Xverify:all -cp "$jar;$runtime" NativeTreeRotation058Probe weave "$GameDirectory/projectzomboid.jar" '-' $fixture fixed
  if($LASTEXITCODE -ne 0){throw "100 tree weave failed $version"}
  New-Item -ItemType Directory -Force -Path "$fixture/viewpoint/platform" | Out-Null
  foreach($name in @('Shaders','GlProgram')){Copy-Item -LiteralPath "$p/build/patch-verification/woven/viewpoint/platform/$name.class" -Destination "$fixture/viewpoint/platform/$name.class" -Force}
 }
 foreach($pack in $Packs) {
  & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/tree100-099;$old;$runtime" NativeTreeLight100Probe baseline099 $pack
  if($LASTEXITCODE -ne 0){throw "099 light negative failed $pack"}
  & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/tree100-100;$stage;$runtime" NativeTreeLight100Probe current $pack
  if($LASTEXITCODE -ne 0){throw "100 material failed $pack"}
 }
}finally{Pop-Location}
Write-Host 'Tree100 checks passed.'
