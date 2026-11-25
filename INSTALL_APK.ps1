# Safe, diagnosable Android APK installer for Abo Uobidah TV.
# Never uninstalls an existing app automatically and never disables the stock launcher.
[CmdletBinding()]
param(
    [string]$ApkPath = (Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'),
    [string]$AdbPath = '',
    [string]$SdkRoot = '',
    [string]$Serial = '',
    [switch]$SetDefaultHome
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Set-Location -LiteralPath $PSScriptRoot
$LogPath = Join-Path $PSScriptRoot 'INSTALL_LOG.txt'
Start-Transcript -Path $LogPath -Append | Out-Null
$PackageName = 'com.uobidah.launcher'

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

function Stop-Safe([string]$Message) {
    Write-Host "`n[STOP - SAFE] $Message" -ForegroundColor Red
    throw $Message
}
function Get-SignerDigest([string]$SignerTool, [string]$FilePath) {
    $result = Invoke-NativeCapture { & $SignerTool verify --verbose --print-certs $FilePath 2>&1 }
    $exitCode = $result.ExitCode
    $text = $result.Output -join "`n"
    if ($exitCode -ne 0) { Stop-Safe "Could not verify APK signature: $text" }
    $match = [regex]::Match($text, '(?im)certificate SHA-256 digest:\s*([0-9a-f]{64})')
    if (-not $match.Success) { Stop-Safe "Signature is present but its SHA-256 certificate fingerprint could not be read. Output: $text" }
    return $match.Groups[1].Value.ToLowerInvariant()
}
function Get-VersionCode([string]$DumpText) {
    $m = [regex]::Match($DumpText, '(?im)versionCode=(\d+)')
    if ($m.Success) { return [int]$m.Groups[1].Value }
    return -1
}

try {
    # Read the same release identity consumed by Gradle; do not maintain duplicate version constants.
    $VersionPropertiesPath = Join-Path $PSScriptRoot 'gradle.properties'
    if (-not (Test-Path -LiteralPath $VersionPropertiesPath)) { Stop-Safe 'gradle.properties is missing; release identity cannot be verified.' }
    $VersionPropertiesText = Get-Content -LiteralPath $VersionPropertiesPath -Raw
    $VersionNameMatch = [regex]::Match($VersionPropertiesText, '(?m)^\s*appVersionName\s*=\s*([^\r\n#]+?)\s*$')
    $VersionCodeMatch = [regex]::Match($VersionPropertiesText, '(?m)^\s*appVersionCode\s*=\s*(\d+)\s*$')
    if (-not $VersionNameMatch.Success -or -not $VersionCodeMatch.Success) { Stop-Safe 'gradle.properties must define appVersionName and appVersionCode.' }
    $ExpectedVersionName = $VersionNameMatch.Groups[1].Value.Trim()
    $ExpectedVersionCode = [int]$VersionCodeMatch.Groups[1].Value

    Write-Host '=====================================================' -ForegroundColor Cyan
    Write-Host 'ABO UOBIDAH TV - SAFE INSTALL / UPDATE' -ForegroundColor Cyan
    Write-Host '=====================================================' -ForegroundColor Cyan

    if (-not (Test-Path -LiteralPath $ApkPath)) { Stop-Safe "APK file not found: $ApkPath. Build the project first." }
    $ApkPath = (Resolve-Path -LiteralPath $ApkPath).Path

    if (-not $AdbPath) {
        $adbCmd = Get-Command adb.exe -ErrorAction SilentlyContinue
        if ($adbCmd) { $AdbPath = $adbCmd.Source }
        if (-not $AdbPath -and $env:ANDROID_SDK_ROOT) { $AdbPath = Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe' }
        if (-not $AdbPath -and $env:ANDROID_HOME) { $AdbPath = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe' }
        if (-not $AdbPath) { $AdbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe' }
    }
    if (-not (Test-Path -LiteralPath $AdbPath)) { Stop-Safe 'adb.exe not found. Install Android SDK Platform-Tools or set ANDROID_SDK_ROOT.' }
    $AdbPath = (Resolve-Path -LiteralPath $AdbPath).Path

    $sdkRoot = $SdkRoot
    if (-not $sdkRoot) { $sdkRoot = $env:ANDROID_SDK_ROOT }
    if (-not $sdkRoot) { $sdkRoot = $env:ANDROID_HOME }
    if (-not $sdkRoot) { $sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    $buildTools = Join-Path $sdkRoot 'build-tools\34.0.0'
    $signer = Join-Path $buildTools 'apksigner.bat'
    $aapt = Join-Path $buildTools 'aapt.exe'
    if (-not (Test-Path -LiteralPath $signer)) { Stop-Safe "apksigner.bat not found: $signer. Run the build bootstrap first." }
    if (-not (Test-Path -LiteralPath $aapt)) { Stop-Safe "aapt.exe not found: $aapt. The APK package/version cannot be validated safely." }

    Write-Host '[1/6] Validating APK package, version and signature...' -ForegroundColor Cyan
    $badgingResult = Invoke-NativeCapture { & $aapt dump badging $ApkPath 2>&1 }
    $badgingLines = @($badgingResult.Output)
    if ($badgingResult.ExitCode -ne 0) { Stop-Safe "aapt could not read the APK: $(($badgingLines -join ' '))" }
    $badging = $badgingLines -join "`n"
    $pkgMatch = [regex]::Match($badging, "package:\s+name='([^']+)'\s+versionCode='(\d+)'\s+versionName='([^']*)'")
    if (-not $pkgMatch.Success) { Stop-Safe "APK metadata is incomplete: $badging" }
    $apkPackage = $pkgMatch.Groups[1].Value
    $apkCode = [int]$pkgMatch.Groups[2].Value
    $apkVersion = $pkgMatch.Groups[3].Value
    if ($apkPackage -ne $PackageName) { Stop-Safe "Wrong application ID. Expected $PackageName but APK contains $apkPackage." }
    if ($apkVersion -ne $ExpectedVersionName -or $apkCode -ne $ExpectedVersionCode) {
        Stop-Safe "Unexpected APK version. Expected $ExpectedVersionName (code $ExpectedVersionCode), found $apkVersion (code $apkCode). Rebuild from this release folder."
    }
    $newDigest = Get-SignerDigest -SignerTool $signer -FilePath $ApkPath
    Write-Host "APK: $apkVersion ($apkCode), package=$apkPackage" -ForegroundColor Green
    Write-Host "Signing certificate SHA-256: $newDigest"

    Write-Host '[2/6] Starting ADB and checking device authorization...' -ForegroundColor Cyan
    $adbStart = Invoke-NativeCapture { & $AdbPath start-server 2>&1 }
    if ($adbStart.ExitCode -ne 0) { Write-Host "[NOTICE] ADB server start: $($adbStart.Output -join ' ')" -ForegroundColor Yellow }
    $deviceResult = Invoke-NativeCapture { & $AdbPath devices 2>&1 }
    $deviceLines = @($deviceResult.Output)
    $ready = @($deviceLines | Where-Object { $_ -match '^\s*\S+\s+device\s*$' })
    $unauthorized = @($deviceLines | Where-Object { $_ -match '^\s*\S+\s+unauthorized\s*$' })
    $offline = @($deviceLines | Where-Object { $_ -match '^\s*\S+\s+offline\s*$' })

    if (-not $Serial -and $ready.Count -eq 0 -and [Environment]::UserInteractive) {
        Write-Host 'No authorized device found.' -ForegroundColor Yellow
        if ($unauthorized.Count -gt 0) { Write-Host 'A device is connected but UNAUTHORIZED. Accept the ADB/RSA prompt on the TV, then retry.' -ForegroundColor Yellow }
        if ($offline.Count -gt 0) { Write-Host 'A device is OFFLINE. Reconnect ADB or restart debugging on the TV.' -ForegroundColor Yellow }
        $tvAddress = Read-Host 'Enter the TV IP:PORT for wireless ADB (or press Enter to cancel)'
        if ($tvAddress -match '^[0-9a-zA-Z.\-:\[\]]+$') {
            $connectResult = Invoke-NativeCapture { & $AdbPath connect $tvAddress 2>&1 }
            $connectLines = @($connectResult.Output)
            $connectLines | ForEach-Object { Write-Host $_ }
            Start-Sleep -Seconds 3
            $deviceResult = Invoke-NativeCapture { & $AdbPath devices 2>&1 }
            $deviceLines = @($deviceResult.Output)
            $ready = @($deviceLines | Where-Object { $_ -match '^\s*\S+\s+device\s*$' })
        }
    }
    if (-not $Serial) {
        if ($ready.Count -eq 0) { Stop-Safe 'No authorized Android device was found. Enable Developer options > USB debugging / Wireless debugging and accept the RSA prompt.' }
        if ($ready.Count -eq 1) {
            $Serial = (($ready[0] -split '\s+')[0])
        } else {
            Write-Host 'Multiple authorized devices were found. Choose the target explicitly:' -ForegroundColor Yellow
            for ($i = 0; $i -lt $ready.Count; $i++) { Write-Host "[$($i + 1)] $(($ready[$i] -split '\s+')[0])" }
            if (-not [Environment]::UserInteractive) { Stop-Safe 'Multiple devices connected. Re-run with -Serial <device-id>.' }
            $choice = Read-Host 'Device number'
            $number = 0
            if (-not [int]::TryParse($choice, [ref]$number) -or $number -lt 1 -or $number -gt $ready.Count) { Stop-Safe 'Invalid device selection. No installation was attempted.' }
            $Serial = (($ready[$number - 1] -split '\s+')[0])
        }
    }
    $stateResult = Invoke-NativeCapture { & $AdbPath -s $Serial get-state 2>&1 }
    $state = $stateResult.Output -join ' '
    if ($stateResult.ExitCode -ne 0 -or $state.Trim() -ne 'device') { Stop-Safe "Selected device '$Serial' is not authorized/online. ADB state: $state" }
    Write-Host "Target device: $Serial" -ForegroundColor Green

    Write-Host '[3/6] Checking Android version and installed package...' -ForegroundColor Cyan
    $sdkResult = Invoke-NativeCapture { & $AdbPath -s $Serial shell getprop ro.build.version.sdk 2>&1 }
    $sdkText = $sdkResult.Output -join ''
    if ($sdkResult.ExitCode -ne 0) { Stop-Safe "Could not read Android API level: $sdkText" }
    $deviceSdk = 0
    [void][int]::TryParse($sdkText.Trim(), [ref]$deviceSdk)
    if ($deviceSdk -lt 26) { Stop-Safe "This app requires Android API 26 or newer. Target device reports API $sdkText." }
    Write-Host "Android API: $deviceSdk"

    $installedPathsResult = Invoke-NativeCapture { & $AdbPath -s $Serial shell pm path $PackageName 2>&1 }
    $installedPathLines = @($installedPathsResult.Output)
    $installedPaths = @($installedPathLines | Where-Object { $_ -match '^package:' } | ForEach-Object { ($_ -replace '^package:', '').Trim() })
    if ($installedPaths.Count -gt 0) {
        $dumpResult = Invoke-NativeCapture { & $AdbPath -s $Serial shell dumpsys package $PackageName 2>&1 }
        $dumpLines = @($dumpResult.Output)
        $dumpText = $dumpLines -join "`n"
        $installedCode = Get-VersionCode $dumpText
        Write-Host "Existing installation detected. versionCode=$installedCode"
        if ($installedCode -gt $apkCode) {
            Stop-Safe "The TV already has versionCode $installedCode, newer than this APK ($apkCode). Android would reject a downgrade. Build a higher versionCode; the installer will not use -d."
        }

        $basePath = $installedPaths | Where-Object { $_ -match '/base\.apk$' } | Select-Object -First 1
        if (-not $basePath) { $basePath = $installedPaths[0] }
        $pulledApk = Join-Path $env:TEMP ("abo-uobidah-installed-" + [guid]::NewGuid().ToString('N') + '.apk')
        try {
            $pullResult = Invoke-NativeCapture { & $AdbPath -s $Serial pull $basePath $pulledApk 2>&1 }
            $pullLines = @($pullResult.Output)
            if ($pullResult.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $pulledApk)) {
                Stop-Safe "Could not read the installed APK to compare signing certificates. No update was attempted. ADB output: $(($pullLines -join ' '))"
            }
            $oldDigest = Get-SignerDigest -SignerTool $signer -FilePath $pulledApk
            if ($oldDigest -ne $newDigest) {
                Stop-Safe "SIGNATURE CONFLICT: the installed app and this APK have different signing certificates. Android will reject an in-place update. Existing SHA-256=$oldDigest; new APK SHA-256=$newDigest. Do NOT uninstall yet. Open the installed app > Settings > Backup & Restore > Create Backup, copy the JSON backup to a safe location, and obtain/build an APK signed with the original key. Uninstalling removes private app data; this script will never do that automatically."
            }
            Write-Host '[OK] Signing certificate matches the installed app. In-place update can preserve app data.' -ForegroundColor Green
        } finally {
            if (Test-Path -LiteralPath $pulledApk) { Remove-Item -LiteralPath $pulledApk -Force -ErrorAction SilentlyContinue }
        }
    } else {
        Write-Host '[INFO] Fresh install: this package is not currently installed.' -ForegroundColor Yellow
    }

    Write-Host '[4/6] Installing without removing app data...' -ForegroundColor Cyan
    $installResult = Invoke-NativeCapture { & $AdbPath -s $Serial install -r $ApkPath 2>&1 }
    $installOutput = @($installResult.Output)
    $installExit = $installResult.ExitCode
    $installText = $installOutput -join "`n"
    $installOutput | ForEach-Object { Write-Host $_ }
    if ($installExit -ne 0 -or $installText -notmatch '(?m)^Success\s*$') {
        if ($installText -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match') {
            Stop-Safe "Android reported a signature conflict despite the preliminary check. No uninstall was attempted. Output: $installText"
        } elseif ($installText -match 'INSTALL_FAILED_VERSION_DOWNGRADE') {
            Stop-Safe "Android rejected this APK as an older version. Increase versionCode above the TV's installed value. Output: $installText"
        } elseif ($installText -match 'INSTALL_FAILED_INSUFFICIENT_STORAGE') {
            Stop-Safe "The TV does not have enough free storage. Free space, restart the TV, then retry. Output: $installText"
        } elseif ($installText -match 'INSTALL_FAILED_USER_RESTRICTED|INSTALL_CANCELED_BY_USER') {
            Stop-Safe "The TV blocked installation. Enable permission to install apps from this source / approve the TV confirmation dialog. Output: $installText"
        } elseif ($installText -match 'INSTALL_FAILED_INVALID_APK|NO_CERTIFICATES|PARSE_FAILED') {
            Stop-Safe "Android could not parse or validate the APK. Rebuild and verify the APK; do not rename an arbitrary file to .apk. Output: $installText"
        } elseif ($installText -match 'INSTALL_FAILED_OLDER_SDK') {
            Stop-Safe "The TV Android version is older than this app's minimum API 26. Update the TV firmware or use a compatible Android device. Output: $installText"
        } elseif ($installText -match 'INSTALL_FAILED_NO_MATCHING_ABIS|INSTALL_FAILED_CPU_ABI_INCOMPATIBLE') {
            $abisResult = Invoke-NativeCapture { & $AdbPath -s $Serial shell getprop ro.product.cpu.abilist 2>&1 }
            $abis = $abisResult.Output -join ' '
            Stop-Safe "APK native libraries do not match the TV architecture. Device ABIs: $abis. Output: $installText"
        } elseif ($installText -match 'INSTALL_FAILED_SHARED_USER_INCOMPATIBLE|INSTALL_FAILED_DUPLICATE_PERMISSION|INSTALL_FAILED_CONFLICTING_PROVIDER') {
            Stop-Safe "Android detected a package/permission/provider conflict with another installed app. Check the full Package Manager output before changing or removing any application. Output: $installText"
        } elseif ($installText -match 'INSTALL_FAILED_VERIFICATION_FAILURE|INSTALL_FAILED_VERIFICATION_TIMEOUT') {
            Stop-Safe "Android's package verifier blocked the APK. Review Play Protect/device security confirmation and verify the source APK. Output: $installText"
        } else {
            Stop-Safe "ADB installation failed (exit $installExit). Output: $installText. Existing app was not uninstalled."
        }
    }

    Write-Host '[5/6] Verifying installed package...' -ForegroundColor Cyan
    $verifyDumpResult = Invoke-NativeCapture { & $AdbPath -s $Serial shell dumpsys package $PackageName 2>&1 }
    $verifyDump = $verifyDumpResult.Output -join "`n"
    $verifiedCode = Get-VersionCode $verifyDump
    $verifyPathsResult = Invoke-NativeCapture { & $AdbPath -s $Serial shell pm path $PackageName 2>&1 }
    $verifyPaths = @($verifyPathsResult.Output | Where-Object { [string]$_ -match '^package:' })
    if ($verifiedCode -ne $ExpectedVersionCode -or $verifyPaths.Count -eq 0) {
        Stop-Safe "Installation command returned success, but post-install verification failed. versionCode=$verifiedCode; pm path count=$($verifyPaths.Count)."
    }
    Write-Host "[OK] Abo Uobidah TV $ExpectedVersionName installed successfully on $Serial." -ForegroundColor Green
    $launchResult = Invoke-NativeCapture { & $AdbPath -s $Serial shell am start -n "$PackageName/.MainLauncherActivity" 2>&1 }
    foreach ($line in $launchResult.Output) { Write-Host $line }
    if ($launchResult.ExitCode -ne 0) { Write-Host '[NOTICE] Package installed and verified, but Android could not auto-launch its activity.' -ForegroundColor Yellow }

    Write-Host '[6/6] Optional default HOME setup' -ForegroundColor Cyan
    $applyHome = $SetDefaultHome.IsPresent
    if (-not $SetDefaultHome -and [Environment]::UserInteractive) {
        $answer = Read-Host 'Set Abo Uobidah TV as the default HOME launcher now? (Y/N; the stock launcher will remain installed)'
        $applyHome = ($answer -match '(?i)^(y|yes)$')
    }
    if ($applyHome) {
        $homeScript = Join-Path $PSScriptRoot 'FORCE_HOME_ADB.ps1'
        if (-not (Test-Path -LiteralPath $homeScript)) { Stop-Safe 'FORCE_HOME_ADB.ps1 is missing.' }
        & powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File $homeScript -Adb $AdbPath -Serial $Serial
        if ($LASTEXITCODE -ne 0) { Write-Host '[WARNING] App installed, but the HOME role/permissions were not all applied. Configure the Default Home app in TV Settings.' -ForegroundColor Yellow }
    } else {
        Write-Host '[INFO] Default HOME was not changed. Choose Abo Uobidah TV in Android TV Home-app settings whenever you are ready.' -ForegroundColor Yellow
    }

    Write-Host "`n[COMPLETE] Installation and package verification finished." -ForegroundColor Green
    exit 0
}
catch {
    Write-Host "`nINSTALLATION STOPPED: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Diagnostic log: $LogPath" -ForegroundColor Yellow
    exit 1
}
finally {
    try { Stop-Transcript | Out-Null } catch {}
}
