param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$out="$project/research/wall114-final"
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$buddy='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3619862853/mods/ZombieBuddy/libs/ZombieBuddy.jar'
$classes="$out/test-classes"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$runtime="$classes;$project/build/tests;$vp;$buddy;$GameDirectory/projectzomboid.jar;$GameDirectory"
& java -jar "$project/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$project/build/VPIsometricLighting.jar;$runtime" -d $classes "$project/tests/WallAttachment114Probe.java"
if($LASTEXITCODE -ne 0){throw '114 probe compilation failed'}
foreach($version in @('baseline','candidate')) {
    $artifact=if($version -eq 'baseline'){"$out/baseline/VPIsometricLighting.jar"}else{"$project/build/VPIsometricLighting.jar"}
    & "$GameDirectory/jre64/bin/java.exe" --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$artifact;$runtime" WallAttachment114Probe $GameDirectory $version *> "$out/$version-gpu.log"
    if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/$version-gpu.log" -Tail 35;throw "114 $version GPU failed"}
    Get-Content -LiteralPath "$out/$version-gpu.log" -Tail 1
}
