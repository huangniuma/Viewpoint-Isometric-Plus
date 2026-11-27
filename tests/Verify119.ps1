param([string]$Artifact,[string]$Baseline,[string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
if(!$Artifact){$Artifact=Join-Path $project 'build/VPIsometricLighting.jar'}
if(!$Baseline){$Baseline=Join-Path $project 'build/checkpoints/118/VPIsometricLighting.jar'}
$run=Join-Path $project ('research/furniture119/run-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff'))
New-Item -ItemType Directory -Path $run,"$run/classes" | Out-Null
Copy-Item -LiteralPath $Artifact -Destination "$run/candidate.jar"
Copy-Item -LiteralPath $Baseline -Destination "$run/baseline118.jar"
Copy-Item -LiteralPath "$PSScriptRoot/FurnitureWall119Probe.java" -Destination "$run/FurnitureWall119Probe.java"
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$buddy='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3619862853/mods/ZombieBuddy/libs/ZombieBuddy.jar'
$runtime="$run/candidate.jar;$run/classes;$vp;$buddy;$GameDirectory/projectzomboid.jar;$GameDirectory"
& java -jar "$project/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp $runtime -d "$run/classes" "$run/FurnitureWall119Probe.java" *> "$run/compile.log"
if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$run/compile.log";throw '119 compilation failed'}
@{artifactOrigin=$Artifact;baselineOrigin=$Baseline;candidateSHA256=(Get-FileHash -LiteralPath "$run/candidate.jar" -Algorithm SHA256).Hash;baselineSHA256=(Get-FileHash -LiteralPath "$run/baseline118.jar" -Algorithm SHA256).Hash;sourceSHA256=(Get-FileHash -LiteralPath "$run/FurnitureWall119Probe.java" -Algorithm SHA256).Hash;scope='Offline hidden GL production helper/source decode; no game attach/settings/save'} | ConvertTo-Json | Set-Content -LiteralPath "$run/inputs.json" -Encoding UTF8
& "$GameDirectory/jre64/bin/java.exe" --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtime FurnitureWall119Probe "$run/baseline118.jar" *> "$run/gpu.log"
if($LASTEXITCODE -ne 0){Get-Content -LiteralPath "$run/gpu.log" -Tail 30;throw "119 GPU fixture failed; $run"}
Get-Content -LiteralPath "$run/gpu.log" -Tail 1
"119 evidence retained: $run"
