param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$out="$project/research/cutaway118/shaders"
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$buddy='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3619862853/mods/ZombieBuddy/libs/ZombieBuddy.jar'
New-Item -ItemType Directory -Force -Path "$out/classes","$out/woven/viewpoint/platform" | Out-Null
foreach($name in @('Shaders','GlProgram')) {
    Copy-Item -LiteralPath "$project/build/patch-verification/woven/viewpoint/platform/$name.class" -Destination "$out/woven/viewpoint/platform/$name.class" -Force
}
$runtime="$out/classes;$project/build/VPIsometricLighting.jar;$vp;$buddy;$GameDirectory/projectzomboid.jar;$GameDirectory"
& java -jar "$project/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp $runtime -d "$out/classes" "$PSScriptRoot/ShaderCompileProbe.java" "$PSScriptRoot/TestShaderPacks.java" *> "$out/compile.log"
if($LASTEXITCODE -ne 0){throw '118 shader probe compilation failed'}
foreach($pack in @('default','library','vivid','somber')) {
    & "$GameDirectory/jre64/bin/java.exe" --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$out/woven;$runtime" ShaderCompileProbe $vp "$out/$pack" $pack *> "$out/$pack.log"
    if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/$pack.log" -Tail 20;throw "118 shader failed: $pack"}
    Get-Content -LiteralPath "$out/$pack.log" -Tail 1
}
Write-Host '118 selected shader packs PASS: default, library, vivid, somber; actual woven shader loading and mesh/forward/shadow links.'
