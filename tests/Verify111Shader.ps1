param(
    [string]$GameDirectory='D:/ProgramFiles/Steam/steamapps/common/ProjectZomboid',
    [string]$Artifact='',
    [string]$Baseline='',
    [string]$Output='research/tree111-testing/final',
    [switch]$CpuOnly,
    [switch]$RenderParity
)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
if(!$Artifact){$Artifact=Join-Path $project 'build/VPIsometricLighting.jar'}
if(!$Baseline){$Baseline=Join-Path $project 'build/checkpoints/110/VPIsometricLighting.jar'}
if(![IO.Path]::IsPathRooted($Output)){$Output=Join-Path $project $Output}
$vp='D:/ProgramFiles/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar'
$java=Join-Path $GameDirectory 'jre64/bin/java.exe'
$compiler=Join-Path $project 'build/tools026/ecj-3.44.0.jar'
$validator=Join-Path $project 'research/nvidia110-tools/deps/glslang-validator-prebuilt-predownloaded-0.0.2/package/bin/glslangValidator.exe'
$classes=Join-Path $project 'build/tests111'
$overrides=Join-Path $Output 'shader-overrides'
$runtime="$classes;$project/build/tests;$vp;$GameDirectory/ZombieBuddy.jar;$GameDirectory/projectzomboid.jar;$GameDirectory"
foreach($required in @($Artifact,$Baseline,$vp,$java,$compiler,$validator)){
    if(!(Test-Path -LiteralPath $required)){throw "Missing dependency: $required"}
}
New-Item -ItemType Directory -Force -Path $classes,"$overrides/viewpoint/platform" | Out-Null
$sources=@('NativeTree111Probe','NativeTreeStyle075Probe','NativeTreeProducerProbe','NativeVisibility053Probe','NativeTreeRender055Probe','NativeTreeRotation058Probe','TestShaderPacks','PatchWeavingProbe') | ForEach-Object {Join-Path $PSScriptRoot ($_+'.java')}
& java -jar $compiler --release 17 -warn:none -encoding UTF-8 -cp "$Artifact;$runtime" -d $classes @sources
if($LASTEXITCODE -ne 0){throw 'Tree111 probe compilation failed'}
# Run VerifyWeaving.ps1 after build.ps1 before this script. The copied source hooks
# call the arm's own addon classes; no installed game files are modified.
foreach($name in @('Shaders','GlProgram')){
    $woven=Join-Path $project "build/patch-verification/woven/viewpoint/platform/$name.class"
    if(!(Test-Path -LiteralPath $woven)){throw "Missing freshly woven shader class: $woven"}
    Copy-Item -LiteralPath $woven -Destination "$overrides/viewpoint/platform/$name.class" -Force
}
$hashes=@($Artifact,$Baseline,$vp,$validator,(Join-Path $project 'research/error030-shader-ownership/poolpit-only-light.frag')) | Get-FileHash -Algorithm SHA256 | Select-Object Path,Hash
$hashes | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $Output 'inputs.json') -Encoding UTF8

function Run-Tree111([string]$Arm,[string]$Jar,[string]$Mode,[string]$Folder,[string]$Prefix){
    New-Item -ItemType Directory -Force -Path $Folder | Out-Null
    $result=& $java --enable-native-access=ALL-UNNAMED -Xverify:all -cp "$Prefix;$Jar;$runtime" NativeTree111Probe $Mode $Folder $Arm 2>&1
    $exit=$LASTEXITCODE
    $result | Tee-Object -FilePath (Join-Path $Folder 'probe.log') | Write-Host
    if($exit -ne 0){throw "Tree111 $Arm $Mode failed (exit $exit)"}
}

Push-Location $project
try {
    $mode=if($CpuOnly){'dump'}else{'shader'}
    $oldOutput=Join-Path $Output 'baseline110'
    $newOutput=Join-Path $Output 'candidate111'
    Run-Tree111 'baseline' $Baseline $mode $oldOutput $overrides
    Run-Tree111 'candidate' $Artifact $mode $newOutput $overrides
    $strict=@()
    foreach($arm in @('baseline110','candidate111')){
        foreach($fragment in Get-ChildItem -LiteralPath (Join-Path $Output $arm) -Filter '*.frag' | Where-Object Name -NotLike '*-selected-expanded.frag'){
            $expectedFailure=$arm -eq 'baseline110' -and $fragment.BaseName -notlike '*-plain'
            $log=& $validator -S frag $fragment.FullName 2>&1 | Out-String
            $exit=$LASTEXITCODE
            Set-Content -LiteralPath ($fragment.FullName+'.strict.log') -Value $log -Encoding UTF8
            if($expectedFailure){
                if($exit -eq 0 -or $log -notmatch "vpTreeArt.*undeclared identifier"){throw "Strict pinned110 ordering negative missing: $($fragment.Name)`n$log"}
            }elseif($exit -ne 0){throw "Strict shader compile failed: $arm/$($fragment.Name)`n$log"}
            $strict+=[pscustomobject]@{arm=$arm;fixture=$fragment.BaseName;expectedFailure=$expectedFailure;exitCode=$exit}
        }
    }
    if($strict.Count -ne 18){throw "Incomplete strict shader matrix: $($strict.Count)"}
    $strict | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $Output 'strict-results.json') -Encoding UTF8

    # Moving declarations must preserve every remaining shader token, including actual
    # selected sun/lamp/torch/spot material functions, native alpha and root depth.
    foreach($pack in @('default','library','vivid','somber')){
        $texts=@()
        foreach($folder in @($oldOutput,$newOutput)){
            $text=Get-Content -LiteralPath (Join-Path $folder "$pack-plain.frag") -Raw
            $text=$text -replace 'vec3\s+vPos\s*;','' -replace 'vec4\s+vpTreeArt\s*,\s*vpTreeEngine\s*;','' -replace 'float\s+vpTreeOutdoors\s*;',''
            $texts+=($text -replace '\s+','')
        }
        if($texts[0] -cne $texts[1]){throw "Shader tokens changed outside moved declarations: $pack"}
    }
    Write-Host 'Tree111 strict PASS: 13 successful shaders, 5 pinned110 undefined-global controls; all four plain pack shader token streams unchanged after removing moved declarations.'

    if($RenderParity -and !$CpuOnly){
        foreach($arm in @('baseline110','candidate111')){
            $jar=if($arm -eq 'baseline110'){$Baseline}else{$Artifact}
            $fixture=Join-Path $Output "$arm-native-woven"
            & $java -Xverify:all -cp "$jar;$runtime" NativeTreeRotation058Probe weave "$GameDirectory/projectzomboid.jar" '-' $fixture fixed
            if($LASTEXITCODE -ne 0){throw "Tree111 native weave failed: $arm"}
            foreach($name in @('Shaders','GlProgram')){
                New-Item -ItemType Directory -Force -Path "$fixture/viewpoint/platform" | Out-Null
                Copy-Item -LiteralPath "$overrides/viewpoint/platform/$name.class" -Destination "$fixture/viewpoint/platform/$name.class" -Force
            }
            Run-Tree111 $arm $jar 'render-default' (Join-Path $Output "$arm-default-render") $fixture
        }
        $parity=& $java -Xverify:all -cp "$Artifact;$runtime" NativeTree111Probe compare (Join-Path $Output 'baseline110-default-render') (Join-Path $Output 'candidate111-default-render') 2>&1
        $exit=$LASTEXITCODE
        $parity | Tee-Object -FilePath (Join-Path $Output 'default-parity.log') | Write-Host
        if($exit -ne 0){throw 'Tree111 default output parity failed'}
    }
}finally{Pop-Location}
Write-Host ('Tree111 checks passed. CPU-only='+$CpuOnly+'; default output parity='+($RenderParity -and !$CpuOnly)+'. Hidden GPU runners must remain serial; retained files are independent developer fixtures and never ship in the production jar.')
