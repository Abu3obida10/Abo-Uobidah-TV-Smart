#Requires -Version 5.1
[CmdletBinding()]
param(
    [string]$ApkPath,
    [string]$VersionName,
    [long]$VersionCode = 0,
    [string]$Repository = 'Abu3obida10/Abo-Uobidah-TV-Smart',
    [string]$ReleaseNotes = 'إصلاحات وتحسينات لتطبيق Abo Uobidah TV.',
    [string]$ExpectedSignerSha256 = ''
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

function Invoke-NativeCapture([scriptblock]$Command) {
    # Windows PowerShell 5.1 may turn native stderr into ErrorRecords.
    # Capture it without terminating the script and preserve the native exit code.
    $savedPreference = $ErrorActionPreference
    $nativeOutput = @()
    $nativeExitCode = 0
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
    $normalized = @($nativeOutput | ForEach-Object {
        if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.ToString() } else { [string]$_ }
    })
    return [pscustomobject]@{ ExitCode=$nativeExitCode; Output=$normalized }
}
function Stop-Publish([string]$Message) { throw $Message }
function Find-AndroidBuildTool([string]$Name) {
    $roots = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME,
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
        (Join-Path $env:USERPROFILE 'Android\Sdk')) |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique
    foreach ($sdk in $roots) {
        $tools = Join-Path $sdk 'build-tools'
        if (-not (Test-Path -LiteralPath $tools)) { continue }
        $matches = @(Get-ChildItem -LiteralPath $tools -Directory -ErrorAction SilentlyContinue |
            Sort-Object { try { [version]$_.Name } catch { [version]'0.0' } } -Descending |
            ForEach-Object { Join-Path $_.FullName $Name } | Where-Object { Test-Path -LiteralPath $_ })
        if ($matches.Count -gt 0) { return $matches[0] }
    }
    return $null
}
function Get-ApkMetadata([string]$AaptPath, [string]$Path) {
    $result = Invoke-NativeCapture { & $AaptPath dump badging $Path 2>&1 }
    if ($result.ExitCode -ne 0) { Stop-Publish "تعذر قراءة بيانات APK بواسطة aapt: $($result.Output -join ' ')" }
    $line = $result.Output | Where-Object { $_ -match "^package:" } | Select-Object -First 1
    if (-not $line) { Stop-Publish 'ملف APK لا يحتوي على بيانات package صالحة.' }
    $m = [regex]::Match($line, "package:\s+name='([^']+)'\s+versionCode='([^']+)'\s+versionName='([^']*)'")
    if (-not $m.Success) { Stop-Publish "تعذر تحليل بيانات اسم الحزمة والإصدار: $line" }
    return [pscustomobject]@{ Package=$m.Groups[1].Value; Code=[long]$m.Groups[2].Value; Name=$m.Groups[3].Value }
}

$manifestPath = $null
$releaseApkPath = $null
$releaseTempDir = $null
try {
    Write-Host 'Abo Uobidah TV — نشر إصدار التطبيق فقط (APK + update.json)' -ForegroundColor Cyan
    Write-Host 'لن يتم رفع ملفات المصدر أو Gradle أو المشروع أو أي ملفات أخرى.' -ForegroundColor Green

    if ([string]::IsNullOrWhiteSpace($ApkPath)) { $ApkPath = Read-Host 'أدخل المسار الكامل لملف APK الموقّع' }
    if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) { Stop-Publish "ملف APK غير موجود: $ApkPath" }
    $ApkPath = (Resolve-Path -LiteralPath $ApkPath).Path
    if ([IO.Path]::GetExtension($ApkPath) -ine '.apk') { Stop-Publish 'الملف المحدد ليس APK.' }
    $apkSize = (Get-Item -LiteralPath $ApkPath).Length
    if ($apkSize -lt 4096) { Stop-Publish 'حجم APK صغير بشكل غير طبيعي؛ تم إيقاف النشر.' }
    if ($apkSize -gt 180MB) { Stop-Publish 'حجم APK يتجاوز الحد الذي يقبله محدّث التطبيق (180 MB).' }

    $aapt = Find-AndroidBuildTool 'aapt.exe'
    $apksigner = Find-AndroidBuildTool 'apksigner.bat'
    if (-not $aapt -or -not $apksigner) { Stop-Publish 'لم يتم العثور على aapt.exe وapksigner.bat داخل Android SDK Build Tools. ثبّت Build Tools ثم أعد المحاولة.' }
    $meta = Get-ApkMetadata -AaptPath $aapt -Path $ApkPath
    if ($meta.Package -ne 'com.uobidah.launcher') { Stop-Publish "معرّف الحزمة غير صحيح: $($meta.Package). المتوقع com.uobidah.launcher." }

    # The Gradle build, installer, and release publisher must all use one version source.
    $versionPropertiesPath = Join-Path $PSScriptRoot 'gradle.properties'
    if (-not (Test-Path -LiteralPath $versionPropertiesPath)) { Stop-Publish 'ملف gradle.properties غير موجود؛ لا يمكن التحقق من هوية الإصدار.' }
    $versionPropertiesText = Get-Content -LiteralPath $versionPropertiesPath -Raw
    $projectNameMatch = [regex]::Match($versionPropertiesText, '(?m)^\s*appVersionName\s*=\s*([^\r\n#]+?)\s*$')
    $projectCodeMatch = [regex]::Match($versionPropertiesText, '(?m)^\s*appVersionCode\s*=\s*(\d+)\s*$')
    if (-not $projectNameMatch.Success -or -not $projectCodeMatch.Success) { Stop-Publish 'يجب تعريف appVersionName وappVersionCode داخل gradle.properties.' }
    $projectVersionName = $projectNameMatch.Groups[1].Value.Trim()
    $projectVersionCode = [long]$projectCodeMatch.Groups[1].Value

    if ([string]::IsNullOrWhiteSpace($VersionName)) { $VersionName = $projectVersionName }
    elseif ($VersionName -ne $projectVersionName) { Stop-Publish 'VersionName المطلوب لا يطابق إصدار المشروع المركزي في gradle.properties.' }
    if ($VersionCode -le 0) { $VersionCode = $projectVersionCode }
    elseif ($VersionCode -ne $projectVersionCode) { Stop-Publish 'VersionCode المطلوب لا يطابق رقم البناء المركزي في gradle.properties.' }

    if ($meta.Name -ne $projectVersionName -or $meta.Code -ne $projectVersionCode) {
        Stop-Publish 'إصدار APK لا يطابق appVersionName/appVersionCode الحاليين في gradle.properties.'
    }
    if ($meta.Name -ne $VersionName -or $meta.Code -ne $VersionCode) {
        Stop-Publish "بيانات APK لا تطابق البيانات المطلوبة. داخل الملف: $($meta.Name) / $($meta.Code)، المطلوب: $VersionName / $VersionCode."
    }
    if ($VersionName -notmatch '^\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?$') { Stop-Publish 'صيغة VersionName غير صالحة.' }
    if ($VersionCode -le 0) { Stop-Publish 'VersionCode يجب أن يكون أكبر من صفر.' }

    $signResult = Invoke-NativeCapture { & $apksigner verify --verbose --print-certs $ApkPath 2>&1 }
    if ($signResult.ExitCode -ne 0) { Stop-Publish "فشل التحقق من توقيع APK: $($signResult.Output -join ' ')" }
    $certLine = $signResult.Output | Where-Object { $_ -match 'certificate SHA-256 digest:' } | Select-Object -First 1
    if (-not $certLine) { Stop-Publish 'تعذر استخراج بصمة شهادة توقيع APK.' }
    $certMatch = [regex]::Match($certLine, '([A-Fa-f0-9]{64})\s*$')
    if (-not $certMatch.Success) { Stop-Publish 'تنسيق بصمة شهادة توقيع APK غير معروف.' }
    $signerSha = $certMatch.Groups[1].Value.ToLowerInvariant()
    if ($ExpectedSignerSha256 -and $signerSha -ne ($ExpectedSignerSha256 -replace '\s','').ToLowerInvariant()) {
        Stop-Publish 'شهادة توقيع APK لا تطابق ExpectedSignerSha256. لن يتم نشر تحديث قد يفشل على الأجهزة المثبت عليها التطبيق.'
    }

    if ($Repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') { Stop-Publish 'اكتب المستودع بصيغة owner/repository.' }
    $tag = "v$VersionName"
    $apkAsset = 'Abo-Uobidah-TV.apk'
    $apkUrl = "https://github.com/$Repository/releases/download/$tag/$apkAsset"
    $releaseTempDir = Join-Path $env:TEMP ("AboUobidahRelease-$VersionName-" + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $releaseTempDir -Force | Out-Null
    $releaseApkPath = Join-Path $releaseTempDir $apkAsset
    Copy-Item -LiteralPath $ApkPath -Destination $releaseApkPath -Force
    $sha = (Get-FileHash -LiteralPath $releaseApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $manifest = [ordered]@{
        packageName = 'com.uobidah.launcher'
        versionName = $VersionName
        versionCode = $VersionCode
        tag = $tag
        apkUrl = $apkUrl
        sha256 = $sha
        releaseNotes = $ReleaseNotes
    }
    $manifestPath = Join-Path $releaseTempDir 'update.json'
    $jsonText = $manifest | ConvertTo-Json -Depth 5
    # Windows PowerShell 5.1's Set-Content -Encoding UTF8 writes a BOM.
    # Android JSONObject does not consistently accept a BOM; write UTF-8 without BOM.
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($manifestPath, $jsonText + "`n", $utf8NoBom)

    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { Stop-Publish 'GitHub CLI غير مثبت. ثبّت gh ثم نفّذ gh auth login مرة واحدة.' }
    $auth = Invoke-NativeCapture { & gh auth status 2>&1 }
    if ($auth.ExitCode -ne 0) { Stop-Publish "GitHub CLI غير مسجل الدخول. نفّذ gh auth login. $($auth.Output -join ' ')" }

    $repoResult = Invoke-NativeCapture { & gh api "repos/$Repository" --jq '{"private":.private,"size":.size,"default_branch":.default_branch}' 2>&1 }
    if ($repoResult.ExitCode -ne 0) { Stop-Publish "لا يمكن الوصول إلى المستودع $Repository: $($repoResult.Output -join ' ')" }
    $repoInfo = ($repoResult.Output -join "`n") | ConvertFrom-Json
    if ($repoInfo.private -eq $true) { Stop-Publish 'المستودع يجب أن يكون عاماً حتى يستطيع التطبيق فحص التحديثات دون رمز وصول.' }

    # GitHub Releases need a tag target. For an empty repository, create a tiny
    # non-source README commit; no application source is pushed.
    if ([int]$repoInfo.size -eq 0) {
        $readme = "# Abo Uobidah TV — Updates`n`nThis repository contains app release assets only. No application source code is hosted here.`n"
        $payload = @{ message='Initialize release-only repository'; content=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($readme)) } | ConvertTo-Json -Compress
        $payloadFile = Join-Path $env:TEMP 'abo-uobidah-release-init.json'
        [System.IO.File]::WriteAllText($payloadFile, $payload, $utf8NoBom)
        $init = Invoke-NativeCapture { & gh api --method PUT "repos/$Repository/contents/README.md" --input $payloadFile 2>&1 }
        Remove-Item -LiteralPath $payloadFile -Force -ErrorAction SilentlyContinue
        if ($init.ExitCode -ne 0) { Stop-Publish "تعذر إنشاء مرجع README الضروري لبدء أول Release: $($init.Output -join ' ')" }
    }

    $existing = Invoke-NativeCapture { & gh api "repos/$Repository/releases/tags/$tag" 2>&1 }
    if ($existing.ExitCode -eq 0) { Stop-Publish "الإصدار $tag منشور بالفعل. لا تعِد استخدام tag؛ ارفع versionName وversionCode." }
    if (($existing.Output -join ' ') -notmatch '(?i)not found|404') {
        Stop-Publish "تعذر التأكد من عدم وجود tag بسبب خطأ GitHub API: $($existing.Output -join ' ')"
    }

    Write-Host "`nالتحقق ناجح: $($meta.Name) / code $($meta.Code)" -ForegroundColor Green
    Write-Host "APK SHA-256: $sha"
    Write-Host "Signing certificate SHA-256: $signerSha"
    Write-Host 'سيتم إرسال ملفين فقط: Abo-Uobidah-TV.apk و update.json.' -ForegroundColor Yellow
    $confirmation = Read-Host "اكتب PUBLISH $VersionName للمتابعة"
    if ($confirmation -cne "PUBLISH $VersionName") { Write-Host 'تم الإلغاء، ولم يتم رفع أي APK.' -ForegroundColor Yellow; exit 0 }

    $release = Invoke-NativeCapture { & gh release create $tag $releaseApkPath $manifestPath --repo $Repository --title "Abo Uobidah TV $VersionName" --notes $ReleaseNotes 2>&1 }
    foreach ($line in $release.Output) { Write-Host $line }
    if ($release.ExitCode -ne 0) { Stop-Publish "فشل نشر GitHub Release (exit $($release.ExitCode)). $($release.Output -join ' ')" }

    $verify = Invoke-NativeCapture { & gh api "repos/$Repository/releases/tags/$tag" 2>&1 }
    if ($verify.ExitCode -ne 0) { Stop-Publish 'تم إنشاء الطلب لكن تعذر التحقق من الإصدار المنشور عبر GitHub API.' }
    $releaseData = ($verify.Output -join "`n") | ConvertFrom-Json
    $assetNames = @($releaseData.assets | ForEach-Object { $_.name })
    if ($assetNames -notcontains $apkAsset -or $assetNames -notcontains 'update.json') {
        Stop-Publish "الإصدار منشور لكن التحقق من ملفي APK وupdate.json لم يكتمل. الأصول الظاهرة: $($assetNames -join ', ')"
    }
    Write-Host "`nتم نشر $tag بنجاح. الملفات المنشورة فقط: $apkAsset و update.json." -ForegroundColor Green
    Write-Host "https://github.com/$Repository/releases/tag/$tag"
    Write-Host 'ملاحظة: يجب أن تطابق شهادة توقيع هذا APK شهادة التطبيق المثبت على التلفزيون.' -ForegroundColor Yellow
}
catch {
    Write-Host "`nPUBLISH FAILED: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
finally {
    if ($manifestPath -and (Test-Path -LiteralPath $manifestPath)) { Remove-Item -LiteralPath $manifestPath -Force -ErrorAction SilentlyContinue }
    if ($releaseTempDir -and (Test-Path -LiteralPath $releaseTempDir)) { Remove-Item -LiteralPath $releaseTempDir -Recurse -Force -ErrorAction SilentlyContinue }
}
