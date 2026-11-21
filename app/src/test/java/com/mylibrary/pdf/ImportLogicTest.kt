package com.mylibrary.pdf

import com.mylibrary.pdf.util.Hashing
import com.mylibrary.pdf.util.formatSize
import com.mylibrary.pdf.util.sanitizeFileName
import com.mylibrary.pdf.util.titleFromFileName
import com.mylibrary.pdf.util.toPersianDigits
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ImportLogicTest {

    @Test fun hash_sameContent_sameHash_forDuplicateDetection() {
        val data = ByteArray(200_000) { (it % 251).toByte() }
        val out1 = ByteArrayOutputStream()
        val out2 = ByteArrayOutputStream()
        val h1 = Hashing.copyAndHash(ByteArrayInputStream(data), out1)
        val h2 = Hashing.copyAndHash(ByteArrayInputStream(data), out2)
        assertEquals(h1, h2)
        assertEquals(64, h1.length)
        assertArrayEquals(data, out1.toByteArray())
    }

    @Test fun hash_differentContent_differentHash() {
        val a = Hashing.copyAndHash(ByteArrayInputStream(byteArrayOf(1, 2, 3)), ByteArrayOutputStream())
        val b = Hashing.copyAndHash(ByteArrayInputStream(byteArrayOf(1, 2, 4)), ByteArrayOutputStream())
        assertNotEquals(a, b)
    }

    @Test fun hash_knownVector_emptyInput() {
        val h = Hashing.copyAndHash(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream())
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", h)
    }

    @Test fun title_stripsPdfExtension_andUnderscores() {
        assertEquals("my book", titleFromFileName("my_book.PDF"))
        assertEquals("کتاب بدون نام", titleFromFileName(null))
        assertEquals("کتاب بدون نام", titleFromFileName("   "))
        assertEquals("کتاب بدون نام", titleFromFileName(".pdf"))
    }

    @Test fun sanitize_removesIllegalChars() {
        assertEquals("a_b_c", sanitizeFileName("a/b:c"))
        assertEquals("book", sanitizeFileName(""))
    }

    @Test fun formatSize_units() {
        assertEquals("500 بایت", formatSize(500))
        assertEquals("2 کیلوبایت", formatSize(2048))
        assertEquals("1.5 مگابایت", formatSize((1.5 * 1024 * 1024).toLong()))
    }

    @Test fun persianDigits() {
        assertEquals("۱۲۳۴۵", "12345".toPersianDigits())
        assertEquals("۱۲ صفحه", "12 صفحه".toPersianDigits())
    }
}
