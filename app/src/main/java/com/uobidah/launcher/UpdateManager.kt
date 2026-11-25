package com.uobidah.launcher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.uobidah.launcher.core.UpdatePolicy
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal data class UpdateManifest(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val apkUrl: String,
    val sha256: String,
    val releaseNotes: String
)

sealed class UpdateCheckResult {
    data class Ready(val versionName: String, val versionCode: Long, val apk: File, val notes: String) : UpdateCheckResult()
    data class Current(val versionName: String) : UpdateCheckResult()
    data object NeedsRepository : UpdateCheckResult()
    data class Failed(val message: String) : UpdateCheckResult()
}

/** GitHub Releases updater. It downloads and verifies updates but delegates installation confirmation to Android. */
object UpdateManager {
    private const val CHANNEL_ID = "abo_uobidah_updates"
    private const val NOTIFICATION_ID = 19020
    private const val MAX_MANIFEST_BYTES = 256 * 1024
    private const val MAX_APK_BYTES = 180L * 1024L * 1024L
    private val lock = Any()

    fun normalizeRepository(raw: String): String? = UpdatePolicy.normalizeRepository(raw)

    fun currentVersionCode(context: Context): Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else legacyVersionCode(info)
    } catch (_: Exception) { 0L }

    @Suppress("DEPRECATION")
    private fun legacyVersionCode(info: PackageInfo): Long = info.versionCode.toLong()

    fun currentVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "غير معروف"
    } catch (_: Exception) { "غير معروف" }

    fun readyApk(context: Context): File? {
        val prefs = Prefs(context)
        val code = prefs.updateReadyVersionCode
        val path = prefs.updateReadyPath
        if (code <= currentVersionCode(context) || path.isBlank()) return null
        val file = File(path)
        if (!file.isFile || file.length() <= 0L) return null
        return runCatching {
            val storedHash = prefs.updateReadySha256
            if (!UpdatePolicy.isValidSha256(storedHash) || !sha256(file).equals(storedHash, ignoreCase = true)) {
                throw SecurityException("فشل إعادة التحقق من سلامة ملف التحديث المحفوظ")
            }
            verifyDownloadedApk(context, file, code)
            file
        }.getOrNull()
    }

    fun checkAndDownload(context: Context, force: Boolean = false): UpdateCheckResult = synchronized(lock) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        if (!force && !prefs.autoUpdatesEnabled) return@synchronized UpdateCheckResult.Current(currentVersionName(app))
        val repository = UpdatePolicy.normalizeRepository(prefs.updateRepository)
            ?: return@synchronized UpdateCheckResult.NeedsRepository
        try {
            val manifest = fetchLatestManifest(repository, prefs.testChannel)
            val installedCode = currentVersionCode(app)
            if (!UpdatePolicy.isNewer(manifest.versionCode, installedCode)) {
                val stalePath = prefs.updateReadyPath
                if (stalePath.isNotBlank()) runCatching { File(stalePath).delete() }
                prefs.updateReadyPath = ""
                prefs.updateReadyVersionName = ""
                prefs.updateReadyVersionCode = 0L
                prefs.updateReadySha256 = ""
                prefs.updateReadyNotes = ""
                prefs.updateLastCheckAt = System.currentTimeMillis()
                return@synchronized UpdateCheckResult.Current(currentVersionName(app))
            }
            val existing = readyApk(app)
            if (existing != null && prefs.updateReadyVersionCode == manifest.versionCode &&
                prefs.updateReadySha256.equals(manifest.sha256, ignoreCase = true)) {
                prefs.updateLastCheckAt = System.currentTimeMillis()
                return@synchronized UpdateCheckResult.Ready(manifest.versionName, manifest.versionCode, existing, manifest.releaseNotes)
            }

            val dir = File(app.filesDir, "updates")
            if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("تعذر إنشاء مجلد التحديث")
            dir.listFiles()?.filter { it.isFile && it.name.endsWith(".part") }?.forEach { it.delete() }
            val target = File(dir, "Abo-Uobidah-TV-${manifest.versionCode}.apk")
            val temp = File(dir, target.name + ".part")
            temp.delete()
            downloadHttps(manifest.apkUrl, temp)
            val actualHash = sha256(temp)
            if (!actualHash.equals(manifest.sha256, ignoreCase = true)) {
                temp.delete()
                throw SecurityException("فشل التحقق من سلامة ملف التحديث (SHA-256)")
            }
            verifyDownloadedApk(app, temp, manifest.versionCode)
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            prefs.updateReadyPath = target.absolutePath
            prefs.updateReadyVersionName = manifest.versionName
            prefs.updateReadyVersionCode = manifest.versionCode
            prefs.updateReadySha256 = manifest.sha256.lowercase()
            prefs.updateReadyNotes = manifest.releaseNotes.take(4000)
            prefs.updateLastCheckAt = System.currentTimeMillis()
            UpdateCheckResult.Ready(manifest.versionName, manifest.versionCode, target, manifest.releaseNotes)
        } catch (e: Exception) {
            UpdateCheckResult.Failed(e.message?.take(240) ?: "تعذر فحص التحديث")
        }
    }

    private fun fetchLatestManifest(repository: String, testChannel: Boolean = false): UpdateManifest {
        val apiUrl = if (testChannel) "https://api.github.com/repos/$repository/releases?per_page=10" else "https://api.github.com/repos/$repository/releases/latest"
        val releaseText = String(
            readHttpsBytes(
                apiUrl,
                timeoutMs = 20000,
                maxBytes = 2 * 1024 * 1024,
                headers = mapOf("Accept" to "application/vnd.github+json", "X-GitHub-Api-Version" to "2022-11-28")
            ), Charsets.UTF_8
        )
        val release: JSONObject = if (testChannel) {
            // قناة الاختبار (اختيارية): أحدث إصدار غير مسودة، ويشمل التجريبي. باقي الفحوص (التوقيع والإصدار والبصمة) كما هي.
            val arr = org.json.JSONArray(releaseText)
            var found: JSONObject? = null
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                if (UpdatePolicy.releaseAllowed(r.optBoolean("draft", false), r.optBoolean("prerelease", false), true)) { found = r; break }
            }
            found ?: throw IllegalStateException("لا يوجد إصدار منشور")
        } else {
            val r = JSONObject(releaseText)
            if (r.optBoolean("draft", false) || r.optBoolean("prerelease", false)) {
                throw IllegalStateException("لا يوجد إصدار مستقر منشور")
            }
            r
        }
        val assets = release.optJSONArray("assets") ?: throw IllegalStateException("الإصدار لا يحتوي على ملفات مرفقة")
        var manifestUrl: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            if (asset.optString("name").equals("update.json", true)) {
                manifestUrl = asset.optString("browser_download_url")
                break
            }
        }
        val safeManifestUrl = manifestUrl?.takeIf {
            it.startsWith("https://github.com/$repository/releases/download/", ignoreCase = true) &&
                it.substringAfterLast('/').equals("update.json", ignoreCase = true)
        } ?: throw IllegalStateException("ملف update.json غير موجود في أحدث إصدار")
        val manifestText = String(readHttpsBytes(safeManifestUrl, timeoutMs = 20000, maxBytes = MAX_MANIFEST_BYTES), Charsets.UTF_8)
        return parseManifest(manifestText, release.optString("tag_name"), release.optString("body", ""), repository)
    }

    /** Validates update.json against the GitHub release it came from (pure; covered by UpdateManifestContractTest). */
    internal fun parseManifest(manifestText: String, releaseTag: String, releaseBody: String, repository: String): UpdateManifest {
        val json = JSONObject(manifestText)
        val packageName = json.optString("packageName")
        if (packageName != "com.uobidah.launcher") throw SecurityException("ملف التحديث لا يخص Abo Uobidah TV")
        val versionName = json.optString("versionName").trim()
        val versionCode = json.optLong("versionCode", 0L)
        if (versionName.isBlank() || versionCode <= 0L) throw IllegalStateException("بيانات الإصدار غير مكتملة")
        val apkUrl = json.optString("apkUrl").trim()
        if (!UpdatePolicy.isAllowedReleaseAsset(apkUrl, repository)) throw SecurityException("رابط APK ليس من مستودع الإصدار المحدد")
        val hash = json.optString("sha256").trim()
        if (!UpdatePolicy.isValidSha256(hash)) throw SecurityException("بصمة SHA-256 غير صحيحة")
        if (json.optString("tag") != releaseTag) throw SecurityException("وسم الإصدار في manifest لا يطابق GitHub")
        val tag = releaseTag.removePrefix("v")
        if (tag != versionName) throw SecurityException("رقم الإصدار في manifest لا يطابق وسم GitHub")
        val notes = json.optString("releaseNotes", releaseBody).take(4000)
        return UpdateManifest(packageName, versionName, versionCode, apkUrl, hash, notes)
    }

    private fun isTrustedGitHubHost(host: String): Boolean =
        UpdatePolicy.isTrustedGitHubHost(host)

    /** Reads small GitHub API/manifest responses and refuses HTTPS-to-HTTP redirects. */
    private fun readHttpsBytes(
        rawUrl: String,
        timeoutMs: Int,
        maxBytes: Int,
        headers: Map<String, String> = emptyMap()
    ): ByteArray {
        var current = URL(rawUrl)
        var hops = 0
        while (true) {
            if (!current.protocol.equals("https", true) || (current.port != -1 && current.port != 443) ||
                !isTrustedGitHubHost(current.host.lowercase())) {
                throw SecurityException("تم رفض الاتصال بخادم تحديث غير موثوق")
            }
            val connection = current.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = timeoutMs
                connection.readTimeout = timeoutMs
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "AboUobidahTV-Updater/1.0")
                connection.setRequestProperty("Accept", "application/json")
                headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: throw IllegalStateException("رابط تحويل غير صالح")
                    if (hops++ >= 6) throw IllegalStateException("عدد تحويلات اتصال التحديث كبير")
                    current = URL(current, location)
                    if (!current.protocol.equals("https", true) || (current.port != -1 && current.port != 443) ||
                !isTrustedGitHubHost(current.host.lowercase())) {
                        throw SecurityException("تم رفض تحويل إلى خادم تحديث غير موثوق")
                    }
                    continue
                }
                if (code !in 200..299) throw IllegalStateException("فشل الاتصال بخدمة التحديث: HTTP $code")
                if (connection.contentLengthLong > maxBytes) throw IllegalStateException("استجابة التحديث أكبر من الحد المسموح")
                return connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 32 * 1024))
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > maxBytes) throw IllegalStateException("استجابة التحديث أكبر من الحد المسموح")
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun downloadHttps(rawUrl: String, output: File) {
        var current = URL(rawUrl)
        var hops = 0
        while (true) {
            if (!current.protocol.equals("https", true) || (current.port != -1 && current.port != 443)) {
                throw SecurityException("تم رفض تحويل تنزيل غير آمن أو بمنفذ غير قياسي")
            }
            val host = current.host.lowercase()
            val trustedHost = isTrustedGitHubHost(host)
            if (!trustedHost) throw SecurityException("تم رفض خادم تنزيل غير موثوق")
            val connection = current.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 20000
                connection.readTimeout = 60000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "AboUobidahTV-Updater/1.0")
                connection.setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream")
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: throw IllegalStateException("رابط تنزيل غير صالح")
                    if (hops++ >= 6) throw IllegalStateException("عدد تحويلات رابط التنزيل كبير")
                    current = URL(current, location)
                    continue
                }
                if (code !in 200..299) throw IllegalStateException("فشل تنزيل التحديث: HTTP $code")
                val length = connection.contentLengthLong
                if (length > MAX_APK_BYTES) throw IllegalStateException("حجم التحديث أكبر من الحد المسموح")
                BufferedInputStream(connection.inputStream).use { input ->
                    BufferedOutputStream(FileOutputStream(output)).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_APK_BYTES) throw IllegalStateException("حجم التحديث أكبر من الحد المسموح")
                            out.write(buffer, 0, count)
                        }
                        out.flush()
                    }
                }
                if (output.length() < 1024L) throw IllegalStateException("ملف APK الذي تم تنزيله غير مكتمل")
                return
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    @Suppress("DEPRECATION")
    private fun verifyDownloadedApk(context: Context, file: File, expectedVersionCode: Long) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw IllegalStateException("الملف الذي تم تنزيله ليس APK صالحاً")
        if (archive.packageName != context.packageName) throw SecurityException("معرّف التطبيق في APK غير مطابق")
        val minimumSdk = archive.applicationInfo?.minSdkVersion ?: 1
        if (minimumSdk > Build.VERSION.SDK_INT) throw SecurityException("هذا التحديث يتطلب Android API $minimumSdk، والجهاز API ${Build.VERSION.SDK_INT}")
        val apkCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        if (apkCode != expectedVersionCode || !UpdatePolicy.isNewer(apkCode, currentVersionCode(context))) {
            throw SecurityException("رقم إصدار APK غير مطابق أو ليس أحدث من النسخة الحالية")
        }
        val currentInfo = context.packageManager.getPackageInfo(context.packageName, flags)
        val currentSigners = signerDigests(currentInfo)
        val apkSigners = signerDigests(archive)
        if (currentSigners.isEmpty() || apkSigners.isEmpty() || currentSigners != apkSigners) {
            throw SecurityException("شهادة توقيع التحديث مختلفة عن التطبيق المثبت؛ تم إيقاف التثبيت لحماية بياناتك")
        }
    }

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            info.signatures?.toList().orEmpty()
        }
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }.toSet()
    }

    fun launchInstaller(context: Context): Boolean {
        val apk = readyApk(context) ?: return false
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val permissionIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(permissionIntent)
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val install = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        context.startActivity(install)
        return true
    }

    fun notifyReady(context: Context, versionName: String, versionCode: Long) {
        val prefs = Prefs(context)
        if (prefs.updateLastNotifiedCode >= versionCode) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "تحديثات Abo Uobidah TV", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val tap = Intent(context, UpdateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = android.app.PendingIntent.getActivity(
            context, 19020, tap,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("تحديث Abo Uobidah TV جاهز")
            .setContentText("الإصدار $versionName تم تنزيله والتحقق منه. اضغط للمتابعة.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("الإصدار $versionName جاهز. افتح شاشة التحديثات ثم اختر تثبيت التحديث."))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            val compat = NotificationManagerCompat.from(context)
            if (!compat.areNotificationsEnabled()) return
            compat.notify(NOTIFICATION_ID, notification)
            prefs.updateLastNotifiedCode = versionCode
        } catch (_: SecurityException) {
            // Leave the notification marker unchanged; it can be retried after permission is granted.
        }
    }
}

object UpdateScheduler {
    private const val PERIODIC_NAME = "abo_uobidah_tv_periodic_update_check"
    private const val IMMEDIATE_NAME = "abo_uobidah_tv_immediate_update_check"
    private const val LAUNCH_NAME = "abo_uobidah_tv_launch_update_check"
    private val LAUNCH_CHECK_MIN_AGE_MS = TimeUnit.HOURS.toMillis(6)

    fun schedule(context: Context) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        if (!prefs.autoUpdatesEnabled || UpdatePolicyRepo.isBlank(prefs.updateRepository)) {
            WorkManager.getInstance(app).cancelUniqueWork(PERIODIC_NAME)
            return
        }
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
            .setInitialDelay(10, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Also check soon after app launch when the last attempt is stale; unique work prevents page-navigation spam. */
    fun checkOnLaunch(context: Context) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        if (!prefs.autoUpdatesEnabled || UpdateManager.normalizeRepository(prefs.updateRepository) == null) return
        val elapsed = System.currentTimeMillis() - prefs.updateLastCheckAt
        if (prefs.updateLastCheckAt > 0L && elapsed in 0 until LAUNCH_CHECK_MIN_AGE_MS) return
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(LAUNCH_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun checkNow(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<UpdateCheckWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(IMMEDIATE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        val wm = WorkManager.getInstance(context.applicationContext)
        wm.cancelUniqueWork(PERIODIC_NAME)
        wm.cancelUniqueWork(IMMEDIATE_NAME)
        wm.cancelUniqueWork(LAUNCH_NAME)
    }
}

/** Kept separate to make the scheduling guard easy to audit. */
private object UpdatePolicyRepo {
    fun isBlank(repository: String): Boolean = UpdateManager.normalizeRepository(repository) == null
}
