param([string]$GameDirectory = 'D:\ProgramFiles\Steam\steamapps\common\ProjectZomboid')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectDirectory = Split-Path $PSScriptRoot -Parent
$classes = Join-Path $projectDirectory 'build\classes'
$testClasses = Join-Path $projectDirectory 'build\tests076'
$stubs = Join-Path $projectDirectory 'build\compile-stubs'
$gameJar = Join-Path $GameDirectory 'projectzomboid.jar'
$vpJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3809306528\mods\Viewpoint\42\media\java\client\Viewpoint.jar'
$buddyJar = 'D:\ProgramFiles\Steam\steamapps\workshop\content\108600\3619862853\mods\ZombieBuddy\libs\ZombieBuddy.jar'
$java = Join-Path $GameDirectory 'jre64\bin\java.exe'
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$sources = @('LinkageProbe.java', 'ProfileProbe.java', 'SettingsProbe.java', 'OverlayProbe.java') | ForEach-Object { Join-Path $PSScriptRoot $_ }
& java -jar (Join-Path $projectDirectory 'build\tools026\ecj-3.44.0.jar') --release 17 -encoding UTF-8 -cp "$stubs;$classes;$vpJar;$buddyJar;$gameJar" -d $testClasses @sources
if ($LASTEXITCODE -ne 0) { throw 'Naming regression probes did not compile' }
$runtimePath = "$testClasses;$classes;$vpJar;$buddyJar;$gameJar"
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath LinkageProbe $classes
if ($LASTEXITCODE -ne 0) { throw 'Runtime linkage verification failed' }
& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath ProfileProbe
if ($LASTEXITCODE -ne 0) { throw 'Compatible-profile regression failed' }
$settingsFixture = Join-Path $projectDirectory 'build\settings-fixture076'
foreach ($scenario in @('write-off', 'read-off', 'read-on')) {
    & $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtimePath SettingsProbe $settingsFixture $scenario
    if ($LASTEXITCODE -ne 0) { throw "VP settings persistence regression failed: $scenario" }
}
$addonUiFixture = Join-Path $projectDirectory 'build\addons076-fixture'
$addonUiClass = Join-Path $addonUiFixture 'viewpoint\platform'
New-Item -ItemType Directory -Force -Path $addonUiClass | Out-Null
Copy-Item -LiteralPath (Join-Path $projectDirectory 'build\patch-verification\woven\viewpoint\platform\AddonsPage.class') -Destination $addonUiClass -Force
& $java --enable-native-access=ALL-UNNAMED "-Djava.library.path=$GameDirectory" -Xverify:all -cp "$addonUiFixture;$runtimePath" OverlayProbe
if ($LASTEXITCODE -ne 0) { throw 'Bilingual Addons UI regression failed' }
Write-Host 'Naming076 checks passed: linkage, compatible profiles, settings persistence and bilingual UI.'
