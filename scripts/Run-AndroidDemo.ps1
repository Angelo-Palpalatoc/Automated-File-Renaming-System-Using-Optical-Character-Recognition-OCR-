param([switch]$Build)
. "$PSScriptRoot/Emulator-Environment.ps1"
if ($Build) { & "$PSScriptRoot/Build-Android.ps1" }
$apk = "$projectRoot/app/build/outputs/apk/debug/app-debug.apk"
if (!(Test-Path -LiteralPath $apk)) { throw 'APK is missing. Run with -Build first.' }
if (!(Test-Path -LiteralPath $emulator) -or !(Test-Path -LiteralPath "$env:ANDROID_AVD_HOME/$avdName.ini")) {
    throw 'Run scripts/Setup-Emulator.ps1 first.'
}
& $adb start-server
if ($LASTEXITCODE -ne 0) { throw 'ADB could not start.' }
$connected = & $adb devices
if (!($connected | Where-Object { $_ -match ('^' + [regex]::Escape($emulatorSerial) + '\s') })) {
    & $emulator -accel-check
    if ($LASTEXITCODE -ne 0) { throw 'Enable Windows Hypervisor Platform and restart Windows before launching the emulator.' }
    # The emulator is intentionally visible so the user can interact with the app.
    $process = Start-Process -FilePath $emulator -ArgumentList @('-avd', $avdName, '-port', '5580', '-gpu', 'auto', '-no-boot-anim') -WindowStyle Normal -PassThru -RedirectStandardOutput "$toolRoot/emulator-stdout.log" -RedirectStandardError "$toolRoot/emulator-stderr.log"
    Write-Output "Starting $avdName (process $($process.Id))..."
}
$deadline = (Get-Date).AddMinutes(4)
$booted = $false
while ((Get-Date) -lt $deadline) {
    # ADB normally reports "device not found/offline" during early startup.
    # Windows PowerShell can treat redirected native stderr as an exception.
    $savedPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $bootStatus = & $adb -s $emulatorSerial shell getprop sys.boot_completed 2>$null
        $bootExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $savedPreference }
    if ($bootExit -eq 0 -and ($bootStatus -join '').Trim() -eq '1') { $booted = $true; break }
    Write-Output 'Waiting for Android to finish starting...'
    Start-Sleep -Seconds 5
}
if (!$booted) { throw 'Startup is not confirmed after four minutes. Inspect the emulator window and .tools/emulator-stderr.log; no automatic relaunch was attempted.' }
& $adb -s $emulatorSerial install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed; existing app data was not cleared.' }
& $adb -s $emulatorSerial shell mkdir -p /sdcard/Download/SEC_Demo_Source /sdcard/Download/SEC_Demo_Output
if ($LASTEXITCODE -ne 0) { throw 'Could not prepare demo folders.' }
# Copy only project demo documents, never arbitrary bank files from the PC.
$samples = Get-ChildItem "$projectRoot/demo-documents" -File -Recurse | Where-Object { $_.Extension.ToLowerInvariant() -in @('.pdf', '.jpg', '.jpeg', '.png') -and $_.Name -ne 'contact_sheet.png' }
foreach ($sample in $samples) {
    & $adb -s $emulatorSerial push $sample.FullName ("/sdcard/Download/SEC_Demo_Source/" + $sample.Name)
    if ($LASTEXITCODE -ne 0) { throw "Could not transfer demo document: $($sample.Name)" }
}
& $adb -s $emulatorSerial shell am start -W -n com.local.secrename/.MainActivity
if ($LASTEXITCODE -ne 0) { throw 'Android could not start the app.' }
Write-Output 'SEC Rename is open. Choose Download/SEC_Demo_Source, then Download/SEC_Demo_Output in the Android folder picker.'
Write-Output 'For later code updates, run this script with -Build. It updates the installed app without uninstalling it.'
