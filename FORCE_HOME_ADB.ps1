# Optional setup after installation: request permissions and choose Abo Uobidah TV as HOME.
# This script never disables or removes the manufacturer's launcher.
[CmdletBinding()]
param([string]$TvIp = '', [string]$Serial = '', [string]$Adb = '')
$ErrorActionPreference = 'Stop'
$pkg = 'com.uobidah.launcher'
$homeComp = "$pkg/.MainLauncherActivity"
$listener = "$pkg/com.uobidah.launcher.IslandNotificationListener"
$log = Join-Path $PSScriptRoot 'HOME_SETUP_LOG.txt'
Start-Transcript -Path $log -Append | Out-Null
$warnings = New-Object System.Collections.Generic.List[string]

function Invoke-NativeCapture([scriptblock]$Command) {
    $savedPreference = $ErrorActionPreference
    $nativeOutput = @(); $nativeExitCode = 0
    $LASTEXITCODE = -1
    try {
        $ErrorActionPreference = 'Continue'
        $nativeOutput = @(. $Command)
        if ($LASTEXITCODE -ge 0) { $nativeExitCode = [int]$LASTEXITCODE }
    } catch { $nativeOutput += $_.ToString(); $nativeExitCode = 1 }
    finally { $ErrorActionPreference = $savedPreference }
    $normalized = @($nativeOutput | ForEach-Object { if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.ToString() } else { [string]$_ } })
    return [pscustomobject]@{ ExitCode = $nativeExitCode; Output = $normalized }
}

function Invoke-DeviceShell([string]$Command, [string]$Label, [switch]$Critical) {
    Write-Host "> adb shell $Command" -ForegroundColor DarkGray
    $result = Invoke-NativeCapture { & $script:Adb -s $script:Serial shell $Command 2>&1 }
    $lines = @($result.Output)
    $code = $result.ExitCode
    foreach ($line in $lines) { if ($line) { Write-Host $line } }
    if ($code -ne 0) {
        $message = "$Label failed (exit $code): $(($lines -join ' ').Trim())"
        if ($Critical) { throw $message }
        $script:warnings.Add($message)
        Write-Host "[WARNING] $message" -ForegroundColor Yellow
        return $false
    }
    return $true
}

try {
    if (-not $Adb) {
        $cmd = Get-Command adb.exe -ErrorAction SilentlyContinue
        if ($cmd) { $Adb = $cmd.Source }
        foreach ($candidate in @(
            "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe",
            "$env:ANDROID_HOME\platform-tools\adb.exe",
            "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
            "$env:USERPROFILE\Android\Sdk\platform-tools\adb.exe"
        )) {
            if (-not $Adb -and (Test-Path -LiteralPath $candidate)) { $Adb = $candidate }
        }
    }
    if (-not $Adb -or -not (Test-Path -LiteralPath $Adb)) { throw 'adb.exe not found. Install Android SDK Platform-Tools.' }
    $Adb = (Resolve-Path -LiteralPath $Adb).Path

    if ($TvIp) {
        $connectResult = Invoke-NativeCapture { & $Adb connect $TvIp 2>&1 }
        $connect = @($connectResult.Output)
        $connect | ForEach-Object { Write-Host $_ }
        Start-Sleep -Seconds 2
    }
    if (-not $Serial) {
        $devicesResult = Invoke-NativeCapture { & $Adb devices 2>&1 }
        $rows = @($devicesResult.Output)
        $ready = @($rows | Where-Object { $_ -match '^\s*\S+\s+device\s*$' })
        if ($ready.Count -eq 0) { throw 'No authorized device. Enable USB/Wireless debugging and accept the ADB/RSA prompt on the TV.' }
        if ($ready.Count -gt 1) {
            Write-Host 'Multiple devices detected:' -ForegroundColor Yellow
            for ($i=0; $i -lt $ready.Count; $i++) { Write-Host "[$($i+1)] $(($ready[$i] -split '\s+')[0])" }
            $choice = Read-Host 'Choose device number'
            $number = 0
            if (-not [int]::TryParse($choice, [ref]$number) -or $number -lt 1 -or $number -gt $ready.Count) { throw 'Invalid device selection.' }
            $Serial = (($ready[$number-1] -split '\s+')[0])
        } else { $Serial = (($ready[0] -split '\s+')[0]) }
    }
    $stateResult = Invoke-NativeCapture { & $Adb -s $Serial get-state 2>&1 }
    $state = $stateResult.Output -join ' '
    if ($stateResult.ExitCode -ne 0 -or $state.Trim() -ne 'device') { throw "Selected device is not online/authorized: $state" }

    $installedResult = Invoke-NativeCapture { & $Adb -s $Serial shell pm path $pkg 2>&1 }
    $installed = @($installedResult.Output | Where-Object { [string]$_ -match '^package:' })
    if ($installed.Count -eq 0) { throw "Package $pkg is not installed. Install the APK before running HOME setup." }
    $apiResult = Invoke-NativeCapture { & $Adb -s $Serial shell getprop ro.build.version.sdk 2>&1 }
    $apiText = $apiResult.Output -join ''
    $api = 0; [void][int]::TryParse($apiText.Trim(), [ref]$api)

    Write-Host "Device: $Serial (Android API $api)" -ForegroundColor Cyan
    Write-Host 'Applying optional permissions; unsupported vendor commands will be reported as warnings.' -ForegroundColor Cyan

    if ($api -ge 33) { [void](Invoke-DeviceShell "pm grant $pkg android.permission.POST_NOTIFICATIONS" 'POST_NOTIFICATIONS') }
    [void](Invoke-DeviceShell "pm grant $pkg android.permission.ACCESS_FINE_LOCATION" 'ACCESS_FINE_LOCATION')
    [void](Invoke-DeviceShell "appops set $pkg SYSTEM_ALERT_WINDOW allow" 'Overlay permission')
    if ($api -ge 29) { [void](Invoke-DeviceShell "cmd notification allow_listener $listener" 'Notification listener') }
    [void](Invoke-DeviceShell "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS" 'WRITE_SECURE_SETTINGS')

    # Preserve all other accessibility services. Add only the app's dedicated Home helper.
    $svc = "$pkg/$pkg.HomeKeyOverrideService"
    $currentResult = Invoke-NativeCapture { & $Adb -s $Serial shell settings get secure enabled_accessibility_services 2>&1 }
    $current = ($currentResult.Output -join '').Trim()
    if ($current -eq 'null' -or [string]::IsNullOrWhiteSpace($current)) { $updated = $svc }
    elseif ($current -notmatch [regex]::Escape($svc)) { $updated = "$current`:$svc" }
    else { $updated = $current }
    [void](Invoke-DeviceShell "settings put secure enabled_accessibility_services $updated" 'Accessibility service list')
    [void](Invoke-DeviceShell 'settings put secure accessibility_enabled 1' 'Accessibility master switch')

    # These app-ops vary by Android/vendor release; their failure does not undo installation.
    [void](Invoke-DeviceShell "dumpsys deviceidle whitelist +$pkg" 'Battery optimization exemption')
    [void](Invoke-DeviceShell "appops set $pkg RUN_IN_BACKGROUND allow" 'Background execution')
    [void](Invoke-DeviceShell "appops set $pkg RUN_ANY_IN_BACKGROUND allow" 'Background execution policy')

    [void](Invoke-DeviceShell "cmd package set-home-activity $homeComp" 'Set HOME activity')
    [void](Invoke-DeviceShell "cmd role add-role-holder --user 0 android.app.role.HOME $pkg" 'Add HOME role holder')
    [void](Invoke-DeviceShell 'am start -a android.intent.action.MAIN -c android.intent.category.HOME' 'Open HOME')
    Start-Sleep -Seconds 2
    $resolvedResult = Invoke-NativeCapture { & $Adb -s $Serial shell 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' 2>&1 }
    $resolved = $resolvedResult.Output -join ' '
    if ($resolved -match [regex]::Escape($pkg)) {
        Write-Host '[OK] Abo Uobidah TV is the default HOME app.' -ForegroundColor Green
    } else {
        Write-Host '[NOTICE] The device still resolves another HOME app. Choose Abo Uobidah TV from Android TV Settings > Apps > Default apps > Home app.' -ForegroundColor Yellow
        Write-Host 'The stock launcher was left installed and enabled.' -ForegroundColor Yellow
        $warnings.Add('Default HOME role did not resolve to this app on the device.')
    }

    if ($warnings.Count -gt 0) {
        Write-Host "`nSetup completed with $($warnings.Count) warning(s). See HOME_SETUP_LOG.txt. The app installation itself remains successful." -ForegroundColor Yellow
        exit 0
    }
    Write-Host '[COMPLETE] HOME setup finished.' -ForegroundColor Green
    exit 0
} catch {
    Write-Host "[FAILED] $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Log: $log" -ForegroundColor Yellow
    exit 1
} finally {
    try { Stop-Transcript | Out-Null } catch {}
}
