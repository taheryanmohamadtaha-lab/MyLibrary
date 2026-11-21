@file:OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)

package com.mylibrary.pdf.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mylibrary.pdf.MyLibraryApp
import com.mylibrary.pdf.pdf.PdfSession
import com.mylibrary.pdf.ui.ReaderState
import com.mylibrary.pdf.ui.ReaderViewModel
import com.mylibrary.pdf.util.fa
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

private val NightFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

@Composable
fun ReaderScreen(bookId: Long, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as MyLibraryApp
    val vm: ReaderViewModel = viewModel(
        key = "reader$bookId",
        factory = viewModelFactory { initializer { ReaderViewModel(app.container, bookId) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by app.container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val night = settings?.nightReading ?: false
    val scope = rememberCoroutineScope()

    when (val st = state) {
        is ReaderState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text("در حال آماده‌سازی کتاب…", Modifier.padding(top = 16.dp))
            }
        }
        is ReaderState.Error -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(st.message, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text("بازگشت") }
            }
        }
        is ReaderState.Ready -> ReaderContent(
            st = st,
            vm = vm,
            night = night,
            onToggleNight = { scope.launch { app.container.settings.setNight(!night) } },
            onBack = onBack,
        )
    }
}

@Composable
private fun ReaderContent(
    st: ReaderState.Ready,
    vm: ReaderViewModel,
    night: Boolean,
    onToggleNight: () -> Unit,
    onBack: () -> Unit,
) {
    val count = st.session.pageCount
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = st.book.lastPage.coerceIn(0, (count - 1).coerceAtLeast(0)))
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var showJump by rememberSaveable { mutableStateOf(false) }
    var showBookmarks by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()

    val currentPage by remember {
        derivedStateOf {
            if (count > 0 && !listState.canScrollForward) count - 1 else listState.firstVisibleItemIndex
        }
    }
    val isBookmarked = bookmarks.any { it.page == currentPage }

    // ذخیره خودکار آخرین صفحه
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { currentPage }.debounce(600).collect { vm.saveProgress(it) }
    }
    val latest by rememberUpdatedState(currentPage)
    DisposableEffect(Unit) {
        onDispose { vm.saveProgress(latest) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(st.book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } },
                actions = {
                    IconButton(onClick = { vm.toggleBookmark(currentPage) }) {
                        Icon(if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "نشانک")
                    }
                    IconButton(onClick = { showBookmarks = true }) { Icon(Icons.Filled.Bookmarks, "فهرست نشانک‌ها") }
                    IconButton(onClick = onToggleNight) {
                        Icon(if (night) Icons.Filled.LightMode else Icons.Filled.DarkMode, "حالت شب")
                    }
                },
            )
        },
    ) { inner ->
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(inner).background(if (night) Color(0xFF111111) else Color(0xFFD9D3CB)),
        ) {
            val viewportPx = constraints.maxWidth.toFloat()
            val density = LocalDensity.current
            val widthDp = with(density) { (viewportPx * scale).toDp() }
            val bucket = ceil(scale.toDouble()).toInt().coerceIn(1, 3)
            val renderWidth = min(viewportPx * bucket, 3000f).roundToInt()

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1.05f) { scale = 1f; offsetX = 0f } else { scale = 2.5f }
                        })
                    }
                    .pointerInput(viewportPx) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val multi = event.changes.count { it.pressed } >= 2
                                if (multi || scale > 1f) {
                                    val zoom = if (multi) event.calculateZoom() else 1f
                                    val pan = event.calculatePan()
                                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                                    val maxOff = (viewportPx * newScale - viewportPx) / 2f
                                    scale = newScale
                                    offsetX = (offsetX + pan.x).coerceIn(-maxOff, maxOff)
                                    if (multi) event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(count, key = { it }) { index ->
                    val ratio = st.ratios.getOrElse(index) { 1.414f }
                    PdfPage(
                        session = st.session,
                        index = index,
                        renderWidth = renderWidth,
                        night = night,
                        modifier = Modifier
                            .requiredWidth(widthDp)
                            .aspectRatio(1f / ratio)
                            .offset { IntOffset(offsetX.roundToInt(), 0) },
                    )
                }
            }

            // نمایشگر شماره صفحه (با لمس: پرش به صفحه)
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp).clickable { showJump = true },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                shadowElevation = 6.dp,
            ) {
                Text(
                    "صفحه ${(currentPage + 1).fa()} از ${count.fa()}",
                    Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }

    if (showJump) {
        var text by remember { mutableStateOf("") }
        val target = text.toIntOrNull()
        AlertDialog(
            onDismissRequest = { showJump = false },
            title = { Text("پرش به صفحه") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(6) },
                    label = { Text("شماره صفحه (۱ تا ${count.fa()})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = target != null && target in 1..count,
                    onClick = {
                        showJump = false
                        scope.launch { listState.scrollToItem((target ?: 1) - 1) }
                    },
                ) { Text("برو") }
            },
            dismissButton = { TextButton(onClick = { showJump = false }) { Text("انصراف") } },
        )
    }

    if (showBookmarks) {
        ModalBottomSheet(onDismissRequest = { showBookmarks = false }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text("نشانک‌ها", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                if (bookmarks.isEmpty()) {
                    Text("هنوز نشانکی نگذاشته‌اید. با آیکن نشانک بالای صفحه، صفحه جاری را نشانک کنید.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                bookmarks.forEach { b ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            showBookmarks = false
                            scope.launch { listState.scrollToItem(b.page.coerceIn(0, count - 1)) }
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Bookmark, null, tint = MaterialTheme.colorScheme.primary)
                        Text("صفحه ${(b.page + 1).fa()}", Modifier.weight(1f).padding(horizontal = 12.dp))
                        IconButton(onClick = { vm.deleteBookmark(b.id) }) { Icon(Icons.Filled.Delete, "حذف نشانک") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(session: PdfSession, index: Int, renderWidth: Int, night: Boolean, modifier: Modifier) {
    // هنگام تغییر زوم، تصویر قبلی تا آماده شدن تصویر جدید نمایش داده می‌شود
    var bmp by remember(index) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(index) { mutableStateOf(false) }
    LaunchedEffect(index, renderWidth) {
        val b = session.render(index, renderWidth)
        if (b != null) { bmp = b; failed = false } else if (bmp == null) failed = true
    }
    Box(modifier.background(if (night) Color.Black else Color.White), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            val img = remember(b) { b.asImageBitmap() }
            Image(
                bitmap = img,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                colorFilter = if (night) NightFilter else null,
            )
        } else if (failed) {
            Text("نمایش این صفحه ممکن نشد", color = if (night) Color.White else Color.Black)
        } else {
            CircularProgressIndicator(Modifier.padding(16.dp))
        }
    }
}
