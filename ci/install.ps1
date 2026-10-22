param(
  [Parameter(Mandatory=$true)][string]$Serial,
  [Parameter(Mandatory=$true)][string]$SdkRoot
)
$ErrorActionPreference='Stop'
$taskRepo=Split-Path $PSScriptRoot -Parent
$taskApk=Join-Path $taskRepo 'output/local-companion-0.39.0.apk'
$taskAdb=Join-Path $SdkRoot 'platform-tools/adb.exe'
$taskAapt=Join-Path $SdkRoot 'build-tools/36.0.0/aapt2.exe'
foreach($path in @($taskApk,$taskAdb,$taskAapt)) {
  if(!(Test-Path -LiteralPath $path -PathType Leaf)) {throw "Required file missing: $path"}
}
$metadata=& $taskAapt dump badging $taskApk
if($LASTEXITCODE -ne 0) {throw 'Cannot inspect delivery APK'}
$metadataText=$metadata -join "`n"
if($metadataText -notmatch "(?m)^package: name='cn\.localfirst\.companion' ") {throw 'Delivery APK has the wrong application ID'}
if($metadataText -match '(?m)^application-debuggable') {throw 'Debug APK cannot be installed by the delivery entry point'}
function Invoke-Device([string[]]$Arguments) {
  $result=& $taskAdb -s $Serial @Arguments
  if($LASTEXITCODE -ne 0) {throw "ADB failed: $($Arguments[0])"}
  return $result
}
$installed=Invoke-Device -Arguments @('install','-r',$taskApk)
if(($installed -join "`n") -notmatch '(?m)^Success\s*$') {throw 'Release installation was not confirmed'}
$packages=Invoke-Device -Arguments @('shell','pm','list','packages','--user','0','cn.localfirst.companion')
if($packages -notcontains 'package:cn.localfirst.companion') {throw 'Release package is not installed'}
# Remove the duplicate launcher only after the complete release installs successfully.
# Keep its private data so removing a validation build never erases its records.
foreach($obsolete in @('cn.localfirst.companion.debug.test','cn.localfirst.companion.debug')) {
  if($packages -contains "package:$obsolete") {
    $removed=Invoke-Device -Arguments @('shell','pm','uninstall','-k','--user','0',$obsolete)
    if(($removed -join "`n") -notmatch '(?m)^Success\s*$') {throw "Could not remove duplicate package: $obsolete"}
  }
}
$remaining=@(Invoke-Device -Arguments @('shell','pm','list','packages','--user','0','cn.localfirst.companion'))
if($remaining.Count -ne 1 -or $remaining[0] -ne 'package:cn.localfirst.companion') {throw 'The device still contains additional companion packages'}
Invoke-Device -Arguments @('shell','am','start','-n','cn.localfirst.companion/com.ai.assistance.operit.ui.main.MainActivity')
Write-Output 'Installed one companion app. Existing release data and removed validation data are retained.'
