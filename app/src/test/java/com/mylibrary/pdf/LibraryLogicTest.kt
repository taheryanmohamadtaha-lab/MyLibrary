package com.mylibrary.pdf

import com.mylibrary.pdf.data.BookEntity
import com.mylibrary.pdf.data.LibraryLogic
import com.mylibrary.pdf.data.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryLogicTest {

    private fun book(
        id: Long,
        title: String,
        added: Long = id,
        lastRead: Long? = null,
        page: Int = 0,
        pages: Int = 100,
        fav: Boolean = false,
        cat: String? = null,
    ) = BookEntity(
        id = id, title = title, fileName = "$id.pdf", coverFileName = null, hash = "h$id",
        pageCount = pages, sizeBytes = 1000, addedAt = added, lastReadAt = lastRead,
        lastPage = page, favorite = fav, category = cat,
    )

    @Test fun progress_notStarted_isZero() {
        assertEquals(0, LibraryLogic.progressPercent(0, 100, hasRead = false))
    }

    @Test fun progress_firstPageOpened() {
        assertEquals(1, LibraryLogic.progressPercent(0, 100, hasRead = true))
    }

    @Test fun progress_lastPage_is100() {
        assertEquals(100, LibraryLogic.progressPercent(99, 100, hasRead = true))
    }

    @Test fun progress_isClamped_andSafeForZeroPages() {
        assertEquals(100, LibraryLogic.progressPercent(500, 100, hasRead = true))
        assertEquals(0, LibraryLogic.progressPercent(3, 0, hasRead = true))
    }

    @Test fun sort_newest_firstByAddedAtDescending() {
        val r = LibraryLogic.sort(listOf(book(1, "a", added = 10), book(2, "b", added = 30), book(3, "c", added = 20)), SortOrder.NEWEST)
        assertEquals(listOf(2L, 3L, 1L), r.map { it.id })
    }

    @Test fun sort_name_isAlphabetical() {
        val r = LibraryLogic.sort(listOf(book(1, "Zebra"), book(2, "Apple"), book(3, "Mango")), SortOrder.NAME)
        assertEquals(listOf("Apple", "Mango", "Zebra"), r.map { it.title })
    }

    @Test fun sort_lastRead_unreadBooksGoLast() {
        val r = LibraryLogic.sort(
            listOf(book(1, "a", lastRead = null), book(2, "b", lastRead = 500), book(3, "c", lastRead = 900)),
            SortOrder.LAST_READ,
        )
        assertEquals(listOf(3L, 2L, 1L), r.map { it.id })
    }

    @Test fun filter_byTitle_isCaseInsensitive() {
        val r = LibraryLogic.filter(listOf(book(1, "Kotlin Guide"), book(2, "Java")), "kotlin", null, false)
        assertEquals(listOf(1L), r.map { it.id })
    }

    @Test fun filter_favoritesAndCategory() {
        val list = listOf(
            book(1, "a", fav = true, cat = "رمان"),
            book(2, "b", fav = true, cat = "درسی"),
            book(3, "c", fav = false, cat = "رمان"),
        )
        assertEquals(listOf(1L, 2L), LibraryLogic.filter(list, "", null, true).map { it.id })
        assertEquals(listOf(1L, 3L), LibraryLogic.filter(list, "", "رمان", false).map { it.id })
        assertEquals(listOf(1L), LibraryLogic.filter(list, "", "رمان", true).map { it.id })
    }

    @Test fun continueReading_excludesUnstartedAndFinished() {
        val list = listOf(
            book(1, "unstarted"),
            book(2, "finished", lastRead = 100, page = 99),
            book(3, "reading-old", lastRead = 200, page = 10),
            book(4, "reading-new", lastRead = 300, page = 10),
        )
        assertEquals(listOf(4L, 3L), LibraryLogic.continueReading(list).map { it.id })
    }

    @Test fun categories_areDistinctAndSorted() {
        val list = listOf(book(1, "a", cat = "ب"), book(2, "b", cat = "الف"), book(3, "c", cat = "ب"), book(4, "d", cat = " "))
        assertEquals(listOf("الف", "ب"), LibraryLogic.categories(list))
    }

    @Test fun libraryEmpty_returnsEmptyLists() {
        assertTrue(LibraryLogic.sort(emptyList(), SortOrder.NAME).isEmpty())
        assertTrue(LibraryLogic.continueReading(emptyList()).isEmpty())
    }
}
