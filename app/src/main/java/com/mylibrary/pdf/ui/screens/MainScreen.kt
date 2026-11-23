@file:OptIn(ExperimentalMaterial3Api::class)

package com.mylibrary.pdf.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylibrary.pdf.ui.LibraryViewModel
import com.mylibrary.pdf.ui.UiEvent
import com.mylibrary.pdf.util.fa

@Composable
fun MainScreen(vm: LibraryViewModel, onOpenBook: (Long) -> Unit, onOpenPage: (Long, Int) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val progress by vm.importProgress.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.importUris(uris)
    }
    val addPdf = { picker.launch(arrayOf("application/pdf")) }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is UiEvent.Message -> snackbar.showSnackbar(e.text)
                is UiEvent.Undo -> {
                    val r = snackbar.showSnackbar(e.text, actionLabel = "بازگردانی", duration = SnackbarDuration.Long)
                    if (r == SnackbarResult.ActionPerformed) vm.undoDelete(e.bookId) else vm.purge(e.bookId)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.LocalLibrary, null) }, label = { Text("کتابخانه") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Favorite, null) }, label = { Text("علاقه‌مندی‌ها") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("تنظیمات") },
                )
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { addPdf() },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("افزودن PDF") },
                )
            }
        },
    ) { inner ->
        // فقط فاصله پایین را اعمال می‌کنیم؛ TopAppBar خودش فضای نوار وضعیت را مدیریت می‌کند.
        Box(Modifier.fillMaxSize().padding(bottom = inner.calculateBottomPadding())) {
            when (tab) {
                0 -> BookCollectionScreen("کتابخانه من", false, vm, progress, onOpenBook, onOpenPage, addPdf)
                1 -> BookCollectionScreen("علاقه‌مندی‌ها", true, vm, progress, onOpenBook, onOpenPage, addPdf)
                else -> SettingsScreen(vm)
            }
        }
    }

    val rep = report
    if (rep != null) {
        AlertDialog(
            onDismissRequest = { vm.dismissReport() },
            title = { Text("گزارش افزودن فایل‌ها") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (rep.added > 0) Text("${rep.added.fa()} کتاب با موفقیت اضافه شد.")
                    if (rep.duplicates.isNotEmpty()) {
                        Text("تکراری (قبلاً در کتابخانه بود):", Modifier.padding(top = 8.dp))
                        rep.duplicates.forEach { Text("• $it") }
                    }
                    if (rep.failures.isNotEmpty()) {
                        Text("ناموفق:", Modifier.padding(top = 8.dp))
                        rep.failures.forEach { (n, why) -> Text("• $n — $why", Modifier.padding(top = 4.dp)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.dismissReport() }) { Text("باشه") } },
        )
    }
}
