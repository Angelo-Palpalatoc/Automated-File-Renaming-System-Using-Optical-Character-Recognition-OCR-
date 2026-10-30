$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.tools'
$jdkDirectory = Get-ChildItem "$toolRoot/jdk" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if (!$jdkDirectory) { throw 'Run scripts/Setup-Android.ps1 first, or build with a configured Android Studio installation.' }
$env:JAVA_HOME = $jdkDirectory.FullName
$env:ANDROID_HOME = "$toolRoot/android-sdk"
$env:ANDROID_USER_HOME = "$toolRoot/android-user-root/.android"
$env:ANDROID_SDK_HOME = "$toolRoot/android-user-root"
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null
$env:GRADLE_USER_HOME = "$toolRoot/gradle-home"
if (!(Test-Path "$toolRoot/demo-debug.keystore")) {
    & "$env:JAVA_HOME/bin/keytool.exe" -genkeypair -keystore "$toolRoot/demo-debug.keystore" -storepass android -keypass android -alias androiddebugkey -dname 'CN=Android Debug,O=Android,C=US' -validity 10000 -keyalg RSA -keysize 2048
    if ($LASTEXITCODE -ne 0) { throw 'Could not generate the local demo signing key.' }
}
Push-Location $projectRoot
try {
    & "$toolRoot/gradle-8.11.1/bin/gradle.bat" :shared:desktopTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Android build or checks failed. See the Gradle output.' }
    Write-Output 'APK: app/build/outputs/apk/debug/app-debug.apk'
} finally { Pop-Location }
