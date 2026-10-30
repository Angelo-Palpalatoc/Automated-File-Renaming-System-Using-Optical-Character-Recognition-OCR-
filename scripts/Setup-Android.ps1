param([switch]$AcceptAndroidLicenses)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.tools'
New-Item -ItemType Directory -Force -Path $toolRoot | Out-Null
function Fetch([string]$url, [string]$path) {
    if (!(Test-Path -LiteralPath $path)) { Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $path -TimeoutSec 300 }
}
function Verify([string]$path, [string]$expected, [string]$algorithm = 'SHA256') {
    if ((Get-FileHash -LiteralPath $path -Algorithm $algorithm).Hash -ine $expected.Trim()) { throw "Checksum mismatch: $path" }
}
Write-Output 'Preparing project-local JDK 17 and Gradle 8.11.1...'
$jdkZip = Join-Path $toolRoot 'jdk17.zip'
Fetch 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' $jdkZip
$jdkMetadata = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
Verify $jdkZip $jdkMetadata[0].binary.package.checksum
if (!(Test-Path "$toolRoot/jdk")) { Expand-Archive -LiteralPath $jdkZip -DestinationPath "$toolRoot/jdk" }
$env:JAVA_HOME = (Get-ChildItem "$toolRoot/jdk" -Directory | Select-Object -First 1).FullName
$env:GRADLE_USER_HOME = "$toolRoot/gradle-home"
$gradleZip = "$toolRoot/gradle-8.11.1-bin.zip"
Fetch 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' $gradleZip
$gradleChecksumFile = "$toolRoot/gradle-8.11.1-bin.zip.sha256"
Fetch 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256' $gradleChecksumFile
$gradleHash = Get-Content -LiteralPath $gradleChecksumFile -Raw
Verify $gradleZip $gradleHash
if (!(Test-Path "$toolRoot/gradle-8.11.1")) { Expand-Archive -LiteralPath $gradleZip -DestinationPath $toolRoot }
Write-Output 'Downloading Android command-line tools using the official SDK repository manifest...'
[xml]$sdkRepository = (Invoke-WebRequest -UseBasicParsing 'https://dl.google.com/android/repository/repository2-1.xml').Content
$package = $sdkRepository.SelectNodes("//*[local-name()='remotePackage']") | Where-Object { $_.path -eq 'cmdline-tools;12.0' } | Select-Object -First 1
$archive = $package.archives.archive | Where-Object { $_.'host-os' -eq 'windows' } | Select-Object -First 1
if (!$archive) { throw 'Windows SDK archive not found in official manifest.' }
$sdkZip = "$toolRoot/android-commandlinetools-12.zip"
Fetch ("https://dl.google.com/android/repository/" + $archive.complete.url) $sdkZip
$sdkChecksum = $archive.complete.SelectSingleNode("*[local-name()='checksum']")
$sdkAlgorithm = $sdkChecksum.GetAttribute('type')
if ([string]::IsNullOrWhiteSpace($sdkAlgorithm)) { $sdkAlgorithm = 'SHA1' }
Verify $sdkZip $sdkChecksum.InnerText $sdkAlgorithm
$env:ANDROID_HOME = "$toolRoot/android-sdk"
$env:ANDROID_USER_HOME = "$toolRoot/android-user"
if (!(Test-Path "$env:ANDROID_HOME/cmdline-tools/12.0/bin/sdkmanager.bat")) {
    Expand-Archive -LiteralPath $sdkZip -DestinationPath "$toolRoot/sdk-unpack-12" -Force
    New-Item -ItemType Directory -Force -Path "$env:ANDROID_HOME/cmdline-tools" | Out-Null
    Copy-Item -LiteralPath "$toolRoot/sdk-unpack-12/cmdline-tools" -Destination "$env:ANDROID_HOME/cmdline-tools/12.0" -Recurse
}
$sdkmanager = "$env:ANDROID_HOME/cmdline-tools/12.0/bin/sdkmanager.bat"
if ($AcceptAndroidLicenses) {
    # Invocation requires explicit agreement to the license-acceptance switch.
    (1..80 | ForEach-Object { 'y' }) | & $sdkmanager "--sdk_root=$env:ANDROID_HOME" --licenses
    if ($LASTEXITCODE -ne 0) { throw 'Android license setup failed.' }
} else {
    throw 'Read the Android SDK licenses, then rerun with -AcceptAndroidLicenses if you agree.'
}
& $sdkmanager "--sdk_root=$env:ANDROID_HOME" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
if ($LASTEXITCODE -ne 0) { throw 'Android SDK installation failed.' }
Write-Output 'Generating a Gradle wrapper...'
Push-Location $projectRoot
try {
    & "$toolRoot/gradle-8.11.1/bin/gradle.bat" wrapper --gradle-version 8.11.1 --gradle-distribution-sha256-sum $gradleHash.Trim() --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Gradle wrapper generation failed.' }
} finally { Pop-Location }
Write-Output 'Toolchain ready. Use scripts/Build-Android.ps1 to build the APK.'
