package com.mylibrary.pdf.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "books", indices = [Index(value = ["hash"], unique = true)])
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** نام فایل داخل filesDir/books */
    val fileName: String,
    /** نام فایل جلد داخل filesDir/covers (ممکن است null باشد) */
    val coverFileName: String?,
    /** SHA-256 محتوای فایل برای تشخیص تکراری */
    val hash: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val addedAt: Long,
    val lastReadAt: Long? = null,
    /** شماره صفحه (از صفر) */
    val lastPage: Int = 0,
    val favorite: Boolean = false,
    val category: String? = null,
    /** حذف نرم برای قابلیت بازگردانی */
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val page: Int,
    val createdAt: Long,
)

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE deletedAt IS NULL")
    suspend fun getAllNow(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE hash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): BookEntity?

    @Query("SELECT * FROM books WHERE deletedAt IS NOT NULL")
    suspend fun getDeleted(): List<BookEntity>

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query("UPDATE books SET lastPage = :page, lastReadAt = :time WHERE id = :id")
    suspend fun saveProgress(id: Long, page: Int, time: Long)

    @Query("UPDATE books SET deletedAt = :time WHERE id = :id")
    suspend fun setDeleted(id: Long, time: Long?)

    @Query("UPDATE books SET favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY page")
    fun observeBookmarks(bookId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId AND page = :page LIMIT 1")
    suspend fun getBookmark(bookId: Long, page: Int): BookmarkEntity?

    @Query("SELECT * FROM bookmarks")
    suspend fun getAllBookmarks(): List<BookmarkEntity>

    @Insert
    suspend fun insertBookmark(b: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)
}

@Database(entities = [BookEntity::class, BookmarkEntity::class], version = 1, exportSchema = false)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
}
