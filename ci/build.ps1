param([string[]]$Tasks=@(':app:testDebugUnitTest',':app:assembleRelease'),[string]$GodotExecutable=(Join-Path (Split-Path $PSScriptRoot -Parent) '.local/godot-editor/Godot_v4.7.2-stable_win64_console.exe'),[string]$EvidenceStage='E15_GODOT_HOME')
$ErrorActionPreference='Stop'
$taskRepo=Split-Path $PSScriptRoot -Parent
if($EvidenceStage -notmatch '^[A-Z0-9_-]+$'){throw 'Invalid evidence stage.'}
if(!(Test-Path -LiteralPath $GodotExecutable)){throw 'Provide -GodotExecutable for the Godot 4.7.2 console executable.'}
$godotVersion=& $GodotExecutable --version
if($LASTEXITCODE -ne 0 -or $godotVersion -ne '4.7.2.stable.official.ed1daf0bf'){throw 'Godot 4.7.2 official editor is required for the embedded scene.'}
$env:GODOT_EXECUTABLE=(Resolve-Path -LiteralPath $GodotExecutable).Path
$env:JAVA_HOME='C:/Users/15942/dev/jdk-21'
$env:ANDROID_HOME='C:/Users/15942/dev/sdk/android-sdk'
$env:GRADLE_USER_HOME=Join-Path $taskRepo '.local/.gradle-m0'
$env:PATH="$env:JAVA_HOME/bin;$env:ANDROID_HOME/platform-tools;$env:PATH"
Push-Location $taskRepo
try {
  & ./gradlew.bat @Tasks "-PevidenceStage=$EvidenceStage" --no-daemon --console=plain --stacktrace
  if($LASTEXITCODE -ne 0) {throw "Gradle failed ($LASTEXITCODE)"}
} finally {Pop-Location}
