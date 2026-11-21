package com.mylibrary.pdf.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import kotlin.math.sqrt

object PdfUtil {
    private const val MAX_PIXELS = 16_000_000L

    /** تعداد صفحه؛ برای PDF رمزدار SecurityException و برای خراب IOException می‌دهد. */
    fun pageCount(file: File): Int {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val r = PdfRenderer(pfd)
            try {
                return r.pageCount
            } finally {
                r.close()
            }
        } finally {
            pfd.close()
        }
    }

    fun renderPage(renderer: PdfRenderer, index: Int, targetWidth: Int): Bitmap {
        val page = renderer.openPage(index)
        try {
            var w = targetWidth.coerceAtLeast(1)
            var h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
            if (w.toLong() * h > MAX_PIXELS) {
                val f = sqrt(MAX_PIXELS.toDouble() / (w.toLong() * h))
                w = (w * f).toInt().coerceAtLeast(1)
                h = (h * f).toInt().coerceAtLeast(1)
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            val m = Matrix()
            m.setScale(w.toFloat() / page.width, h.toFloat() / page.height)
            page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        } finally {
            page.close()
        }
    }

    fun renderCover(pdf: File, out: File, width: Int = 480): Boolean = try {
        val pfd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val r = PdfRenderer(pfd)
            try {
                val bmp = renderPage(r, 0, width)
                out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                bmp.recycle()
                true
            } finally {
                r.close()
            }
        } finally {
            pfd.close()
        }
    } catch (e: Throwable) {
        false
    }
}

/**
 * یک نشست خواندن: PdfRenderer فقط یک صفحه را همزمان باز می‌گذارد، پس همه‌چیز پشت Mutex است.
 * کش LRU بر اساس حجم بایت‌ها محدود شده تا OOM رخ ندهد.
 */
class PdfSession(file: File) : Closeable {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = try {
        PdfRenderer(pfd)
    } catch (e: Throwable) {
        pfd.close()
        throw e
    }
    private val mutex = Mutex()

    @Volatile private var closed = false
    val pageCount: Int = renderer.pageCount

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 6).toInt().coerceAtLeast(8 * 1024),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /** نسبت ارتفاع به عرض همه صفحه‌ها، برای چیدمان بدون پرش. */
    suspend fun loadRatios(): List<Float> = withContext(Dispatchers.Default) {
        val out = ArrayList<Float>(pageCount)
        var last = 1.414f
        for (i in 0 until pageCount) {
            ensureActive()
            val r = mutex.withLock {
                if (closed) {
                    last
                } else {
                    try {
                        val p = renderer.openPage(i)
                        try {
                            if (p.width > 0) p.height.toFloat() / p.width else last
                        } finally {
                            p.close()
                        }
                    } catch (e: Exception) {
                        last
                    }
                }
            }
            last = r
            out.add(r)
        }
        out
    }

    suspend fun render(page: Int, width: Int): Bitmap? = withContext(Dispatchers.Default) {
        val key = "$page@$width"
        val hit = cache.get(key)
        if (hit != null) {
            hit
        } else {
            mutex.withLock {
                val again = cache.get(key)
                if (again != null) {
                    again
                } else if (closed) {
                    null
                } else {
                    try {
                        val bmp = PdfUtil.renderPage(renderer, page, width)
                        cache.put(key, bmp)
                        bmp
                    } catch (e: Throwable) {
                        if (e is OutOfMemoryError) cache.evictAll()
                        null
                    }
                }
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        // بستن پشت Mutex تا وسط رندر نباشد
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                cache.evictAll()
                try { renderer.close() } catch (_: Throwable) {}
                try { pfd.close() } catch (_: Throwable) {}
            }
        }
    }
}
