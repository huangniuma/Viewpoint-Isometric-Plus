param(
    [Parameter(Mandatory = $true)][string]$GameDirectory,
    [Parameter(Mandatory = $true)][string]$ViewpointJar,
    [Parameter(Mandatory = $true)][string]$ZombieBuddyJar,
    [string]$CompilerJar = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectDirectory = $PSScriptRoot
$buildDirectory = Join-Path $projectDirectory 'build'
$compilerVersion = '3.44.0'
$compilerSHA256 = '14C46DDC2E511C46C998DA728F6A80F13A689C920F3832402F7AD150F6A6188D'
$java = (Get-Command java -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
$jar = (Get-Command jar -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source

if (!(Test-Path -LiteralPath $GameDirectory -PathType Container)) { throw "Missing game directory: $GameDirectory" }
$GameDirectory = (Resolve-Path -LiteralPath $GameDirectory).Path
$gameJar = Join-Path $GameDirectory 'projectzomboid.jar'
foreach ($file in @($gameJar, $ViewpointJar, $ZombieBuddyJar)) {
    if (!(Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing input: $file" }
}
$ViewpointJar = (Resolve-Path -LiteralPath $ViewpointJar).Path
$ZombieBuddyJar = (Resolve-Path -LiteralPath $ZombieBuddyJar).Path

if ((Test-Path -LiteralPath $buildDirectory) -and
    ((Get-Item -LiteralPath $buildDirectory -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
    throw 'The generated build directory must not be a link or junction.'
}
New-Item -ItemType Directory -Force -Path $buildDirectory | Out-Null

function Reset-GeneratedDirectory([string]$Name) {
    if ($Name -notin @('classes', 'compile-stubs', 'addon-classes')) { throw "Unexpected generated directory: $Name" }
    $target = [IO.Path]::GetFullPath((Join-Path $buildDirectory $Name))
    $expected = [IO.Path]::GetFullPath((Join-Path $projectDirectory "build/$Name"))
    if ($target -ne $expected -or !(Split-Path -Parent $target).Equals($buildDirectory, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove a directory outside build: $target"
    }
    if (Test-Path -LiteralPath $target) {
        $items = @(Get-Item -LiteralPath $target -Force) + @(Get-ChildItem -LiteralPath $target -Recurse -Force)
        $links = $items |
            Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }
        if ($links) { throw "Generated directory contains a link or junction: $target" }
        Remove-Item -LiteralPath $target -Recurse -Force
    }
    New-Item -ItemType Directory -Path $target | Out-Null
    return $target
}

if (!$CompilerJar) {
    $toolDirectory = Join-Path $buildDirectory 'tools'
    New-Item -ItemType Directory -Force -Path $toolDirectory | Out-Null
    $CompilerJar = Join-Path $toolDirectory "ecj-$compilerVersion.jar"
    if (!(Test-Path -LiteralPath $CompilerJar -PathType Leaf)) {
        $download = "$CompilerJar.download"
        $uri = "https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/$compilerVersion/ecj-$compilerVersion.jar"
        Invoke-WebRequest -Uri $uri -OutFile $download -UseBasicParsing
        if ((Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash -ne $compilerSHA256) {
            throw 'Downloaded ECJ checksum does not match the pinned compiler.'
        }
        Move-Item -LiteralPath $download -Destination $CompilerJar -Force
    }
}
if (!(Test-Path -LiteralPath $CompilerJar -PathType Leaf)) { throw "Missing compiler: $CompilerJar" }
$CompilerJar = (Resolve-Path -LiteralPath $CompilerJar).Path
if ((Get-FileHash -LiteralPath $CompilerJar -Algorithm SHA256).Hash -ne $compilerSHA256) {
    throw "Use ECJ $compilerVersion with SHA-256 $compilerSHA256."
}

$classDirectory = Reset-GeneratedDirectory 'classes'
$stubDirectory = Reset-GeneratedDirectory 'compile-stubs'
$managedDirectory = Reset-GeneratedDirectory 'addon-classes'
$stubSources = @(Get-ChildItem -LiteralPath (Join-Path $projectDirectory 'compile-stubs') -Filter '*.java' -Recurse | Sort-Object FullName | ForEach-Object FullName)
& $java -jar $CompilerJar --release 17 -encoding UTF-8 -cp $gameJar -d $stubDirectory @stubSources
if ($LASTEXITCODE -ne 0) { throw 'Compilation ABI build failed.' }

$compilePath = "$stubDirectory;$ViewpointJar;$ZombieBuddyJar;$gameJar"
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectDirectory 'src') -Filter '*.java' -Recurse | Sort-Object FullName | ForEach-Object FullName)
& $java -jar $CompilerJar --release 17 -encoding UTF-8 -cp $compilePath -d $classDirectory @sources
if ($LASTEXITCODE -ne 0) { throw 'Addon compilation failed.' }
$rendererJar = Join-Path $buildDirectory 'VPIsometricLighting.jar'
& $jar --create --file $rendererJar -C $classDirectory vpiso
if ($LASTEXITCODE -ne 0) { throw 'Renderer packaging failed.' }

$managedSources = @(Get-ChildItem -LiteralPath (Join-Path $projectDirectory 'addon-src') -Filter '*.java' -Recurse | Sort-Object FullName | ForEach-Object FullName)
& $java -jar $CompilerJar --release 17 -encoding UTF-8 -cp $ViewpointJar -d $managedDirectory @managedSources
if ($LASTEXITCODE -ne 0) { throw 'Managed addon compilation failed.' }
$managedJar = Join-Path $buildDirectory 'ViewpointIsometricPlusAddon.jar'
& $jar --create --file $managedJar -C $managedDirectory vip
if ($LASTEXITCODE -ne 0) { throw 'Managed addon packaging failed.' }

[ordered]@{
    checkpoint = '113'
    version = '0.2.35'
    stagedOnly = $true
    compiledFor = 'Java 17'
    compilerVersion = $compilerVersion
    compilerSHA256 = $compilerSHA256
    artifactSHA256 = (Get-FileHash -LiteralPath $rendererJar -Algorithm SHA256).Hash
    managedArtifactSHA256 = (Get-FileHash -LiteralPath $managedJar -Algorithm SHA256).Hash
    viewpointSHA256 = (Get-FileHash -LiteralPath $ViewpointJar -Algorithm SHA256).Hash
    gameSHA256 = (Get-FileHash -LiteralPath $gameJar -Algorithm SHA256).Hash
    zombieBuddySHA256 = (Get-FileHash -LiteralPath $ZombieBuddyJar -Algorithm SHA256).Hash
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $buildDirectory 'manifest.json') -Encoding utf8
Write-Host "Staged $rendererJar"
Write-Host "Staged $managedJar"
