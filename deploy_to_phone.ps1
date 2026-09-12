$adb = "C:\Users\Yashwant\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$apk = Join-Path $PSScriptRoot "app-release.apk"

if (-not (Test-Path $apk)) {
    Write-Error "APK not found at $apk"
    exit 1
}

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "   YMedia Player Auto-Deploy to Phone    " -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "Searching for connected Android device..." -ForegroundColor Yellow

$timeout = 60
$elapsed = 0
$deviceFound = $false

while ($elapsed -lt $timeout) {
    $devicesOutput = & $adb devices
    $lines = $devicesOutput -split "`r?`n" | Where-Object { $_ -match '\S' -and $_ -notmatch 'List of devices' }
    
    foreach ($line in $lines) {
        if ($line -match '(\S+)\s+device$') {
            $deviceId = $matches[1]
            Write-Host "Found authorized device: $deviceId" -ForegroundColor Green
            $deviceFound = $true
            break
        } elseif ($line -match '(\S+)\s+unauthorized$') {
            $deviceId = $matches[1]
            Write-Host "Device $deviceId detected, but UNAUTHORIZED." -ForegroundColor Yellow
            Write-Host "--> Please unlock your phone and tap 'Allow USB debugging' on the screen!" -ForegroundColor Magenta
        }
    }

    if ($deviceFound) { break }

    Start-Sleep -Seconds 2
    $elapsed += 2
    Write-Host "Waiting for device... ($elapsed/${timeout}s) - Make sure USB Debugging is ON in Settings > Developer options"
}

if (-not $deviceFound) {
    Write-Host ""
    Write-Host "No authorized device connected after $timeout seconds." -ForegroundColor Red
    Write-Host "Steps to enable on Samsung Galaxy:" -ForegroundColor Yellow
    Write-Host "1. Open Settings -> About phone -> Software information"
    Write-Host "2. Tap 'Build number' 7 times to enable Developer options"
    Write-Host "3. Go back to Settings -> Developer options -> Enable 'USB debugging'"
    Write-Host "4. Unlock your phone and tap 'Allow' when the prompt appears"
    exit 1
}

Write-Host "`nInstalling $apk on device ($deviceId)..." -ForegroundColor Cyan
$installResult = & $adb -s $deviceId install -r -d $apk
Write-Host $installResult

if ($installResult -match "Success") {
    Write-Host "`nLaunching YMedia Player..." -ForegroundColor Green
    & $adb -s $deviceId shell am start -n com.example.ymediaplayer/.MainActivity
    Write-Host "App launched successfully!" -ForegroundColor Green
} else {
    Write-Host "Installation failed. Retrying with uninstall first..." -ForegroundColor Yellow
    & $adb -s $deviceId uninstall com.example.ymediaplayer
    & $adb -s $deviceId install -r $apk
    & $adb -s $deviceId shell am start -n com.example.ymediaplayer/.MainActivity
}
