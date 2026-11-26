param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$stage="$p/build/VPIsometricLighting.jar";$base="$p/build/checkpoints/111/VPIsometricLighting.jar"
$classes="$p/build/tests112";$out="$p/research/perf112/final"
New-Item -ItemType Directory -Force -Path $classes,$out | Out-Null
$runtime="$classes;$p/build/tests075;$p/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
$sources=@("$p/tests/Source112Probe.java","$p/tests/Range107GpuProbe.java","$p/research/perf107-allocation/Range107Probe.java")
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $classes @sources
if($LASTEXITCODE -ne 0){throw '112 probe compilation failed'}
$java="$GameDirectory/jre64/bin/java.exe"
foreach($mode in @('parity','exceptions')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" Source112Probe $mode $base $stage
    if($LASTEXITCODE -ne 0){throw "112 packed bounds $mode failed"}
}
foreach($mode in @('baseline','candidate')) {
    $jar=if($mode -eq 'baseline'){$base}else{$stage}
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$jar;$runtime" Range107GpuProbe $mode > "$out/range-$mode.txt"
    if($LASTEXITCODE -ne 0){throw "112 actual GPU owner table failed: $mode"}
    Get-Content -LiteralPath "$out/range-$mode.txt"
}
if((Get-FileHash "$out/range-baseline.txt").Hash -ne (Get-FileHash "$out/range-candidate.txt").Hash){throw '112 GPU source table bytes/start/count changed'}
foreach($extra in @(@(),@('--pzo'))) {
    & python "$p/research/perf112-visibility/run-plan.py" --candidate $stage @extra
    if($LASTEXITCODE -ne 0){throw '112 actual Cook visibility plans changed'}
}
Write-Host 'Performance112 focused checks passed: 1252 exact packed bounds, 4 provider exception controls, native/PZO cooked visibility plans and 288 actual GPU owner table cases; no live traversal FPS claim.'
