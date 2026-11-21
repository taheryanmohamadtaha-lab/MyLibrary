package com.mylibrary.pdf

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.mylibrary.pdf.data.BookRepository
import com.mylibrary.pdf.data.LibraryDatabase
import com.mylibrary.pdf.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    /** برای کارهایی که باید با بسته شدن صفحه قطع نشوند (ذخیره پیشرفت، ورود فایل‌ها). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db = Room.databaseBuilder(context, LibraryDatabase::class.java, "library.db").build()
    val repo = BookRepository(context, db.bookDao())
    val settings = SettingsStore(context)
}

class MyLibraryApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch(Dispatchers.IO) {
            try {
                container.repo.purgeDeleted()
            } catch (_: Throwable) {
            }
        }
    }
}
