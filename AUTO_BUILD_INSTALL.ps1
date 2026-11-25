# Robust Windows build bootstrap for Abo Uobidah TV
# Compatible with Windows PowerShell 5.1 and PowerShell 7.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Set-Location -LiteralPath $PSScriptRoot
$Log = Join-Path $PSScriptRoot 'AUTO_BUILD_LOG.txt'
Start-Transcript -Path $Log -Append | Out-Null

function Write-Step([string]$Message) { Write-Host "`n========== $Message ==========" -ForegroundColor Cyan }
function Invoke-NativeCapture([scriptblock]$Command) {
    # Windows PowerShell 5.1 converts native stderr into ErrorRecords. With
    # ErrorActionPreference=Stop this can terminate a perfectly valid Gradle,
    # ADB or Android SDK Manager invocation. Downgrade only for this native
    # process call, still capture the real process exit code, then restore Stop.
    $savedPreference = $ErrorActionPreference
    $nativeOutput = @()
    $nativeExitCode = 0
    # Create LASTEXITCODE in this function scope before dot-sourcing the
    # scriptblock, so the process exit status is captured from this exact call.
    $LASTEXITCODE = -1
    try {
        $ErrorActionPreference = 'Continue'
        $nativeOutput = @(. $Command)
        if ($LASTEXITCODE -ge 0) { $nativeExitCode = [int]$LASTEXITCODE }
    } catch {
        $nativeOutput += $_.ToString()
        $nativeExitCode = 1
    } finally {
        $ErrorActionPreference = $savedPreference
    }
    $normalizedOutput = @($nativeOutput | ForEach-Object {
        if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.ToString() }
        else { [string]$_ }
    })
    return [pscustomobject]@{ ExitCode = $nativeExitCode; Output = $normalizedOutput }
}
function Fail([string]$Message) { Write-Host "`n[FAILED] $Message" -ForegroundColor Red; throw $Message }
function Find-Java {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
    $cmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($cmd) { $candidates += $cmd.Source }
    $candidates += @(
      "$env:ProgramFiles\Android\Android Studio\jbr\bin\java.exe",
      "$env:ProgramFiles\Android\Android Studio\jre\bin\java.exe",
      "$env:LOCALAPPDATA\Programs\Android Studio\jbr\bin\java.exe"
    )
    foreach ($p in ($candidates | Select-Object -Unique)) { if ($p -and (Test-Path -LiteralPath $p)) { return (Resolve-Path -LiteralPath $p).Path } }
    return $null
}

try {
    Write-Step '1/6 - Checking Java 17'
    $javaExe = Find-Java
    $javaMajor = 0
    if ($javaExe) {
        $probeOut = Join-Path $env:TEMP ("uobidah-java-probe-" + [guid]::NewGuid().ToString('N') + '.txt')
        try {
            $probe = Start-Process -FilePath $javaExe -ArgumentList '-version' -NoNewWindow -Wait -PassThru -RedirectStandardError $probeOut
            $probeText = if (Test-Path -LiteralPath $probeOut) { Get-Content -LiteralPath $probeOut -Raw } else { '' }
            $probeMatch = [regex]::Match([string]$probeText, '(?i)version\s+"?(\d+)')
            if ($probeMatch.Success) { $javaMajor = [int]$probeMatch.Groups[1].Value }
        } finally { Remove-Item -LiteralPath $probeOut -Force -ErrorAction SilentlyContinue }
    }
    if (-not $javaExe -or $javaMajor -ne 17) {
        Write-Host 'A Java 17 runtime was not found. Installing Temurin JDK 17 with winget...' -ForegroundColor Yellow
        $winget = Get-Command winget.exe -ErrorAction SilentlyContinue
        if (-not $winget) { Fail 'Java 17 is missing and winget is unavailable. Install JDK 17, then run this file again.' }
        $wingetResult = Invoke-NativeCapture { & $winget.Source install --id EclipseAdoptium.Temurin.17.JDK --exact --silent --accept-package-agreements --accept-source-agreements 2>&1 }
        foreach ($line in $wingetResult.Output) { Write-Host $line }
        if ($wingetResult.ExitCode -ne 0) { Fail "JDK 17 installation failed (winget exit $($wingetResult.ExitCode))." }
        $adoptiumRoot = Join-Path $env:ProgramFiles 'Eclipse Adoptium'
        $installed17 = @()
        if (Test-Path -LiteralPath $adoptiumRoot) {
            $installed17 = @(Get-ChildItem -LiteralPath $adoptiumRoot -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | ForEach-Object { Join-Path $_.FullName 'bin\java.exe' } | Where-Object { Test-Path -LiteralPath $_ })
        }
        if ($installed17.Count -gt 0) { $javaExe = $installed17[0] } else { $javaExe = Find-Java }
        if (-not $javaExe) { Fail 'JDK 17 installation completed but java.exe could not be located. Reopen Windows and retry.' }
        $javaMajor = 0
    }
    # java -version writes to STDERR. Do not invoke it through a PowerShell
    # pipeline while ErrorActionPreference=Stop, because PS 5.1 can turn that
    # normal STDERR output into a terminating error before it can be parsed.
    $stdoutFile = Join-Path $env:TEMP ("uobidah-java-out-" + [guid]::NewGuid().ToString('N') + '.txt')
    $stderrFile = Join-Path $env:TEMP ("uobidah-java-err-" + [guid]::NewGuid().ToString('N') + '.txt')
    try {
        $javaProcess = Start-Process -FilePath $javaExe -ArgumentList '-version' -NoNewWindow -Wait -PassThru `
            -RedirectStandardOutput $stdoutFile -RedirectStandardError $stderrFile
        $versionOutput = ''
        if (Test-Path -LiteralPath $stdoutFile) { $versionOutput += (Get-Content -LiteralPath $stdoutFile -Raw) }
        if (Test-Path -LiteralPath $stderrFile) { $versionOutput += (Get-Content -LiteralPath $stderrFile -Raw) }
    }
    finally {
        Remove-Item -LiteralPath $stdoutFile,$stderrFile -Force -ErrorAction SilentlyContinue
    }
    Write-Host "Java executable: $javaExe"
    Write-Host $versionOutput
    if ($javaProcess.ExitCode -ne 0) { Fail "java -version returned exit code $($javaProcess.ExitCode)." }
    $versionMatch = [regex]::Match([string]$versionOutput, '(?i)version\s+"?(\d+)(?:\.(\d+))?')
    if (-not $versionMatch.Success) { Fail "Could not parse Java version output: $versionOutput" }
    $major = [int]$versionMatch.Groups[1].Value
    if ($major -ne 17) { Fail "Found Java major version $major after bootstrap. This project requires Java 17." }
    $javaHome = Split-Path -Parent (Split-Path -Parent $javaExe)
    $env:JAVA_HOME = $javaHome
    $env:Path = (Join-Path $javaHome 'bin') + ';' + $env:Path
    Write-Host '[OK] Java 17 accepted; patch versions such as 17.0.21 are supported.' -ForegroundColor Green

    Write-Step '2/6 - Locating Android SDK'
    # Prefer a complete installed SDK over a stale/empty environment-variable path.
    $sdkCandidates = @(
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME,
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
        (Join-Path $env:USERPROFILE 'Android\Sdk')
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique
    $sdkRoot = $null
    foreach ($candidate in $sdkCandidates) {
        if (Test-Path -LiteralPath $candidate) {
            $candidatePlatform = Join-Path $candidate 'platforms\android-35\android.jar'
            $candidateAapt2 = Join-Path $candidate 'build-tools\34.0.0\aapt2.exe'
            if ((Test-Path -LiteralPath $candidatePlatform) -and (Test-Path -LiteralPath $candidateAapt2)) {
                $sdkRoot = $candidate
                break
            }
        }
    }
    if (-not $sdkRoot) {
        foreach ($candidate in $sdkCandidates) {
            if (Test-Path -LiteralPath $candidate) { $sdkRoot = $candidate; break }
        }
    }
    if (-not $sdkRoot) { $sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    if (-not (Test-Path -LiteralPath $sdkRoot)) { New-Item -ItemType Directory -Force -Path $sdkRoot | Out-Null }
    $sdkRoot = (Resolve-Path -LiteralPath $sdkRoot).Path
    $env:ANDROID_SDK_ROOT = $sdkRoot
    $env:ANDROID_HOME = $sdkRoot
    Write-Host "Android SDK: $sdkRoot"

    Write-Step '3/6 - Locating Android SDK command-line tools (only needed for missing SDK packages)'
    $sdkManager = $null
    $cmdlineRoot = Join-Path $sdkRoot 'cmdline-tools'
    if (Test-Path -LiteralPath $cmdlineRoot) {
        $sdkManager = Get-ChildItem -Path $cmdlineRoot -Filter sdkmanager.bat -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
    }
    if ($sdkManager) { Write-Host "SDK Manager found: $sdkManager" -ForegroundColor Green }
    else { Write-Host '[INFO] sdkmanager is not installed. A complete local SDK can still build offline; command-line tools will only be downloaded if required SDK packages are missing.' -ForegroundColor Yellow }

    Write-Step '4/6 - Verifying the local Android SDK (offline-first)'
    # Do not force sdkmanager to contact Google's repository metadata on every run.
    # This was the cause of the user's latest failure: sdkmanager stopped at
    # "Computing updates" even though Java, the SDK root and sdkmanager existed.
    $sdkRequiredFiles = @(
        (Join-Path $sdkRoot 'platforms\android-35\android.jar'),
        (Join-Path $sdkRoot 'build-tools\34.0.0\aapt.exe'),
        (Join-Path $sdkRoot 'build-tools\34.0.0\aapt2.exe'),
        (Join-Path $sdkRoot 'build-tools\34.0.0\aidl.exe'),
        (Join-Path $sdkRoot 'build-tools\34.0.0\d8.bat'),
        (Join-Path $sdkRoot 'build-tools\34.0.0\zipalign.exe'),
        (Join-Path $sdkRoot 'build-tools\34.0.0\apksigner.bat')
    )
    $sdkMissing = @($sdkRequiredFiles | Where-Object { -not (Test-Path -LiteralPath $_) })
    $sdkManagerLog = Join-Path $PSScriptRoot 'SDK_MANAGER_LOG.txt'

    function Invoke-SdkManagerLogged([string]$Manager, [string]$SdkRoot, [string[]]$Arguments, [string]$LogPath) {
        $nativeResult = Invoke-NativeCapture { & $Manager "--sdk_root=$SdkRoot" @Arguments 2>&1 }
        $nativeCode = $nativeResult.ExitCode
        $nativeLines = @($nativeResult.Output)
        Set-Content -LiteralPath $LogPath -Value $nativeLines -Encoding UTF8
        foreach ($line in $nativeLines) { Write-Host $line }
        return [int]$nativeCode
    }

    if ($sdkMissing.Count -eq 0) {
        Write-Host '[OK] Android API 35 and Build Tools 34.0.0 are already installed. Skipping sdkmanager network update.' -ForegroundColor Green
        Write-Host '[OK] Existing SDK packages will be used; this avoids the unnecessary repository metadata check.' -ForegroundColor Green
    } else {
        Write-Host '[NOTICE] Some required SDK files are missing:' -ForegroundColor Yellow
        foreach ($item in $sdkMissing) { Write-Host "  - $item" -ForegroundColor Yellow }

        if (-not $sdkManager) {
            Write-Host 'Required SDK packages are absent and sdkmanager is not installed. Downloading Android command-line tools is now necessary.' -ForegroundColor Yellow
            $toolsZip = Join-Path $env:TEMP 'android-commandlinetools-win.zip'
            $toolsUrl = 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip'
            $tempTools = Join-Path $env:TEMP ('android-cmdline-' + [guid]::NewGuid().ToString('N'))
            try {
                Invoke-WebRequest -Uri $toolsUrl -OutFile $toolsZip -UseBasicParsing
                New-Item -ItemType Directory -Force -Path $tempTools | Out-Null
                Expand-Archive -LiteralPath $toolsZip -DestinationPath $tempTools -Force
                $cmdRoot = Join-Path $sdkRoot 'cmdline-tools\latest'
                New-Item -ItemType Directory -Force -Path $cmdRoot | Out-Null
                Copy-Item -Path (Join-Path $tempTools 'cmdline-tools\*') -Destination $cmdRoot -Recurse -Force
                $sdkManager = Join-Path $cmdRoot 'bin\sdkmanager.bat'
            } catch {
                Fail "Android SDK packages are missing and command-line tools could not be downloaded. Connect to the Internet or install Android command-line tools manually. URL: $toolsUrl. Details: $($_.Exception.Message)"
            } finally {
                if (Test-Path -LiteralPath $tempTools) { Remove-Item -LiteralPath $tempTools -Recurse -Force -ErrorAction SilentlyContinue }
            }
            if (-not (Test-Path -LiteralPath $sdkManager)) { Fail 'sdkmanager.bat was not found after extracting the Android command-line tools.' }
            Write-Host "SDK Manager installed: $sdkManager" -ForegroundColor Green
        }

        $sdkLicense = Join-Path $sdkRoot 'licenses\android-sdk-license'
        if (-not (Test-Path -LiteralPath $sdkLicense)) {
            Write-Host 'Android SDK license has not been recorded. Opening sdkmanager license acceptance...' -ForegroundColor Yellow
            $licenseInput = ('y' + [Environment]::NewLine) * 80
            $licenseResult = Invoke-NativeCapture { $licenseInput | & $sdkManager "--sdk_root=$sdkRoot" --licenses 2>&1 }
            $licenseCode = $licenseResult.ExitCode
            $licenseLines = @($licenseResult.Output)
            Set-Content -LiteralPath $sdkManagerLog -Value $licenseLines -Encoding UTF8
            if ($licenseCode -ne 0 -and -not (Test-Path -LiteralPath $sdkLicense)) {
                Write-Host '[FAILED] Could not accept Android SDK licenses. SDK Manager output:' -ForegroundColor Red
                Get-Content -LiteralPath $sdkManagerLog -Tail 80 | ForEach-Object { Write-Host $_ -ForegroundColor Yellow }
                Fail 'Android SDK license setup failed. Check SDK_MANAGER_LOG.txt.'
            }
        }

        $packagesToInstall = @()
        if (-not (Test-Path -LiteralPath (Join-Path $sdkRoot 'platforms\android-35\android.jar'))) { $packagesToInstall += 'platforms;android-35' }
        if (@($sdkMissing | Where-Object { $_ -like '*build-tools*' }).Count -gt 0) { $packagesToInstall += 'build-tools;34.0.0' }
        if ($packagesToInstall.Count -gt 0) {
            $sdkCode = Invoke-SdkManagerLogged -Manager $sdkManager -SdkRoot $sdkRoot -Arguments $packagesToInstall -LogPath $sdkManagerLog
            $sdkMissing = @($sdkRequiredFiles | Where-Object { -not (Test-Path -LiteralPath $_) })
            if ($sdkMissing.Count -gt 0) {
                Write-Host '[FAILED] SDK Manager did not install all required files. Diagnostic output:' -ForegroundColor Red
                if (Test-Path -LiteralPath $sdkManagerLog) { Get-Content -LiteralPath $sdkManagerLog -Tail 100 | ForEach-Object { Write-Host $_ -ForegroundColor Yellow } }
                Fail "Android SDK packages are incomplete (sdkmanager exit $sdkCode). Full details: SDK_MANAGER_LOG.txt."
            }
            if ($sdkCode -ne 0) { Write-Host '[NOTICE] sdkmanager returned a non-zero exit, but all required files are present; continuing with the local SDK.' -ForegroundColor Yellow }
        }
    }

    # Platform-tools are optional for compilation; ADB installation is attempted later only if available.
    $adbInSdk = Join-Path $sdkRoot 'platform-tools\adb.exe'
    if (-not (Test-Path -LiteralPath $adbInSdk)) {
        Write-Host '[NOTICE] SDK platform-tools/adb.exe is absent. APK compilation can still continue; automatic TV installation may be skipped.' -ForegroundColor Yellow
    }

    Write-Step '5/6 - Running regression tests and building APK'
    if (-not (Test-Path '.\gradlew.bat')) { Fail 'gradlew.bat is missing from the project folder.' }

    # PowerShell 5.1 transcripts can omit native Gradle worker diagnostics.
    # Capture test and build output separately so the first failing task is clear.
    $gradleCommon = @('--no-daemon', '--console=plain', '--stacktrace', '--warning-mode', 'all', '--max-workers=2')
    $testLog = Join-Path $PSScriptRoot 'GRADLE_TEST_LOG.txt'
    $buildLog = Join-Path $PSScriptRoot 'GRADLE_BUILD_LOG.txt'

    function Invoke-GradleLogged([string]$TaskName, [string]$LogPath) {
        Remove-Item -LiteralPath $LogPath -Force -ErrorAction SilentlyContinue
        Write-Host "`n[Gradle] Running $TaskName ..." -ForegroundColor Cyan

        # Run the .bat through cmd.exe and let cmd return the real Gradle
        # ERRORLEVEL. Do not infer it from PowerShell's $LASTEXITCODE: in PS 5.1
        # a dot-sourced scriptblock can leave that value in a different scope.
        $runnerPath = Join-Path $env:TEMP ("uobidah-gradle-" + [guid]::NewGuid().ToString('N') + '.cmd')
        $wrapperPath = Join-Path $PSScriptRoot 'gradlew.bat'
        $projectPath = $PSScriptRoot.Replace('%', '%%')
        $wrapperPath = $wrapperPath.Replace('%', '%%')
        $safeLogPath = $LogPath.Replace('%', '%%')
        $taskArguments = (@($gradleCommon) + @($TaskName)) -join ' '
        $runnerLines = @(
            '@echo off',
            'chcp 65001 >nul',
            'setlocal DisableDelayedExpansion',
            ('cd /d "' + $projectPath + '"'),
            ('call "' + $wrapperPath + '" ' + $taskArguments + ' > "' + $safeLogPath + '" 2>&1'),
            'set "GRADLE_EXIT_CODE=%ERRORLEVEL%"',
            ('echo.>>"' + $safeLogPath + '"'),
            ('echo [GRADLE_EXIT_CODE=%GRADLE_EXIT_CODE%]>>"' + $safeLogPath + '"'),
            'exit /b %GRADLE_EXIT_CODE%'
        )
        $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllLines($runnerPath, $runnerLines, $utf8NoBom)

        $process = $null
        $code = 1
        try {
            $psi = New-Object System.Diagnostics.ProcessStartInfo
            $psi.FileName = if ($env:ComSpec) { $env:ComSpec } else { Join-Path $env:SystemRoot 'System32\cmd.exe' }
            # /S /C with doubled outer quotes correctly handles the TEMP path if it has spaces.
            $psi.Arguments = '/d /s /c ""' + $runnerPath + '""'
            $psi.WorkingDirectory = $PSScriptRoot
            $psi.UseShellExecute = $false
            $psi.CreateNoWindow = $true
            $process = [System.Diagnostics.Process]::Start($psi)
            if ($null -eq $process) { throw 'Could not start cmd.exe for the Gradle wrapper.' }
            $process.WaitForExit()
            $code = [int]$process.ExitCode
        } finally {
            if ($null -ne $process) { $process.Dispose() }
            Remove-Item -LiteralPath $runnerPath -Force -ErrorAction SilentlyContinue
        }

        if (Test-Path -LiteralPath $LogPath) {
            Get-Content -LiteralPath $LogPath | ForEach-Object { Write-Host $_ }
        } else {
            Write-Host '[ERROR] Gradle did not create its task log.' -ForegroundColor Red
        }

        if ($code -ne 0) {
            Write-Host "`n========== ROOT-CAUSE SUMMARY: $TaskName (exit $code) ==========" -ForegroundColor Red
            if (Test-Path -LiteralPath $LogPath) {
                $matches = @(Select-String -LiteralPath $LogPath -Pattern 'FAILURE: Build failed|What went wrong:|Execution failed for task|Compilation error|(^|\s)e: |error:|Caused by:|Could not resolve|Could not find|SSLHandshakeException|Remote host terminated the handshake|SSL peer shut down incorrectly|SDK location not found|requires Java|No tests found|There were failing tests' -Context 1,3)
                if ($matches.Count -gt 0) {
                    foreach ($item in ($matches | Select-Object -First 60)) { Write-Host $item.ToString() -ForegroundColor Yellow }
                } else {
                    Get-Content -LiteralPath $LogPath -Tail 120 | ForEach-Object { Write-Host $_ -ForegroundColor Yellow }
                }
                if (Select-String -LiteralPath $LogPath -Pattern 'SSLHandshakeException|Remote host terminated the handshake|SSL peer shut down incorrectly' -Quiet) {
                    Write-Host '[NETWORK/TLS] Gradle could not establish HTTPS to a dependency repository. TLS 1.2 and an alternate Maven Central hostname are configured; if this persists, check VPN/proxy/antivirus HTTPS inspection or try another network.' -ForegroundColor Yellow
                    Write-Host 'This is dependency-download failure; Kotlin source compilation has not started.' -ForegroundColor Yellow
                }
            }
            throw "$TaskName failed with actual process exit code $code. APK build was stopped. Full output: $LogPath"
        }
        return $true
    }

    Invoke-GradleLogged 'testDebugUnitTest' $testLog | Out-Null
    Invoke-GradleLogged 'assembleDebug' $buildLog | Out-Null
    $apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path -LiteralPath $apk)) { Fail 'Gradle reported success but app-debug.apk was not found.' }
    Write-Host "`n[OK] APK created: $apk" -ForegroundColor Green

    # ---- real test report from JUnit XML (no PASS is written unless Gradle actually ran the tests) ----
    $resDir = Join-Path $PSScriptRoot 'app\build\test-results\testDebugUnitTest'
    $xmls = @(Get-ChildItem -LiteralPath $resDir -Filter '*.xml' -ErrorAction SilentlyContinue)
    if ($xmls.Count -eq 0) { Fail 'No JUnit result files were produced; tests did not run.' }
    $tot = 0; $fail = 0; $skip = 0
    foreach ($x in $xmls) {
        [xml]$d = Get-Content -LiteralPath $x.FullName -Raw
        $suites = @()
        if ($d.testsuite) { $suites += $d.testsuite }
        if ($d.testsuites) { $suites += @($d.testsuites.testsuite) }
        foreach ($suite in $suites) {
            $tot += [int]$suite.tests
            $fail += [int]$suite.failures + [int]$suite.errors
            $skip += [int]$suite.skipped
        }
    }
    $report = "Unit tests executed by Gradle on $(Get-Date -Format s): total=$tot failed=$fail skipped=$skip"
    Set-Content -LiteralPath (Join-Path $PSScriptRoot 'TEST_RESULTS_LATEST.txt') -Value $report -Encoding UTF8
    Write-Host $report
    if ($fail -gt 0) { Fail "$fail unit test(s) failed." }

    # ---- verify APK: signature + package/version ----
    $btRoot = Join-Path $sdkRoot 'build-tools\34.0.0'
    $apksigner = Join-Path $btRoot 'apksigner.bat'; $aapt = Join-Path $btRoot 'aapt.exe'
    if (Test-Path -LiteralPath $apksigner) {
        $signatureResult = Invoke-NativeCapture { & $apksigner verify --verbose $apk 2>&1 }
        foreach ($line in $signatureResult.Output) { Write-Host $line }
        if ($signatureResult.ExitCode -ne 0) { Fail 'apksigner verification failed.' }
    } else { Write-Host '[NOTICE] apksigner not found; signature not verified.' -ForegroundColor Yellow }
    if (Test-Path -LiteralPath $aapt) {
        $badgingResult = Invoke-NativeCapture { & $aapt dump badging $apk 2>&1 }
        if ($badgingResult.ExitCode -ne 0) { Fail "aapt failed while reading APK metadata: $($badgingResult.Output -join ' ')" }
        $badging = $badgingResult.Output | Select-Object -First 1
        Write-Host $badging
        if ($badging -notmatch "name='com\.uobidah\.launcher'") { Fail 'APK package name is not com.uobidah.launcher.' }
    }

    Write-Step '6/6 - Safe installation on the selected Android TV/device'
    $adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
    if (-not (Test-Path -LiteralPath $adb)) {
        $adbCommand = Get-Command adb.exe -ErrorAction SilentlyContinue
        if ($adbCommand) { $adb = $adbCommand.Source }
    }
    if (-not (Test-Path -LiteralPath $adb)) {
        Write-Host '[NOTICE] ADB is not installed. APK was built, but automatic installation is unavailable.' -ForegroundColor Yellow
        Write-Host 'Install Android SDK Platform-Tools, then run INSTALL_APK.bat or:'
        Write-Host '  adb install -r app\build\outputs\apk\debug\app-debug.apk'
    } else {
        $adbServerResult = Invoke-NativeCapture { & $adb start-server 2>&1 }
        if ($adbServerResult.ExitCode -ne 0) { Write-Host "[NOTICE] ADB server could not start: $($adbServerResult.Output -join ' ')" -ForegroundColor Yellow }
        $deviceResult = Invoke-NativeCapture { & $adb devices 2>&1 }
        $deviceRows = @($deviceResult.Output)
        $readyDevices = @($deviceRows | Where-Object { $_ -match '^\s*\S+\s+device\s*$' })
        if ($readyDevices.Count -eq 0) {
            Write-Host '[NOTICE] APK build succeeded, but no ADB-authorized device is connected. No installation was attempted.' -ForegroundColor Yellow
            Write-Host 'Enable USB/Wireless debugging, accept the RSA prompt, then run INSTALL_APK.bat.'
            foreach ($row in ($deviceRows | Where-Object { $_ -match 'unauthorized|offline' })) { Write-Host "  $row" -ForegroundColor Yellow }
        } else {
            $installer = Join-Path $PSScriptRoot 'INSTALL_APK.ps1'
            if (-not (Test-Path -LiteralPath $installer)) { Fail 'INSTALL_APK.ps1 is missing. Re-extract the complete project ZIP.' }
            & powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File $installer -ApkPath $apk -AdbPath $adb
            $installCode = $LASTEXITCODE
            if ($installCode -ne 0) { Fail "Safe ADB installation did not complete (exit $installCode). Review INSTALL_LOG.txt; no existing app was uninstalled automatically." }
        }
    }

}
catch {
    Write-Host "`nBuild setup stopped: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "PowerShell transcript: $Log" -ForegroundColor Yellow
    Write-Host "Gradle test/build logs: GRADLE_TEST_LOG.txt and GRADLE_BUILD_LOG.txt (when those tasks were reached)." -ForegroundColor Yellow
    try { Stop-Transcript | Out-Null } catch {}
    exit 1
}
Stop-Transcript | Out-Null
exit 0
