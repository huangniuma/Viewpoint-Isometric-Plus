param(
    [Parameter(Mandatory = $true)][string]$GameDirectory,
    [Parameter(Mandatory = $true)][string]$ViewpointJar,
    [Parameter(Mandatory = $true)][string]$ZombieBuddyJar,
    [string]$CompilerJar = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectDirectory = Split-Path -Parent $PSScriptRoot
$buildInputs = @{
    GameDirectory = $GameDirectory
    ViewpointJar = $ViewpointJar
    ZombieBuddyJar = $ZombieBuddyJar
    CompilerJar = $CompilerJar
}
& (Join-Path $projectDirectory 'build.ps1') @buildInputs

$GameDirectory = (Resolve-Path -LiteralPath $GameDirectory).Path
$ViewpointJar = (Resolve-Path -LiteralPath $ViewpointJar).Path
$ZombieBuddyJar = (Resolve-Path -LiteralPath $ZombieBuddyJar).Path
if (!$CompilerJar) { $CompilerJar = Join-Path $projectDirectory 'build/tools/ecj-3.44.0.jar' }
$CompilerJar = (Resolve-Path -LiteralPath $CompilerJar).Path
$java = (Get-Command java -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
$javaRuntime = Join-Path $GameDirectory 'jre64/bin/java.exe'
if (!(Test-Path -LiteralPath $javaRuntime -PathType Leaf)) { throw "Missing game runtime: $javaRuntime" }
$gameJar = Join-Path $GameDirectory 'projectzomboid.jar'
$classDirectory = Join-Path $projectDirectory 'build/classes'
$smokeDirectory = Join-Path $projectDirectory 'build/smoke'
if ((Test-Path -LiteralPath $smokeDirectory) -and
    ((Get-Item -LiteralPath $smokeDirectory -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
    throw 'The generated smoke directory must not be a link or junction.'
}
New-Item -ItemType Directory -Force -Path $smokeDirectory | Out-Null

# Real dependency classes are used both to compile and run; compilation stubs are absent.
$runtimePath = "$smokeDirectory;$classDirectory;$ViewpointJar;$ZombieBuddyJar;$gameJar;$GameDirectory"
$sources = @((Join-Path $PSScriptRoot 'CameraProbe.java'), (Join-Path $PSScriptRoot 'LinkageProbe.java'))
& $java -jar $CompilerJar --release 17 -encoding UTF-8 -cp $runtimePath -d $smokeDirectory @sources
if ($LASTEXITCODE -ne 0) { throw 'CPU smoke probe compilation failed.' }
& $javaRuntime -Xverify:all -cp $runtimePath CameraProbe
if ($LASTEXITCODE -ne 0) { throw 'Camera CPU smoke check failed.' }
& $javaRuntime --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath LinkageProbe $classDirectory
if ($LASTEXITCODE -ne 0) { throw 'JVM linkage CPU smoke check failed.' }
Write-Host 'CPU smoke checks passed.'
