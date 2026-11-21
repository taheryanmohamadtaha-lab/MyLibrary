@file:OptIn(ExperimentalMaterial3Api::class)

package com.mylibrary.pdf.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylibrary.pdf.data.ThemeMode
import com.mylibrary.pdf.ui.LibraryViewModel
import com.mylibrary.pdf.util.toPersianDigits

@Composable
fun SettingsScreen(vm: LibraryViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val settings = s ?: return
    val context = LocalContext.current
    var withFiles by rememberSaveable { mutableStateOf(false) }
    var askBackup by rememberSaveable { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportBackup(uri, withFiles)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restoreBackup(uri)
    }
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("تنظیمات") },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            SectionTitle("ظاهر")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.SYSTEM to "سیستم", ThemeMode.LIGHT to "روشن", ThemeMode.DARK to "تیره").forEach { (m, label) ->
                    FilterChip(selected = settings.theme == m, onClick = { vm.setTheme(m) }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(16.dp))
            SectionTitle("مطالعه")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("حالت مطالعه شب")
                    Text(
                        "رنگ صفحه‌ها در خواننده وارونه می‌شود (داخل خواننده هم قابل تغییر است).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.nightReading, onCheckedChange = { vm.setNight(it) })
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            SectionTitle("پشتیبان‌گیری و بازیابی")
            Text(
                "خروجی یک فایل zip است. در حالت «فقط فهرست» نشانک‌ها، علاقه‌مندی‌ها، دسته‌ها و پیشرفت مطالعه ذخیره می‌شود و بازیابی آن روی کتاب‌هایی اثر می‌کند که فایلشان در اپ هست.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { askBackup = true }) {
                    Icon(Icons.Filled.Backup, null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("پشتیبان‌گیری")
                }
                OutlinedButton(onClick = {
                    importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                }) {
                    Icon(Icons.Filled.Restore, null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("بازیابی")
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            SectionTitle("درباره")
            Text("کتابخانه من — نسخه ${version.toPersianDigits()}")
            Text("سازنده: محمدطاها طاهریان")
            Spacer(Modifier.height(8.dp))
            Text(
                "این اپ کاملاً آفلاین است و مجوز اینترنت ندارد.\nمحدودیت‌ها: PDFهای رمزدار پشتیبانی نمی‌شوند (محدودیت PdfRenderer اندروید)؛ حداکثر حجم هر فایل ۵۰۰ مگابایت است.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (askBackup) {
        AlertDialog(
            onDismissRequest = { askBackup = false },
            title = { Text("پشتیبان‌گیری") },
            text = { Text("پشتیبان همراه با خود فایل‌های PDF هم ساخته شود؟ (حجم فایل خروجی بزرگ می‌شود.)") },
            confirmButton = {
                TextButton(onClick = {
                    withFiles = true; askBackup = false
                    exportLauncher.launch("my-library-backup.zip")
                }) { Text("همراه با فایل‌ها") }
            },
            dismissButton = {
                TextButton(onClick = {
                    withFiles = false; askBackup = false
                    exportLauncher.launch("my-library-backup.zip")
                }) { Text("فقط فهرست") }
            },
        )
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
}
