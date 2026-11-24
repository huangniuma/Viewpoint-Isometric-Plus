param([string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid')
$ErrorActionPreference='Stop'
$p=Split-Path $PSScriptRoot -Parent
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$tw='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3813908957/mods/ViewpointTrueWeathers/42/media/java/client/ViewpointTrueWeathers.jar'
$runtime="$p/build/tests093;$p/build/VPIsometricLighting.jar;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
New-Item -ItemType Directory -Force -Path "$p/build/tests093" | Out-Null
& java -jar "$p/build/tools026/ecj-3.44.0.jar" --release 17 -warn:none -encoding UTF-8 -cp $runtime -d "$p/build/tests093" "$p/tests/TrueWeather093Probe.java" "$p/tests/TrueWeatherCall093Probe.java"
if($LASTEXITCODE -ne 0){throw '093 probe compile failed'}
Push-Location $p
try {
 & "$GameDirectory/jre64/bin/java.exe" --enable-native-access=ALL-UNNAMED -Xverify:all -cp $runtime TrueWeather093Probe absent
 if($LASTEXITCODE -ne 0){throw '093 optional absence failed'}
 & "$GameDirectory/jre64/bin/java.exe" -Xverify:all -cp "$runtime;$tw" TrueWeatherCall093Probe prepare "$p/build/patch-verification/woven" "$p/build/weather093-woven"
 if($LASTEXITCODE -ne 0){throw '093 call fixture prepare failed'}
 & "$GameDirectory/jre64/bin/java.exe" -Xverify:all -cp "$p/build/weather093-woven;$runtime;$tw" TrueWeatherCall093Probe
 if($LASTEXITCODE -ne 0){throw '093 actual woven callbacks failed'}
 & "$GameDirectory/jre64/bin/java.exe" --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$p/build/weather093-woven;$runtime;$tw" TrueWeather093Probe "$p/build/patch-verification/woven"
 if($LASTEXITCODE -ne 0){throw '093 GPU failed'}
} finally {Pop-Location}
Write-Host 'TrueWeather093 checks passed: optional absence, shader ABI, weather order, normals, depth, snow ownership and mode isolation.'
