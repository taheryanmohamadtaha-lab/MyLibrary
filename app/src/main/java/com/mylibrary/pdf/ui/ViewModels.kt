package com.mylibrary.pdf.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylibrary.pdf.AppContainer
import com.mylibrary.pdf.data.AppSettings
import com.mylibrary.pdf.data.BookEntity
import com.mylibrary.pdf.data.BookmarkEntity
import com.mylibrary.pdf.data.ImportResult
import com.mylibrary.pdf.data.SortOrder
import com.mylibrary.pdf.data.ThemeMode
import com.mylibrary.pdf.data.ViewMode
import com.mylibrary.pdf.pdf.PdfSession
import com.mylibrary.pdf.util.fa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportProgress(val done: Int, val total: Int)

data class ImportReport(
    val added: Int,
    val duplicates: List<String>,
    val failures: List<Pair<String, String>>,
)

sealed class UiEvent {
    data class Message(val text: String) : UiEvent()
    data class Undo(val text: String, val bookId: Long) : UiEvent()
}

/** PDF ورودی از برنامه‌های دیگر (intent) */
class IntentViewModel : ViewModel() {
    val uri = MutableStateFlow<Uri?>(null)
}

class LibraryViewModel(private val c: AppContainer) : ViewModel() {
    val repo = c.repo

    val books: StateFlow<List<BookEntity>?> =
        c.repo.books.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings: StateFlow<AppSettings?> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val allBookmarks: StateFlow<List<BookmarkEntity>> =
        c.repo.allBookmarks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun deleteBookmark(id: Long) { viewModelScope.launch { c.repo.deleteBookmark(id) } }

    val query = MutableStateFlow("")
    val category = MutableStateFlow<String?>(null)

    private val _import = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _import.asStateFlow()
    private val _report = MutableStateFlow<ImportReport?>(null)
    val report: StateFlow<ImportReport?> = _report.asStateFlow()
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    fun dismissReport() { _report.value = null }

    // ---------------- ورود
    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        c.appScope.launch {
            var added = 0
            val dups = ArrayList<String>()
            val fails = ArrayList<Pair<String, String>>()
            uris.forEachIndexed { i, u ->
                _import.value = ImportProgress(i, uris.size)
                when (val r = withContext(Dispatchers.IO) { c.repo.importUri(u) }) {
                    is ImportResult.Added -> added++
                    is ImportResult.Duplicate -> dups.add(r.book.title)
                    is ImportResult.Failed -> fails.add(r.name to r.reason)
                }
            }
            _import.value = null
            if (dups.isEmpty() && fails.isEmpty()) {
                _events.emit(UiEvent.Message("${added.fa()} کتاب به کتابخانه اضافه شد"))
            } else {
                _report.value = ImportReport(added, dups, fails)
            }
        }
    }

    fun importAndOpen(uri: Uri, onOpen: (Long) -> Unit) {
        viewModelScope.launch {
            _import.value = ImportProgress(0, 1)
            val r = withContext(Dispatchers.IO) { c.repo.importUri(uri) }
            _import.value = null
            when (r) {
                is ImportResult.Added -> onOpen(r.book.id)
                is ImportResult.Duplicate -> onOpen(r.book.id)
                is ImportResult.Failed -> _report.value = ImportReport(0, emptyList(), listOf(r.name to r.reason))
            }
        }
    }

    // ---------------- مدیریت
    fun toggleFavorite(b: BookEntity) { viewModelScope.launch { c.repo.setFavorite(b.id, !b.favorite) } }
    fun updateMeta(id: Long, title: String, category: String?) {
        viewModelScope.launch { c.repo.updateMeta(id, title, category) }
    }
    fun delete(b: BookEntity) {
        viewModelScope.launch {
            c.repo.softDelete(b.id)
            _events.emit(UiEvent.Undo("«${b.title}» حذف شد", b.id))
        }
    }
    fun undoDelete(id: Long) { viewModelScope.launch { c.repo.undoDelete(id) } }
    fun purge(id: Long) { c.appScope.launch { c.repo.purgeById(id) } }

    // ---------------- تنظیمات
    fun setSort(v: SortOrder) { viewModelScope.launch { c.settings.setSort(v) } }
    fun setView(v: ViewMode) { viewModelScope.launch { c.settings.setView(v) } }
    fun setTheme(v: ThemeMode) { viewModelScope.launch { c.settings.setTheme(v) } }
    fun setNight(v: Boolean) { viewModelScope.launch { c.settings.setNight(v) } }
    fun finishOnboarding() { viewModelScope.launch { c.settings.setOnboardingDone() } }

    // ---------------- پشتیبان
    fun exportBackup(uri: Uri, withFiles: Boolean) {
        c.appScope.launch {
            val msg = try {
                c.repo.exportBackup(uri, withFiles)
                "پشتیبان با موفقیت ذخیره شد"
            } catch (e: Exception) {
                "ذخیره پشتیبان ناموفق بود"
            }
            _events.emit(UiEvent.Message(msg))
        }
    }

    fun restoreBackup(uri: Uri) {
        c.appScope.launch {
            val msg = try {
                val s = c.repo.restoreBackup(uri)
                "بازیابی انجام شد: ${s.added.fa()} کتاب جدید، ${s.updated.fa()} به‌روزرسانی، ${s.skipped.fa()} ردشده (فایل در پشتیبان نبود)"
            } catch (e: Exception) {
                "فایل پشتیبان معتبر نیست"
            }
            _events.emit(UiEvent.Message(msg))
        }
    }
}

sealed class ReaderState {
    data object Loading : ReaderState()
    data class Error(val message: String) : ReaderState()
    data class Ready(val book: BookEntity, val session: PdfSession, val ratios: List<Float>) : ReaderState()
}

class ReaderViewModel(private val c: AppContainer, private val bookId: Long) : ViewModel() {
    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()
    val bookmarks: StateFlow<List<BookmarkEntity>> =
        c.repo.bookmarks(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var session: PdfSession? = null

    init {
        viewModelScope.launch {
            val book = c.repo.getBook(bookId)
            if (book == null) {
                _state.value = ReaderState.Error("این کتاب پیدا نشد.")
                return@launch
            }
            val file = c.repo.bookFile(book)
            if (!file.exists()) {
                _state.value = ReaderState.Error("فایل این کتاب دیگر وجود ندارد (حذف یا جابه‌جا شده است).")
                return@launch
            }
            try {
                val s = withContext(Dispatchers.IO) { PdfSession(file) }
                session = s
                val ratios = s.loadRatios()
                _state.value = ReaderState.Ready(book, s, ratios)
            } catch (e: SecurityException) {
                _state.value = ReaderState.Error("این PDF رمزدار است و اندروید از باز کردن آن پشتیبانی نمی‌کند.")
            } catch (e: Exception) {
                _state.value = ReaderState.Error("فایل خراب است و باز نمی‌شود.")
            }
        }
    }

    fun saveProgress(page: Int) { c.appScope.launch { c.repo.saveProgress(bookId, page) } }
    fun toggleBookmark(page: Int) { viewModelScope.launch { c.repo.toggleBookmark(bookId, page) } }
    fun deleteBookmark(id: Long) { viewModelScope.launch { c.repo.deleteBookmark(id) } }

    override fun onCleared() {
        session?.close()
        super.onCleared()
    }
}
