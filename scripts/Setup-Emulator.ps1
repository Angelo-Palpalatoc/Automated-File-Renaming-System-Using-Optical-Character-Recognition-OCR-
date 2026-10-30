. "$PSScriptRoot/Emulator-Environment.ps1"
$sdkmanager = "$env:ANDROID_HOME/cmdline-tools/12.0/bin/sdkmanager.bat"
$avdmanager = "$env:ANDROID_HOME/cmdline-tools/12.0/bin/avdmanager.bat"
$systemImage = 'system-images;android-35;google_apis_playstore;x86_64'
Write-Output 'Installing the Android emulator and Android 15 Google Play x86_64 system image...'
# Reuse already accepted SDK licenses; do not silently accept new license terms.
'n' | & $sdkmanager "--sdk_root=$env:ANDROID_HOME" 'emulator' $systemImage
if ($LASTEXITCODE -ne 0) { throw 'SDK installation did not complete. Review the output, including any new license request.' }
if (!(Test-Path -LiteralPath "$env:ANDROID_AVD_HOME/$avdName.ini")) {
    'no' | & $avdmanager create avd --name $avdName --package $systemImage --device 'pixel_7' --path "$env:ANDROID_AVD_HOME/$avdName.avd"
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the virtual device.' }
}
$configPath = "$env:ANDROID_AVD_HOME/$avdName.avd/config.ini"
$config = [System.IO.File]::ReadAllText($configPath)
$settings = [ordered]@{
    'avd.ini.displayname' = 'SEC Rename - Android 15 (Google Play)'
    'hw.ramSize' = '3072'
    'hw.cpu.ncore' = '4'
    'hw.keyboard' = 'yes'
    'PlayStore.enabled' = 'yes'
    'disk.dataPartition.size' = '4G'
}
foreach ($setting in $settings.GetEnumerator()) {
    $pattern = '(?m)^' + [regex]::Escape($setting.Key) + '=.*$'
    $line = $setting.Key + '=' + $setting.Value
    if ([regex]::IsMatch($config, $pattern)) { $config = [regex]::Replace($config, $pattern, $line) }
    else { $config = $config.TrimEnd() + "`r`n$line`r`n" }
}
[System.IO.File]::WriteAllText($configPath, $config, (New-Object System.Text.UTF8Encoding($false)))
Write-Output 'Virtual device prepared. Checking hardware acceleration...'
& $emulator -accel-check
if ($LASTEXITCODE -ne 0) {
    Write-Warning 'Hardware acceleration is not ready. Enable Windows Hypervisor Platform and restart Windows, then run Run-AndroidDemo.ps1.'
} else { Write-Output 'Ready. Run scripts/Run-AndroidDemo.ps1 to open the app.' }
