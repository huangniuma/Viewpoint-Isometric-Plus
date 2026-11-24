param([string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$java = Join-Path $GameDirectory 'jre64\bin\java.exe'
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$testClasses = Join-Path $projectDirectory 'build/tests056'
$stage = Join-Path $projectDirectory 'build/VPIsometricLighting.jar'
$previous = Join-Path $projectDirectory 'build/checkpoints/055/VPIsometricLighting.jar'
$runtime = "$testClasses;$(Join-Path $projectDirectory 'build/tests');$vpJar;$(Join-Path $GameDirectory 'projectzomboid.jar');$GameDirectory"
$sources = @('NativeTreeRender055Probe.java','NativeTreeMask056Probe.java','NativeTreeLifecycle056Probe.java') | ForEach-Object { Join-Path $PSScriptRoot $_ }
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
& java -jar (Join-Path $projectDirectory 'build/tools026/ecj-3.44.0.jar') --release 17 -encoding UTF-8 -cp "$stage;$runtime" -d $testClasses @sources
if ($LASTEXITCODE -ne 0) { throw '056 test compilation failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$previous;$runtime" NativeTreeMask056Probe baseline055
if ($LASTEXITCODE -ne 0) { throw '055 native mask program negative control failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeTreeMask056Probe
if ($LASTEXITCODE -ne 0) { throw '056 native mask queue regression failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$stage;$runtime" NativeTreeLifecycle056Probe
if ($LASTEXITCODE -ne 0) { throw '056 native tree alpha ownership regression failed' }
Write-Host 'Tree stencil regression 056 PASS: pinned055 loses masks under program churn; production helper restores native masks through the real render queue, with native alpha ownership retained.'
