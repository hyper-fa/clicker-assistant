param(
  [string]$DevEcoHome = $env:DEVECO_STUDIO_HOME
)

$ErrorActionPreference = 'Stop'

$candidates = @(
  $DevEcoHome,
  'D:\Huawei\DevEco Studio',
  'C:\Huawei\DevEco Studio',
  'C:\Program Files\Huawei\DevEco Studio',
  'C:\Program Files\DevEco Studio',
  'D:\DevEco Studio'
) | Where-Object { $_ -and (Test-Path $_) }

if (-not $candidates) {
  Write-Error 'DevEco Studio was not found. Set DEVECO_STUDIO_HOME to the DevEco Studio install directory, then rerun this command.'
}

$homeDir = (Resolve-Path $candidates[0]).Path
$nodeExe = Join-Path $homeDir 'tools\node\node.exe'
$hvigorJs = Join-Path $homeDir 'tools\hvigor\bin\hvigorw.js'
$jbrBin = Join-Path $homeDir 'jbr\bin'
$sdkDir = Join-Path $homeDir 'sdk'
$ohosSdkDir = Join-Path $sdkDir 'default\openharmony'

if (-not (Test-Path $nodeExe)) {
  Write-Error "DevEco Node was not found: $nodeExe"
}
if (-not (Test-Path $hvigorJs)) {
  Write-Error "DevEco Hvigor was not found: $hvigorJs"
}

$env:DEVECO_SDK_HOME = $sdkDir
$env:OHOS_BASE_SDK_HOME = $ohosSdkDir
$env:NODE_HOME = Split-Path $nodeExe -Parent
if (Test-Path $jbrBin) {
  $env:JAVA_HOME = Split-Path $jbrBin -Parent
  $env:Path = "$jbrBin;$env:Path"
}

& $nodeExe $hvigorJs @args
exit $LASTEXITCODE
