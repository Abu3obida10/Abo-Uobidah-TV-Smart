package com.uobidah.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.io.IOException
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.TreeSet

/** شبكة بسيطة بلا مكتبات خارجية + خيوط خلفية. */
object Net {
    // Keep network work and image decode/download work in separate bounded pools.
    // A blocked image host can no longer occupy every worker and starve match/prayer requests.
    private val pool: ExecutorService = ThreadPoolExecutor(
        4, 4, 30L, java.util.concurrent.TimeUnit.SECONDS,
        ArrayBlockingQueue(48),
        { r -> Thread(r, "uobidah-net").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )
    private val imagePool: ExecutorService = ThreadPoolExecutor(
        3, 3, 30L, java.util.concurrent.TimeUnit.SECONDS,
        ArrayBlockingQueue(256),
        { r -> Thread(r, "uobidah-image").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )
    private val main = Handler(Looper.getMainLooper())
    private val taskLock = Object()
    private var submittedTaskId = 0L
    private var completedTaskPrefix = 0L
    private val completedOutOfOrder = TreeSet<Long>()

    const val UA_BROWSER =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    fun bg(block: () -> Unit) { submitBg(block) }

    /**
     * Enqueue a FIFO barrier behind all background work already submitted, then post its
     * completion to the main looper. Tests use this to wait for provider callbacks without
     * guessing how long a worker will take. Returns false only if the bounded queue is full.
     */
    fun afterPendingBgTasks(onUiComplete: () -> Unit, onWorkerBarrier: () -> Unit = {}): Boolean {
        val target = synchronized(taskLock) { submittedTaskId }
        return submitBg {
            // The pool has several workers, so a plain FIFO queue barrier is insufficient:
            // an earlier task may still be running on another worker. Wait for the completion
            // prefix captured above. Every provider task posts its UI callback before marking
            // itself complete, so this UI callback is guaranteed to be queued after them all.
            synchronized(taskLock) {
                while (completedTaskPrefix < target) taskLock.wait()
            }
            ui { onUiComplete() }
            onWorkerBarrier()
        }
    }

    private fun markTaskCompleted(id: Long) {
        synchronized(taskLock) {
            if (id == completedTaskPrefix + 1L) {
                completedTaskPrefix = id
                while (completedOutOfOrder.remove(completedTaskPrefix + 1L)) completedTaskPrefix++
            } else if (id > completedTaskPrefix) {
                completedOutOfOrder.add(id)
            }
            taskLock.notifyAll()
        }
    }

    /** Submit background work and report queue saturation to callers that need a completion callback. */
    fun submitBg(block: () -> Unit): Boolean {
        val id = synchronized(taskLock) { ++submittedTaskId }
        return try {
            pool.execute {
                try { block() } catch (_: Throwable) { /* isolate background failures */ }
                finally { markTaskCompleted(id) }
            }
            true
        } catch (_: RejectedExecutionException) {
            // Mark the rejected sequence as complete so later barriers cannot deadlock.
            markTaskCompleted(id)
            // Never run network work on the UI thread when the queue is full.
            false
        }
    }

    fun bgImage(block: () -> Unit) {
        try {
            imagePool.execute {
                try { block() } catch (_: Throwable) { /* isolate image failures */ }
            }
        } catch (_: RejectedExecutionException) {
            // Image work is best-effort; the placeholder remains visible.
        }
    }

    fun ui(block: () -> Unit) {
        main.post { block() }
    }

    /** يتبع التحويلات حتى بين http و https مع إغلاق الاتصال دائماً. */
    fun getBytes(url: String, timeoutMs: Int = 10000, ua: String = UA_BROWSER, referer: String? = null, maxBytes: Int = 8 * 1024 * 1024, headers: Map<String, String> = emptyMap()): ByteArray {
        require(url.startsWith("https://", true) || url.startsWith("http://", true)) { "unsupported URL scheme" }
        require(maxBytes in 1..(24 * 1024 * 1024)) { "invalid response size limit" }
        val safeTimeout = timeoutMs.coerceIn(2_000, 15_000)
        var current = url
        var hops = 0
        val originHost = URL(url).host
        while (true) {
            val conn = URL(current).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = safeTimeout
                conn.readTimeout = safeTimeout
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", ua)
                conn.setRequestProperty("Accept", "application/json,image/png,image/jpeg,image/webp,*/*;q=0.8")
                conn.setRequestProperty("Accept-Language", "ar,en;q=0.8")
                if (!referer.isNullOrBlank()) conn.setRequestProperty("Referer", referer)
                // رؤوس مثل Authorization لا تُرسل إلا للمضيف الأصلي، فلا يتسرب مفتاح API عبر تحويلة لمضيف آخر
                val sendCustomHeaders = URL(current).host.equals(originHost, ignoreCase = true)
                for ((name, value) in (if (sendCustomHeaders) headers else emptyMap())) {
                    // لا يسمح بتمرير رؤوس تحكم الاتصال أو المضيف من شاشة الإعدادات.
                    if (name.matches(Regex("[A-Za-z0-9-]{1,60}")) && !name.equals("host", true) && !name.equals("connection", true)) {
                        conn.setRequestProperty(name, value.take(2048))
                    }
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location")
                    if (loc == null || hops >= 6) throw IOException("redirect loop")
                    current = URL(URL(current), loc).toString()
                    if (!current.startsWith("https://", true) && !current.startsWith("http://", true)) {
                        throw IOException("unsupported redirect scheme")
                    }
                    hops++
                    continue
                }
                if (code !in 200..299) throw IOException("HTTP $code")
                val advertised = conn.contentLengthLong
                if (advertised > maxBytes) throw IOException("response too large")
                return conn.inputStream.use { input ->
                    val out = ByteArrayOutputStream(minOf(maxBytes, 32 * 1024))
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) throw IOException("response too large")
                        out.write(buffer, 0, n)
                    }
                    out.toByteArray()
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    /**
     * Stream a large playlist to disk without allocating its full response in RAM.
     * Redirects remain HTTP(S)-only and the byte ceiling applies even without Content-Length.
     */
    fun downloadToFile(
        url: String,
        destination: File,
        timeoutMs: Int = 30_000,
        maxBytes: Long = 64L * 1024L * 1024L,
        ua: String = UA_BROWSER,
        referer: String? = null
    ): Long {
        require(url.startsWith("https://", true) || url.startsWith("http://", true)) { "unsupported URL scheme" }
        require(maxBytes in 1L..(128L * 1024L * 1024L)) { "invalid response size limit" }
        val parent = destination.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw IOException("cannot create download directory")
        val safeTimeout = timeoutMs.coerceIn(2_000, 60_000)
        var current = url
        // رابط مشاركة MediaFire صفحة HTML؛ نقرأ رابط التنزيل المباشر منها ثم ننزّل الملف نفسه.
        if (com.uobidah.launcher.core.MediaFireLink.isLandingPage(url)) {
            val page = String(getBytes(url, timeoutMs = 20_000, maxBytes = 2 * 1024 * 1024), Charsets.UTF_8)
            current = com.uobidah.launcher.core.MediaFireLink.directUrl(page)
                ?: throw IOException("رابط MediaFire لا يحتوي زر تنزيل مباشر (قد يكون الملف محذوفاً)")
        }
        var hops = 0
        destination.delete()
        try {
            while (true) {
                val conn = URL(current).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = safeTimeout
                    conn.readTimeout = safeTimeout
                    conn.instanceFollowRedirects = false
                    conn.setRequestProperty("User-Agent", ua)
                    conn.setRequestProperty("Accept", "application/x-mpegURL,audio/x-mpegurl,text/plain,*/*;q=0.8")
                    conn.setRequestProperty("Accept-Language", "ar,en;q=0.8")
                    if (!referer.isNullOrBlank()) conn.setRequestProperty("Referer", referer)
                    val code = conn.responseCode
                    if (code in 300..399) {
                        val location = conn.getHeaderField("Location") ?: throw IOException("redirect without location")
                        if (hops >= 6) throw IOException("redirect loop")
                        current = URL(URL(current), location).toString()
                        if (!current.startsWith("https://", true) && !current.startsWith("http://", true)) {
                            throw IOException("unsupported redirect scheme")
                        }
                        hops++
                        continue
                    }
                    if (code !in 200..299) throw IOException("HTTP $code")
                    val advertised = conn.contentLengthLong
                    if (advertised > maxBytes) throw IOException("playlist exceeds the 64 MB safety limit")
                    var total = 0L
                    conn.inputStream.use { input ->
                        FileOutputStream(destination).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > maxBytes) throw IOException("playlist exceeds the 64 MB safety limit")
                                output.write(buffer, 0, count)
                            }
                            output.fd.sync()
                        }
                    }
                    if (total <= 0L) throw IOException("empty response")
                    return total
                } finally {
                    conn.disconnect()
                }
            }
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    fun getString(url: String, timeoutMs: Int = 15000, ua: String = UA_BROWSER): String =
        String(getBytes(url, timeoutMs, ua), Charsets.UTF_8)
}

/** تحميل صور محدود الذاكرة مع كاش ملفات، مناسب للأجهزة ذات 1GB RAM. */
object Img {
    private val maxMemoryCacheBytes: Int = minOf(6 * 1024 * 1024, (Runtime.getRuntime().maxMemory() / 32).toInt().coerceAtLeast(1 * 1024 * 1024))
    private val cache = object : LruCache<String, Bitmap>(maxMemoryCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private const val MAX_DISK_CACHE_BYTES = 16L * 1024L * 1024L
    private val fileCache = object : LruCache<String, Bitmap>(maxMemoryCacheBytes / 2) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    @Volatile private var fileDecodeCount = 0L
    @Volatile private var fileCacheHits = 0L

    fun fileCacheStats(): Pair<Long, Long> = fileDecodeCount to fileCacheHits

    private fun sample(w: Int, h: Int, reqW: Int, reqH: Int): Int {
        if (w <= 0 || h <= 0) return 1
        var s = 1
        while (w / (s * 2) >= reqW.coerceAtLeast(1) && h / (s * 2) >= reqH.coerceAtLeast(1)) s *= 2
        return s
    }

    fun decode(bytes: ByteArray, reqW: Int, reqH: Int): Bitmap? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample(bounds.outWidth, bounds.outHeight, reqW, reqH)
            // Team badges commonly contain transparent backgrounds; RGB_565 would erase alpha.
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    /** يفك ملف صورة محلي بعينة تناسب الحجم المطلوب (خلفية الرئيسية). يعيد null إن تعذر. */
    fun decodeFile(file: File, reqW: Int, reqH: Int): Bitmap? {
        return try {
            if (!file.exists() || file.length() <= 0L) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample(bounds.outWidth, bounds.outHeight, reqW, reqH)
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (_: Throwable) {
            null
        }
    }

    private fun cacheFile(context: Context, url: String): File {
        // شعارات الأندية في مجلد دائم (filesDir): لا يمسحه النظام ولا يُقصّ مع الكاش، فتبقى ظاهرة بعد قطع الإنترنت
        val dir = if (url.startsWith("https://raw.githubusercontent.com/")) File(context.filesDir, "uobidah_club_logos")
        else File(context.cacheDir, "uobidah_image_cache")
        if (!dir.exists()) dir.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        val name = digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(dir, name + ".img")
    }

    /** يحرر كاش الصور داخل اللانشر فقط دون إيقاف تطبيقات النظام أو مسح الملفات المحفوظة. */
    fun clearMemoryCache(): Int {
        val entries = try { cache.snapshot().size + fileCache.snapshot().size } catch (_: Exception) { 0 }
        try { cache.evictAll() } catch (_: Exception) { }
        try { fileCache.evictAll() } catch (_: Exception) { }
        return entries
    }

    private fun trimDiskCache(dir: File) {
        if (dir.name == "uobidah_club_logos") return
        try {
            val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
            var total = files.sumOf { it.length() }
            for (f in files) {
                if (total <= MAX_DISK_CACHE_BYTES) break
                val size = f.length()
                if (f.delete()) total -= size
            }
        } catch (_: Exception) { }
    }

    /** فك صورة محلية مع كاش ذاكرة؛ يمنع إعادة فك الخلفية في كل إعادة بناء للثيم. */
    fun loadFile(file: File, view: ImageView, reqW: Int, reqH: Int, onLoaded: (() -> Unit)? = null) {
        val key = "file:${file.absolutePath}:${file.lastModified()}:${file.length()}:$reqW x $reqH"
        view.tag = key
        if (!file.isFile || file.length() <= 0L) { view.setImageDrawable(null); return }
        val hit = fileCache.get(key)
        if (hit != null) {
            fileCacheHits++
            view.setImageBitmap(hit)
            onLoaded?.invoke()
            return
        }
        view.setImageDrawable(null)
        Net.bgImage {
            val bitmap = decodeFile(file, reqW, reqH)
            if (bitmap != null) {
                fileDecodeCount++
                fileCache.put(key, bitmap)
                Net.ui {
                    if (view.tag == key) {
                        view.setImageBitmap(bitmap)
                        onLoaded?.invoke()
                    }
                }
            }
        }
    }

    /** مواقع الصور ترفض Referer غريب؛ نرسل Referer موقع الصورة نفسه (كان سبب غياب شعارات YSscores وSofaScore). */
    private fun refererFor(url: String): String {
        val u = url.lowercase()
        return when {
            u.contains("ysscores") -> "https://www.ysscores.com/"
            u.contains("sofascore") -> "https://www.sofascore.com/"
            else -> "https://www.thesportsdb.com/"
        }
    }

    fun load(context: Context, url: String, view: ImageView, reqW: Int, reqH: Int, onError: (() -> Unit)? = null, onLoaded: (() -> Unit)? = null) {
        val cacheKey = url + "|" + reqW + "x" + reqH
        // حارس التحميل في وسم مستقل: View.tag يبقى لمالك الواجهة (مثل channel-logo) ولا يُكتب فوقه.
        view.setTag(R.id.img_load_key, cacheKey)
        if (url.isBlank() || !(url.startsWith("https://", true) || url.startsWith("http://", true))) return
        val hit = cache.get(cacheKey)
        if (hit != null) {
            view.setImageBitmap(hit)
            onLoaded?.invoke()
            return
        }
        view.setImageDrawable(null)
        val app = context.applicationContext
        Net.bgImage {
            try {
                val file = cacheFile(app, url)
                var bytes: ByteArray? = null
                var bmp: Bitmap? = null
                if (file.isFile && file.length() in 1..(8L * 1024L * 1024L)) {
                    bytes = try { file.readBytes() } catch (_: Exception) { null }
                    if (bytes != null) bmp = decode(bytes!!, reqW, reqH)
                    if (bmp == null) {
                        // امسح كاش صورة تالفاً ولا تتركه يمنع إعادة تنزيل الشعار السليم.
                        file.delete()
                        bytes = null
                    }
                } else if (file.exists()) {
                    file.delete()
                }
                if (bytes == null) {
                    val downloaded = Net.getBytes(url, 12000, referer = refererFor(url), maxBytes = 8 * 1024 * 1024)
                    if (downloaded.isEmpty() || downloaded.size > 8 * 1024 * 1024) throw IOException("image size rejected")
                    bmp = decode(downloaded, reqW, reqH) ?: throw IOException("unsupported or corrupt image")
                    val tmp = File(file.parentFile, file.name + ".tmp")
                    try {
                        FileOutputStream(tmp).use { it.write(downloaded) }
                        if (!tmp.renameTo(file)) tmp.delete()
                    } catch (e: Exception) {
                        tmp.delete()
                    }
                    trimDiskCache(file.parentFile!!)
                    bytes = downloaded
                }
                val imageBitmap = bmp ?: decode(bytes!!, reqW, reqH) ?: return@bgImage
                cache.put(cacheKey, imageBitmap)
                Net.ui {
                    if (view.getTag(R.id.img_load_key) == cacheKey) {
                        view.setImageBitmap(imageBitmap)
                        onLoaded?.invoke()
                    }
                }
            } catch (_: Exception) {
                // يظل عنصر الشعار الاحتياطي ظاهراً عند تعذّر الاتصال أو الرابط، ويُعلِم المالك إن طلب ذلك.
                if (onError != null) Net.ui { if (view.getTag(R.id.img_load_key) == cacheKey) onError() }
            }
        }
    }
}

/** شارة فريق احتياطية واضحة حتى عند غياب الشعار أو انقطاع الإنترنت. */
object TeamBadgeUi {
    fun create(context: Context, teamName: String, badgeUrl: String, sizeDp: Int, accent: Int): View {
        val frame = FrameLayout(context)
        val size = (sizeDp * context.resources.displayMetrics.density + 0.5f).toInt()
        val fallback = TextView(context)
        fallback.text = teamName.trim().firstOrNull()?.toString() ?: "⚽"
        fallback.textSize = (sizeDp * 0.34f).coerceIn(20f, 34f)
        fallback.setTextColor(Color.WHITE)
        fallback.gravity = Gravity.CENTER
        fallback.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(25, 38, 58))
            setStroke((2 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1), (accent and 0x00FFFFFF) or (0xAA shl 24))
        }
        val image = ImageView(context)
        // الشعار كاملاً: FIT_CENTER مع هامش صغير حتى لا تُقصّ الحواف، وبدون أي قناع دائري
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        image.adjustViewBounds = true
        val pad = (size * 0.05f).toInt()
        image.setPadding(pad, pad, pad, pad)
        image.alpha = 0f
        frame.clipChildren = false
        frame.clipToPadding = false
        frame.addView(fallback, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        frame.addView(image, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        if (badgeUrl.isNotBlank()) {
            // نفك الصورة بضعف المقاس لشعار حاد (الشاشات الكبيرة كانت تُظهره مبهماً/متآكلاً)
            val px2 = (size * 2).coerceIn(96, 512)
            Img.load(context, badgeUrl, image, px2, px2) {
                fallback.visibility = View.GONE
                if (Motion.enabled(context)) {
                    image.scaleX = 0.7f
                    image.scaleY = 0.7f
                    image.alpha = 0f
                    image.animate().cancel()
                    image.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420L)
                        .setInterpolator(android.view.animation.OvershootInterpolator(1.5f)).start()
                } else {
                    image.alpha = 1f
                }
            }
        }
        frame.layoutParams = FrameLayout.LayoutParams(size, size)
        return frame
    }
}

