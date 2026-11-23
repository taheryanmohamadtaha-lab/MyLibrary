@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.mylibrary.pdf.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylibrary.pdf.data.BookEntity
import com.mylibrary.pdf.data.BookRepository
import com.mylibrary.pdf.data.LibraryLogic
import com.mylibrary.pdf.data.SortOrder
import com.mylibrary.pdf.data.ViewMode
import com.mylibrary.pdf.ui.ImportProgress
import com.mylibrary.pdf.ui.LibraryViewModel
import com.mylibrary.pdf.ui.woodColors
import com.mylibrary.pdf.util.fa
import com.mylibrary.pdf.util.formatDate
import com.mylibrary.pdf.util.formatSize
import com.mylibrary.pdf.util.toPersianDigits
import kotlinx.coroutines.launch

private class BookActions(
    val open: (BookEntity) -> Unit,
    val favorite: (BookEntity) -> Unit,
    val rename: (BookEntity) -> Unit,
    val share: (BookEntity) -> Unit,
    val info: (BookEntity) -> Unit,
    val delete: (BookEntity) -> Unit,
)

@Composable
fun BookCollectionScreen(
    title: String,
    favoritesOnly: Boolean,
    vm: LibraryViewModel,
    progress: ImportProgress?,
    onOpenBook: (Long) -> Unit,
    onOpenPage: (Long, Int) -> Unit,
    onAdd: () -> Unit,
) {
    val booksOrNull by vm.books.collectAsStateWithLifecycle()
    val settingsOrNull by vm.settings.collectAsStateWithLifecycle()
    val allBookmarks by vm.allBookmarks.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<BookEntity?>(null) }
    var infoTarget by remember { mutableStateOf<BookEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<BookEntity?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val all = booksOrNull
    val s = settingsOrNull
    if (all == null || s == null) {
        Box(Modifier.fillMaxSize())
        return
    }

    val shown = remember(all, s.sort, query, category, favoritesOnly) {
        LibraryLogic.sort(LibraryLogic.filter(all, query, category, favoritesOnly), s.sort)
    }
    val categories = remember(all) { LibraryLogic.categories(all) }
    val continueList = remember(all, query, favoritesOnly, category) {
        if (favoritesOnly || query.isNotBlank() || category != null) emptyList() else LibraryLogic.continueReading(all)
    }

    // صفحات نشانک‌شده: فقط در تب علاقه‌مندی‌ها
    val bms = remember(all, allBookmarks, query, favoritesOnly) {
        if (!favoritesOnly) {
            emptyList()
        } else {
            val byId = all.associateBy { it.id }
            allBookmarks.mapNotNull { m -> byId[m.bookId]?.let { it to m } }
                .filter { (b, _) -> query.isBlank() || b.title.contains(query.trim(), ignoreCase = true) }
                .sortedByDescending { it.second.createdAt }
        }
    }

    val actions = remember {
        BookActions(
            open = { onOpenBook(it.id) },
            favorite = { vm.toggleFavorite(it) },
            rename = { renameTarget = it },
            share = { b -> scope.launch { shareBook(context, vm.repo, b) } },
            info = { infoTarget = it },
            delete = { deleteTarget = it },
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                if (searching) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        placeholder = { Text("جست‌وجو در عنوان کتاب‌ها") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(title)
                }
            },
            actions = {
                if (searching) {
                    IconButton(onClick = { searching = false; vm.query.value = "" }) { Icon(Icons.Filled.Close, "بستن جست‌وجو") }
                } else {
                    IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, "جست‌وجو") }
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.Sort, "مرتب‌سازی") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        listOf(
                            SortOrder.NEWEST to "جدیدترین",
                            SortOrder.NAME to "نام",
                            SortOrder.LAST_READ to "آخرین مطالعه",
                        ).forEach { (o, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { vm.setSort(o); sortMenu = false },
                                trailingIcon = { if (s.sort == o) Icon(Icons.Filled.Check, null) },
                            )
                        }
                    }
                    IconButton(onClick = { vm.setView(if (s.view == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID) }) {
                        Icon(
                            if (s.view == ViewMode.GRID) Icons.Filled.ViewList else Icons.Filled.GridView,
                            "تغییر حالت نمایش",
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        if (progress != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("در حال افزودن فایل ${(progress.done + 1).fa()} از ${progress.total.fa()} …", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { if (progress.total == 0) 0f else (progress.done + 0.5f) / progress.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(selected = category == null, onClick = { vm.category.value = null }, label = { Text("همه") })
                }
                items(categories) { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { vm.category.value = if (category == c) null else c },
                        label = { Text(c) },
                    )
                }
            }
        }

        when {
            all.isEmpty() -> EmptyLibrary(onAdd)
            shown.isEmpty() && continueList.isEmpty() && bms.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (favoritesOnly && query.isBlank() && category == null) "هنوز کتاب یا صفحه‌ای را به علاقه‌مندی‌ها اضافه نکرده‌اید."
                        else "نتیجه‌ای یافت نشد.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            s.view == ViewMode.GRID -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(130.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp),
                ) {
                    if (continueList.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { ContinueRow(continueList, vm.repo, actions) }
                    }
                    items(shown, key = { it.id }) { b -> GridBook(b, vm.repo, actions) }
                    if (bms.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "bm_header") { BookmarksHeader() }
                        items(bms, key = { "bm${it.second.id}" }, span = { GridItemSpan(maxLineSpan) }) { (b, m) ->
                            BookmarkRow(b, m.page, vm.repo, { onOpenPage(b.id, m.page) }, { vm.deleteBookmark(m.id) })
                        }
                    }
                }
            }
            else -> {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp)) {
                    if (continueList.isNotEmpty()) item { ContinueRow(continueList, vm.repo, actions) }
                    items(shown, key = { it.id }) { b -> ListBook(b, vm.repo, actions) }
                    if (bms.isNotEmpty()) {
                        item(key = "bm_header") { BookmarksHeader() }
                        items(bms, key = { "bm${it.second.id}" }) { (b, m) ->
                            BookmarkRow(b, m.page, vm.repo, { onOpenPage(b.id, m.page) }, { vm.deleteBookmark(m.id) })
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { b ->
        RenameDialog(b, categories, onDismiss = { renameTarget = null }) { t, c ->
            vm.updateMeta(b.id, t, c)
            renameTarget = null
        }
    }
    infoTarget?.let { b -> InfoDialog(b) { infoTarget = null } }
    deleteTarget?.let { b ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف کتاب؟") },
            text = { Text("«${b.title}» از کتابخانه حذف می‌شود. بلافاصله بعد از حذف می‌توانید آن را بازگردانی کنید.") },
            confirmButton = { TextButton(onClick = { vm.delete(b); deleteTarget = null }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("انصراف") } },
        )
    }
}

private suspend fun shareBook(context: Context, repo: BookRepository, b: BookEntity) {
    try {
        val f = repo.prepareShareFile(b)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "اشتراک‌گذاری").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

// ------------------------------------------------------------------ جلد

@Composable
private fun BookCover(book: BookEntity, repo: BookRepository, modifier: Modifier = Modifier) {
    val cover by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, book.coverFileName) {
        value = repo.loadCover(book)
    }
    val c = cover
    if (c != null) {
        Image(c, book.title, modifier, contentScale = ContentScale.Crop)
    } else {
        val palette = listOf(0xFF8D5A3B, 0xFF4E6E58, 0xFF5B6C8F, 0xFF9A5B6B, 0xFF7A6A3A)
        val color = Color(palette[(book.title.hashCode() and 0x7fffffff) % palette.size])
        Box(modifier.background(color), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                Icon(Icons.Filled.PictureAsPdf, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(6.dp))
                Text(book.title, color = Color.White, maxLines = 4, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ------------------------------------------------------------------ شبکه (قفسه)

@Composable
private fun GridBook(b: BookEntity, repo: BookRepository, actions: BookActions) {
    var menu by remember { mutableStateOf(false) }
    val (w1, w2) = woodColors(MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val percent = LibraryLogic.progressOf(b)
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = { actions.open(b) }, onLongClick = { menu = true })) {
        Box(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp)) {
            val shape = RoundedCornerShape(6.dp)
            BookCover(b, repo, Modifier.fillMaxWidth().aspectRatio(0.7f).shadow(8.dp, shape).clip(shape))
            if (b.favorite) {
                Icon(Icons.Filled.Favorite, null, tint = Color(0xFFE53935), modifier = Modifier.align(Alignment.TopStart).padding(5.dp).size(18.dp))
            }
            if (percent > 0) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Black.copy(alpha = 0.25f),
                )
            }
            BookMenu(menu, { menu = false }, b, actions)
        }
        // تخته قفسه
        Box(
            Modifier.fillMaxWidth().height(12.dp)
                .shadow(3.dp)
                .background(Brush.verticalGradient(listOf(w1, w2))),
        )
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(b.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            Text(
                if (percent > 0) "٪${percent.fa()}" else "شروع نشده",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ListBook(b: BookEntity, repo: BookRepository, actions: BookActions) {
    var menu by remember { mutableStateOf(false) }
    val percent = LibraryLogic.progressOf(b)
    Card(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).combinedClickable(onClick = { actions.open(b) }, onLongClick = { menu = true }),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(b, repo, Modifier.width(56.dp).height(80.dp).clip(RoundedCornerShape(6.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(b.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${b.pageCount.fa()} صفحه · ${formatSize(b.sizeBytes).toPersianDigits()}" + (b.category?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                Text("٪${percent.fa()} خوانده شده", style = MaterialTheme.typography.labelSmall)
            }
            if (b.favorite) Icon(Icons.Filled.Favorite, null, tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "گزینه‌ها") }
                BookMenu(menu, { menu = false }, b, actions)
            }
        }
    }
}

@Composable
private fun BookMenu(expanded: Boolean, onDismiss: () -> Unit, b: BookEntity, a: BookActions) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (b.favorite) "حذف از علاقه‌مندی‌ها" else "افزودن به علاقه‌مندی‌ها") },
            leadingIcon = { Icon(if (b.favorite) Icons.Filled.FavoriteBorder else Icons.Filled.Favorite, null) },
            onClick = { onDismiss(); a.favorite(b) },
        )
        DropdownMenuItem(text = { Text("تغییر نام و دسته") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { onDismiss(); a.rename(b) })
        DropdownMenuItem(text = { Text("اشتراک‌گذاری فایل") }, leadingIcon = { Icon(Icons.Filled.Share, null) }, onClick = { onDismiss(); a.share(b) })
        DropdownMenuItem(text = { Text("اطلاعات فایل") }, leadingIcon = { Icon(Icons.Filled.Info, null) }, onClick = { onDismiss(); a.info(b) })
        DropdownMenuItem(text = { Text("حذف") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { onDismiss(); a.delete(b) })
    }
}

// ------------------------------------------------------------------ نشانک‌ها

@Composable
private fun BookmarksHeader() {
    Text(
        "صفحات نشانک‌شده",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun BookmarkRow(b: BookEntity, page: Int, repo: BookRepository, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { onOpen() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(b, repo, Modifier.width(40.dp).height(56.dp).clip(RoundedCornerShape(5.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(b.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(
                    "صفحه ${(page + 1).fa()} از ${b.pageCount.fa()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, "حذف نشانک") }
        }
    }
}

// ------------------------------------------------------------------ ادامه مطالعه

@Composable
private fun ContinueRow(list: List<BookEntity>, repo: BookRepository, actions: BookActions) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text("ادامه مطالعه", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.id }) { b ->
                Card(
                    Modifier.width(260.dp).clickable { actions.open(b) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        BookCover(b, repo, Modifier.width(52.dp).height(74.dp).clip(RoundedCornerShape(6.dp)))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(b.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "صفحه ${(b.lastPage + 1).fa()} از ${b.pageCount.fa()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(progress = { LibraryLogic.progressOf(b) / 100f }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ حالت خالی

@Composable
private fun EmptyLibrary(onAdd: () -> Unit) {
    val t = rememberInfiniteTransition(label = "float")
    val dy by t.animateFloat(
        initialValue = -8f, targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "dy",
    )
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.offset(y = dy.dp).size(130.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.MenuBook, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(24.dp))
        Text("قفسه‌ی شما هنوز خالی است", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "اولین PDF را اضافه کنید تا جلدش روی قفسه بنشیند.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAdd) {
            Icon(Icons.Filled.PictureAsPdf, null)
            Spacer(Modifier.width(8.dp))
            Text("افزودن PDF")
        }
    }
}

// ------------------------------------------------------------------ دیالوگ‌ها

@Composable
private fun RenameDialog(
    book: BookEntity,
    existing: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit,
) {
    var title by rememberSaveable(book.id) { mutableStateOf(book.title) }
    var category by rememberSaveable(book.id) { mutableStateOf(book.category.orEmpty()) }
    val suggestions = (listOf("درسی", "رمان", "کاری") + existing).distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تغییر نام و دسته") },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("عنوان") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(category, { category = it }, label = { Text("دسته / پوشه (اختیاری)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { s -> AssistChip(onClick = { category = s }, label = { Text(s) }) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, category) }, enabled = title.isNotBlank()) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun InfoDialog(b: BookEntity, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اطلاعات فایل") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("عنوان: ${b.title}")
                Text("تعداد صفحه: ${b.pageCount.fa()}")
                Text("حجم: ${formatSize(b.sizeBytes).toPersianDigits()}")
                Text("تاریخ افزودن: ${formatDate(b.addedAt)}")
                Text("آخرین مطالعه: ${b.lastReadAt?.let { formatDate(it) } ?: "—"}")
                Text("پیشرفت: ٪${LibraryLogic.progressOf(b).fa()}")
                b.category?.let { Text("دسته: $it") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}
