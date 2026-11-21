package com.mylibrary.pdf.util

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale

/** منطق خالص (بدون وابستگی به اندروید) تا قابل تست واحد باشد. */
object Hashing {
    /** ورودی را در خروجی کپی می‌کند و هش SHA-256 محتوا را برمی‌گرداند. */
    fun copyAndHash(input: InputStream, output: OutputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
            output.write(buf, 0, n)
        }
        output.flush()
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

fun String.toPersianDigits(): String {
    val sb = StringBuilder(length)
    for (c in this) {
        if (c in '0'..'9') sb.append('۰' + (c - '0')) else sb.append(c)
    }
    return sb.toString()
}

fun Int.fa(): String = toString().toPersianDigits()

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes بایت"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.0f کیلوبایت", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f مگابایت", mb)
    return String.format(Locale.US, "%.2f گیگابایت", mb / 1024.0)
}

fun titleFromFileName(name: String?): String {
    val n = name?.trim().orEmpty()
    if (n.isEmpty()) return "کتاب بدون نام"
    val base = if (n.endsWith(".pdf", ignoreCase = true)) n.dropLast(4) else n
    return base.replace('_', ' ').trim().ifEmpty { "کتاب بدون نام" }
}

fun sanitizeFileName(title: String): String =
    title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).ifEmpty { "book" }

fun formatDate(ms: Long): String = try {
    val f = android.icu.text.SimpleDateFormat("d MMMM yyyy", android.icu.util.ULocale("fa_IR@calendar=persian"))
    f.format(java.util.Date(ms))
} catch (e: Throwable) {
    java.text.DateFormat.getDateInstance().format(java.util.Date(ms))
}
