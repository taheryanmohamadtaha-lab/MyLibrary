package com.mylibrary.pdf.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mylibrary.pdf.pdf.PdfUtil
import com.mylibrary.pdf.util.Hashing
import com.mylibrary.pdf.util.sanitizeFileName
import com.mylibrary.pdf.util.titleFromFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

sealed class ImportResult {
    data class Added(val book: BookEntity) : ImportResult()
    data class Duplicate(val book: BookEntity) : ImportResult()
    data class Failed(val name: String, val reason: String) : ImportResult()
}

data class RestoreSummary(val added: Int, val updated: Int, val skipped: Int)

class BookRepository(private val context: Context, private val dao: BookDao) {

    companion object {
        const val MAX_FILE_BYTES = 500L * 1024 * 1024
        const val MSG_PASSWORD =
            "این PDF رمزدار است. موتور PDF اندروید (PdfRenderer) از PDFهای رمزدار پشتیبانی نمی‌کند؛ ابتدا رمز را از فایل بردارید."
        const val MSG_CORRUPT = "فایل خراب یا نامعتبر است و قابل باز شدن نیست."
        const val MSG_NO_SPACE = "فضای خالی دستگاه برای افزودن این فایل کافی نیست."
        private val ZIP_BOOK = Regex("books/[0-9a-f]{64}\\.pdf")
    }

    private val booksDir = File(context.filesDir, "books").also { it.mkdirs() }
    private val coversDir = File(context.filesDir, "covers").also { it.mkdirs() }
    private val shareDir = File(context.cacheDir, "share").also { it.mkdirs() }
    private val coverCache = LruCache<String, ImageBitmap>(64)

    val books: Flow<List<BookEntity>> = dao.observeAll()

    fun bookFile(b: BookEntity): File = File(booksDir, b.fileName)

    suspend fun getBook(id: Long): BookEntity? = dao.get(id)

    // ---------------------------------------------------------------- ورود

    suspend fun importUri(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val (displayName, declaredSize) = queryMeta(uri)
        val title = titleFromFileName(displayName)
        if (declaredSize > MAX_FILE_BYTES) {
            return@withContext ImportResult.Failed(title, "حجم فایل بیش از حد مجاز (۵۰۰ مگابایت) است.")
        }
        if (!hasSpace(maxOf(declaredSize, 0L))) {
            return@withContext ImportResult.Failed(title, MSG_NO_SPACE)
        }
        val tmp = File(booksDir, "import_${System.nanoTime()}.tmp")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult.Failed(title, "خواندن فایل ممکن نشد.")
            val hash = input.use { ins -> tmp.outputStream().use { out -> Hashing.copyAndHash(ins, out) } }
            if (tmp.length() > MAX_FILE_BYTES) {
                return@withContext ImportResult.Failed(title, "حجم فایل بیش از حد مجاز (۵۰۰ مگابایت) است.")
            }
            ingest(tmp, hash, title)
        } catch (e: SecurityException) {
            ImportResult.Failed(title, "دسترسی به فایل ممکن نشد.")
        } catch (e: IOException) {
            ImportResult.Failed(title, if (isNoSpace(e)) MSG_NO_SPACE else "خطا در خواندن یا کپی فایل.")
        } finally {
            tmp.delete()
        }
    }

    /** فایل موقت معتبر را به کتابخانه می‌افزاید (بعد از کپی و هش). */
    private suspend fun ingest(tmp: File, hash: String, title: String): ImportResult {
        val existing = dao.getByHash(hash)
        if (existing != null) {
            if (existing.deletedAt == null) return ImportResult.Duplicate(existing)
            purge(existing)
        }
        val pages = try {
            PdfUtil.pageCount(tmp)
        } catch (e: SecurityException) {
            return ImportResult.Failed(title, MSG_PASSWORD)
        } catch (e: Exception) {
            return ImportResult.Failed(title, MSG_CORRUPT)
        }
        if (pages <= 0) return ImportResult.Failed(title, MSG_CORRUPT)

        val base = UUID.randomUUID().toString()
        val finalFile = File(booksDir, "$base.pdf")
        if (!tmp.renameTo(finalFile)) {
            try {
                tmp.copyTo(finalFile, overwrite = true)
            } catch (e: IOException) {
                finalFile.delete()
                return ImportResult.Failed(title, if (isNoSpace(e)) MSG_NO_SPACE else "ذخیره فایل ممکن نشد.")
            }
        }
        val coverName = "$base.jpg"
        val hasCover = PdfUtil.renderCover(finalFile, File(coversDir, coverName))
        val entity = BookEntity(
            title = title,
            fileName = finalFile.name,
            coverFileName = if (hasCover) coverName else null,
            hash = hash,
            pageCount = pages,
            sizeBytes = finalFile.length(),
            addedAt = System.currentTimeMillis(),
        )
        val id = dao.insert(entity)
        return ImportResult.Added(entity.copy(id = id))
    }

    private fun queryMeta(uri: Uri): Pair<String?, Long> {
        var name: String? = null
        var size = -1L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        } catch (_: Exception) {
        }
        if (name == null) name = uri.lastPathSegment
        return name to size
    }

    private fun hasSpace(needed: Long): Boolean = try {
        StatFs(context.filesDir.path).availableBytes > needed + 20L * 1024 * 1024
    } catch (_: Exception) {
        true
    }

    private fun isNoSpace(e: IOException): Boolean {
        val m = e.message ?: return false
        return m.contains("ENOSPC") || m.contains("No space", ignoreCase = true)
    }

    // ---------------------------------------------------------------- جلد

    suspend fun loadCover(book: BookEntity): ImageBitmap? {
        val name = book.coverFileName ?: return null
        coverCache.get(name)?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = File(coversDir, name)
            if (!f.exists()) {
                null
            } else {
                BitmapFactory.decodeFile(f.path)?.asImageBitmap()?.also { coverCache.put(name, it) }
            }
        }
    }

    // ---------------------------------------------------------------- مدیریت کتاب

    suspend fun updateMeta(id: Long, title: String, category: String?) {
        val b = dao.get(id) ?: return
        dao.update(
            b.copy(
                title = title.trim().ifEmpty { b.title },
                category = category?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }

    suspend fun setFavorite(id: Long, fav: Boolean) = dao.setFavorite(id, fav)

    suspend fun saveProgress(id: Long, page: Int) = dao.saveProgress(id, page.coerceAtLeast(0), System.currentTimeMillis())

    suspend fun softDelete(id: Long) = dao.setDeleted(id, System.currentTimeMillis())

    suspend fun undoDelete(id: Long) = dao.setDeleted(id, null)

    suspend fun purgeById(id: Long) {
        val b = dao.get(id) ?: return
        if (b.deletedAt != null) purge(b)
    }

    private suspend fun purge(b: BookEntity) = withContext(Dispatchers.IO) {
        File(booksDir, b.fileName).delete()
        b.coverFileName?.let { File(coversDir, it).delete(); coverCache.remove(it) }
        dao.deleteById(b.id)
    }

    suspend fun purgeDeleted() {
        dao.getDeleted().forEach { purge(it) }
        // پاکسازی فایل‌های موقت نیمه‌کاره
        booksDir.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { it.delete() }
    }

    /** کپی با نام خوانا در cache برای اشتراک‌گذاری. */
    suspend fun prepareShareFile(b: BookEntity): File = withContext(Dispatchers.IO) {
        shareDir.listFiles()?.forEach { it.delete() }
        val out = File(shareDir, sanitizeFileName(b.title) + ".pdf")
        bookFile(b).copyTo(out, overwrite = true)
        out
    }

    // ---------------------------------------------------------------- نشانک

    fun bookmarks(bookId: Long): Flow<List<BookmarkEntity>> = dao.observeBookmarks(bookId)

    suspend fun toggleBookmark(bookId: Long, page: Int) {
        val ex = dao.getBookmark(bookId, page)
        if (ex != null) dao.deleteBookmark(ex.id)
        else dao.insertBookmark(BookmarkEntity(bookId = bookId, page = page, createdAt = System.currentTimeMillis()))
    }

    suspend fun deleteBookmark(id: Long) = dao.deleteBookmark(id)

    // ---------------------------------------------------------------- پشتیبان

    suspend fun exportBackup(uri: Uri, withFiles: Boolean) = withContext(Dispatchers.IO) {
        val list = dao.getAllNow()
        val byId = list.associateBy { it.id }
        val json = JSONObject()
        json.put("version", 1)
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(
                JSONObject()
                    .put("title", b.title).put("hash", b.hash).put("pageCount", b.pageCount)
                    .put("sizeBytes", b.sizeBytes).put("addedAt", b.addedAt)
                    .put("lastReadAt", b.lastReadAt ?: JSONObject.NULL)
                    .put("lastPage", b.lastPage).put("favorite", b.favorite)
                    .put("category", b.category ?: JSONObject.NULL),
            )
        }
        json.put("books", arr)
        val bm = JSONArray()
        dao.getAllBookmarks().forEach { m ->
            val b = byId[m.bookId] ?: return@forEach
            bm.put(JSONObject().put("hash", b.hash).put("page", m.page).put("createdAt", m.createdAt))
        }
        json.put("bookmarks", bm)

        val os = context.contentResolver.openOutputStream(uri) ?: throw IOException("cannot open output")
        ZipOutputStream(BufferedOutputStream(os)).use { zip ->
            zip.putNextEntry(ZipEntry("library.json"))
            zip.write(json.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            if (withFiles) {
                list.forEach { b ->
                    val f = bookFile(b)
                    if (f.exists()) {
                        zip.putNextEntry(ZipEntry("books/${b.hash}.pdf"))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    suspend fun restoreBackup(uri: Uri): RestoreSummary = withContext(Dispatchers.IO) {
        var meta: JSONObject? = null
        var added = 0
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("cannot open input")
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (name == "library.json") {
                    meta = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                } else if (ZIP_BOOK.matches(name)) {
                    val tmp = File(booksDir, "restore_${System.nanoTime()}.tmp")
                    try {
                        val hash = tmp.outputStream().use { Hashing.copyAndHash(zip, it) }
                        val title = meta?.optJSONArray("books")?.let { a ->
                            (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optString("hash") == hash }
                        }?.optString("title")?.takeIf { it.isNotEmpty() } ?: "کتاب بازیابی‌شده"
                        if (ingest(tmp, hash, title) is ImportResult.Added) added++
                    } finally {
                        tmp.delete()
                    }
                }
                zip.closeEntry()
            }
        }
        val m = meta ?: throw IOException("library.json not found")
        var updated = 0
        var skipped = 0
        val arr = m.optJSONArray("books") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val b = dao.getByHash(o.optString("hash"))
            if (b == null || b.deletedAt != null) {
                skipped++
                continue
            }
            dao.update(
                b.copy(
                    title = o.optString("title").ifEmpty { b.title },
                    favorite = o.optBoolean("favorite", b.favorite),
                    category = if (o.isNull("category")) null else o.optString("category").ifEmpty { null },
                    lastPage = o.optInt("lastPage", b.lastPage).coerceIn(0, (b.pageCount - 1).coerceAtLeast(0)),
                    lastReadAt = if (o.isNull("lastReadAt")) null else o.optLong("lastReadAt"),
                ),
            )
            updated++
        }
        val bms = m.optJSONArray("bookmarks") ?: JSONArray()
        for (i in 0 until bms.length()) {
            val o = bms.getJSONObject(i)
            val b = dao.getByHash(o.optString("hash")) ?: continue
            val page = o.optInt("page", -1)
            if (page < 0 || b.deletedAt != null || dao.getBookmark(b.id, page) != null) continue
            dao.insertBookmark(BookmarkEntity(bookId = b.id, page = page, createdAt = o.optLong("createdAt")))
        }
        RestoreSummary(added, updated, skipped)
    }
}
