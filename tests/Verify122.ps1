param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$out="$project/research/fade122-final"
$stage="$project/build/VPIsometricLighting.jar"
$managed="$project/build/ViewpointIsometricPlusAddon.jar"
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$buddy='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3619862853/mods/ZombieBuddy/libs/ZombieBuddy.jar'
New-Item -ItemType Directory -Force -Path $out,"$out/classes" | Out-Null
$rendererHash=(Get-FileHash -LiteralPath $stage).Hash
$managedHash=(Get-FileHash -LiteralPath $managed).Hash
function Assert-Artifact {
    if((Get-FileHash -LiteralPath $stage).Hash -ne $rendererHash -or (Get-FileHash -LiteralPath $managed).Hash -ne $managedHash){throw '122 artifact changed during verification'}
}
@{checkpoint='122';version='0.2.44';rendererSHA256=$rendererHash;managedSHA256=$managedHash;scope='Independent CPU/hidden GL; no game attach, save or settings writes';liveVisualAcceptance=$false} | ConvertTo-Json | Set-Content -LiteralPath "$out/regression-artifact.json" -Encoding UTF8
$checks=[ordered]@{weaving='VerifyWeaving.ps1';shaders='Verify118Shaders.ps1';interaction='Verify084.ps1';picking='Verify085.ps1';click='Verify086.ps1';settings='VerifySettings078.ps1';camera='Verify118.ps1';furniture='Verify119.ps1';wall='Verify114.ps1';fade='VerifyFurniture075.ps1';motion='Verify102Motion.ps1'}
foreach($check in $checks.GetEnumerator()) {
    if($check.Key -eq 'picking') {
        & "$PSScriptRoot/$($check.Value)" -GameDirectory $GameDirectory -Packs @('default','library','vivid','somber') *> "$out/$($check.Key).log"
    } else {
        & "$PSScriptRoot/$($check.Value)" -GameDirectory $GameDirectory *> "$out/$($check.Key).log"
    }
    if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/$($check.Key).log" -Tail 35;throw "122 regression failed: $($check.Key)"}
    Assert-Artifact
    Write-Host "122 PASS $($check.Key)"
}
$runtime="$stage;$out/classes;$vp;$buddy;$GameDirectory/projectzomboid.jar;$GameDirectory"
& java -jar "$project/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp $runtime -d "$out/classes" "$PSScriptRoot/Fade122GpuProbe.java" *> "$out/focused-compile.log"
if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/focused-compile.log";throw '122 focused GPU compile failed'}
& "$GameDirectory/jre64/bin/java.exe" --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtime Fade122GpuProbe *> "$out/focused-gpu.log"
if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/focused-gpu.log" -Tail 30;throw '122 focused smooth fade GPU failed'}
Assert-Artifact
Get-Content -LiteralPath "$out/focused-gpu.log" -Tail 1
& "$project/research/fade122-render/RunRouting.ps1" -Artifact $stage -GameDirectory $GameDirectory *> "$out/routing.log"
if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/routing.log" -Tail 35;throw '122 CPU draw routing failed'}
Assert-Artifact
Write-Host '122 regression PASS; smooth transparency, actual draw routing/cleanup, model/mesh shaders, original fades/cutaways, interaction and motion; live visual acceptance pending.'
