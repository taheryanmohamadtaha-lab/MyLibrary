package com.mylibrary.pdf.data

import java.text.Collator
import java.util.Locale
import kotlin.math.roundToInt

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class SortOrder { NEWEST, NAME, LAST_READ }
enum class ViewMode { GRID, LIST }

/** منطق خالص کتابخانه: پیشرفت، فیلتر و مرتب‌سازی. */
object LibraryLogic {

    fun progressPercent(lastPage: Int, pageCount: Int, hasRead: Boolean): Int {
        if (!hasRead || pageCount <= 0) return 0
        val p = ((lastPage + 1).toDouble() / pageCount * 100).roundToInt()
        return p.coerceIn(0, 100)
    }

    fun progressOf(b: BookEntity): Int = progressPercent(b.lastPage, b.pageCount, b.lastReadAt != null)

    fun sort(books: List<BookEntity>, order: SortOrder): List<BookEntity> = when (order) {
        SortOrder.NEWEST -> books.sortedByDescending { it.addedAt }
        SortOrder.NAME -> {
            val collator = Collator.getInstance(Locale("fa"))
            books.sortedWith { a, b -> collator.compare(a.title, b.title) }
        }
        SortOrder.LAST_READ -> books.sortedWith(
            compareByDescending<BookEntity> { it.lastReadAt ?: 0L }.thenByDescending { it.addedAt },
        )
    }

    fun filter(
        books: List<BookEntity>,
        query: String,
        category: String?,
        favoritesOnly: Boolean,
    ): List<BookEntity> {
        val q = query.trim()
        return books.filter { b ->
            (!favoritesOnly || b.favorite) &&
                (category == null || b.category == category) &&
                (q.isEmpty() || b.title.contains(q, ignoreCase = true))
        }
    }

    fun categories(books: List<BookEntity>): List<String> =
        books.mapNotNull { it.category?.trim()?.takeIf { c -> c.isNotEmpty() } }.distinct().sorted()

    /** کتاب‌هایی که شروع شده‌اند ولی تمام نشده‌اند، جدیدترین مطالعه اول. */
    fun continueReading(books: List<BookEntity>, limit: Int = 8): List<BookEntity> =
        books.filter { it.lastReadAt != null && progressOf(it) < 100 }
            .sortedByDescending { it.lastReadAt }
            .take(limit)
}
