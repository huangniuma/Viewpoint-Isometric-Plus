param([string]$GameDirectory = 'D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$java = Join-Path $GameDirectory 'jre64/bin/java.exe'
$vpJar = 'D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$stage = Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$previous = Join-Path $projectDirectory 'build/checkpoints/066/VPIsometricLighting.jar'
$testClasses = Join-Path $projectDirectory 'build/tests-reflections067'
$runtime = "$testClasses;$(Join-Path $projectDirectory 'build/tests');$vpJar;$(Join-Path $GameDirectory 'ZombieBuddy.jar');$(Join-Path $GameDirectory 'projectzomboid.jar');$GameDirectory"
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
& java -jar (Join-Path $projectDirectory 'build/tools026/ecj-3.44.0.jar') --release 17 -warn:none -encoding UTF-8 -cp "$stage;$runtime" -d $testClasses (Join-Path $PSScriptRoot 'Reflections067Probe.java')
if ($LASTEXITCODE -ne 0) { throw '067 reflection probe compilation failed' }
$variants = @(
    @('Stable','D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3835034413/mods/ProjectReflectionsNightly/42/media/java/client/ProjectReflections.jar','projectreflections'),
    @('Legacy','D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3835034413/mods/ProjectReflections/42/media/java/client/ProjectReflections.jar','projectreflections'),
    @('V2','D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3835034413/mods/ProjectReflectionsV2/42/media/java/client/ProjectReflectionsV2.jar','prv2')
)
Push-Location $projectDirectory
try {
    & $java --enable-native-access=ALL-UNNAMED '-Dnet.bytebuddy.experimental=true' -Xverify:all -cp "$stage;$runtime" Reflections067Probe absent
    if ($LASTEXITCODE -ne 0) { throw '067 optional-absent linkage failed' }
    foreach ($variant in $variants) {
        Write-Host "Checking Project Reflections $($variant[0])"
        & $java --enable-native-access=ALL-UNNAMED '-Dnet.bytebuddy.experimental=true' -Xverify:all -cp "$previous;$runtime" Reflections067Probe baseline066 $variant[1] $variant[2]
        if ($LASTEXITCODE -ne 0) { throw "066 reflection negative control failed: $($variant[0])" }
        & $java --enable-native-access=ALL-UNNAMED '-Dnet.bytebuddy.experimental=true' -Xverify:all -cp "$stage;$runtime" Reflections067Probe regression $variant[1] $variant[2]
        if ($LASTEXITCODE -ne 0) { throw "067 reflection regression failed: $($variant[0])" }
    }
} finally { Pop-Location }
Write-Host '067 Project Reflections verification PASS: Stable, Legacy and V2 real optional consumers; 066 negative controls; native source alpha; absent-jar linkage. CPU fixtures, no map or GL.'
