$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.tools'
$jdkDirectory = Get-ChildItem "$toolRoot/jdk" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if (!$jdkDirectory) { throw 'Run Setup-Android.ps1 first to prepare the project toolchain.' }
$env:JAVA_HOME = $jdkDirectory.FullName
$env:ANDROID_HOME = "$toolRoot/android-sdk"
$env:ANDROID_USER_HOME = "$toolRoot/android-user-root/.android"
$env:ANDROID_SDK_HOME = "$toolRoot/android-user-root"
$env:ANDROID_AVD_HOME = "$toolRoot/android-avd"
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME, $env:ANDROID_AVD_HOME | Out-Null
$avdName = 'SEC_Rename_API_35'
$emulatorSerial = 'emulator-5580'
$adb = "$env:ANDROID_HOME/platform-tools/adb.exe"
$emulator = "$env:ANDROID_HOME/emulator/emulator.exe"
