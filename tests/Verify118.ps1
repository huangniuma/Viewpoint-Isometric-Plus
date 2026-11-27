param(
    [string]$Artifact,
    [string]$Baseline,
    [string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid'
)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
if(!$Artifact){$Artifact=Join-Path $project 'build/VPIsometricLighting.jar'}
$run=Join-Path $project ('research/cutaway118/run-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff'))
New-Item -ItemType Directory -Path $run,"$run/classes","$run/source" | Out-Null
Copy-Item -LiteralPath $Artifact -Destination "$run/candidate.jar"
if($Baseline){Copy-Item -LiteralPath $Baseline -Destination "$run/baseline117.jar"}
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$buddy='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3619862853/mods/ZombieBuddy/libs/ZombieBuddy.jar'
$probes=@('CutawayRange118Probe','RotationWallProbe','RotationCameraProbe','RotationLayersProbe','RotationInteractionProbe')
$sources=@($probes | ForEach-Object {Copy-Item -LiteralPath "$PSScriptRoot/$_.java" -Destination "$run/source/$_.java";"$run/source/$_.java"})
$runtime="$run/candidate.jar;$run/classes;$vp;$buddy;$GameDirectory/projectzomboid.jar;$GameDirectory"
& java -jar "$project/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp $runtime -d "$run/classes" @sources *> "$run/compile.log"
if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$run/compile.log";throw '118 probe compilation failed'}
@{artifactOrigin=$Artifact;candidateSHA256=(Get-FileHash -LiteralPath "$run/candidate.jar" -Algorithm SHA256).Hash;baselineOrigin=$Baseline;scope='Hidden offline GL and controlled CPU camera/layer/interaction fixtures; no attach/save/settings changes'} | ConvertTo-Json | Set-Content -LiteralPath "$run/inputs.json" -Encoding UTF8
foreach($probe in $probes){
    $arguments=@();if($probe -eq 'CutawayRange118Probe' -and $Baseline){$arguments=@("$run/baseline117.jar")}
    & "$GameDirectory/jre64/bin/java.exe" --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtime $probe @arguments *> "$run/$probe.log"
    if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$run/$probe.log" -Tail 25;throw "118 regression failed: $probe; $run"}
    Get-Content -LiteralPath "$run/$probe.log" -Tail 1
}
"118 evidence retained: $run"
