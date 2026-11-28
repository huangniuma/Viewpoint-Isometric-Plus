param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',[string]$VerifiedWeavingLog='',
      [ValidateSet('','weaving','shaders','interaction','picking','click','settings','camera','furniture','wall','fade','motion')][string]$StartAt='')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$out="$project/research/fade123-final"
$stage="$project/build/VPIsometricLighting.jar"
$managed="$project/build/ViewpointIsometricPlusAddon.jar"
New-Item -ItemType Directory -Force -Path $out | Out-Null
$rendererHash=(Get-FileHash -LiteralPath $stage).Hash
$managedHash=(Get-FileHash -LiteralPath $managed).Hash
function Assert-Artifact {
    if((Get-FileHash -LiteralPath $stage).Hash -ne $rendererHash -or (Get-FileHash -LiteralPath $managed).Hash -ne $managedHash){throw '123 artifact changed during verification'}
}
@{checkpoint='123';version='0.2.45';rendererSHA256=$rendererHash;managedSHA256=$managedHash;scope='Independent CPU/hidden GL; no game attach, save or settings writes';liveVisualAcceptance=$false} | ConvertTo-Json | Set-Content -LiteralPath "$out/regression-artifact.json" -Encoding UTF8
$checks=[ordered]@{weaving='VerifyWeaving.ps1';shaders='Verify118Shaders.ps1';interaction='Verify084.ps1';picking='Verify085.ps1';click='Verify086.ps1';settings='VerifySettings078.ps1';camera='Verify118.ps1';furniture='Verify119.ps1';wall='Verify114.ps1';fade='VerifyFurniture075.ps1';motion='Verify102Motion.ps1'}
if($VerifiedWeavingLog) {
    $content=Get-Content -LiteralPath $VerifiedWeavingLog -Raw
    if(!$content.Contains('patches=156/156;') -or !$content.Contains('verifiedClasses=224/224; failures=0')){throw 'Invalid existing weaving result'}
    Copy-Item -LiteralPath $VerifiedWeavingLog -Destination "$out/weaving.log"
    $checks.Remove('weaving')
}
$runChecks=!$StartAt
foreach($check in $checks.GetEnumerator()) {
    if($check.Key -eq $StartAt){$runChecks=$true}
    if(!$runChecks){continue}
    if($check.Key -eq 'picking') {
        & "$PSScriptRoot/$($check.Value)" -GameDirectory $GameDirectory -Packs @('default','library','vivid','somber') *> "$out/$($check.Key).log"
    } else {
        & "$PSScriptRoot/$($check.Value)" -GameDirectory $GameDirectory *> "$out/$($check.Key).log"
    }
    if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/$($check.Key).log" -Tail 35;throw "123 regression failed: $($check.Key)"}
    Assert-Artifact
    Write-Host "123 PASS $($check.Key)"
}
& "$project/research/fade123-shader/RunOverlap.ps1" -Artifact $stage -Expectation candidate -GameDirectory $GameDirectory *> "$out/overlap.log"
if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/overlap.log" -Tail 35;throw '123 overlap failed'}
Assert-Artifact
& "$project/research/fade123-shader/RunActor.ps1" -Artifact $stage -GameDirectory $GameDirectory *> "$out/actor-gpu.log"
if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/actor-gpu.log" -Tail 35;throw '123 actor GPU failed'}
Assert-Artifact
& "$project/research/fade123-routing/RunCharacters.ps1" -Artifact $stage -GameDirectory $GameDirectory *> "$out/characters.log"
if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/characters.log" -Tail 35;throw '123 characters failed'}
Assert-Artifact
& "$project/research/fade123-routing/RunRouting123.ps1" -Artifact $stage -GameDirectory $GameDirectory *> "$out/routing.log"
if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$out/routing.log" -Tail 35;throw '123 routing failed'}
Assert-Artifact
Write-Host '123 regression PASS; complementary replacement, smooth native visibility, actor alpha/shadow intervals, picking/cutaway and motion; live visual acceptance pending.'
