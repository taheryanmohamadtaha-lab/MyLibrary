@file:OptIn(ExperimentalFoundationApi::class)

package com.mylibrary.pdf.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class OnbPage(val icon: ImageVector, val title: String, val body: String)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pages = listOf(
        OnbPage(Icons.Filled.AutoStories, "به «کتابخانه من» خوش آمدید", "همه PDFهای شما، روی یک قفسه زیبا؛ با جلد واقعی هر کتاب و درصد پیشرفت مطالعه."),
        OnbPage(Icons.Filled.Add, "افزودن PDF در هر زمان", "روی دکمه «افزودن PDF» بزنید و یک یا چند فایل انتخاب کنید. نسخه‌ای از فایل‌ها در حافظه خصوصی اپ ذخیره می‌شود و با حذف فایل اصلی از دست نمی‌رود."),
        OnbPage(Icons.Filled.CloudOff, "کاملاً آفلاین", "این اپ هیچ مجوز اینترنتی ندارد. آخرین صفحه‌ای که خوانده‌اید خودکار ذخیره می‌شود و از «ادامه مطالعه» برمی‌گردید."),
    )
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = onDone) { Text("رد کردن") }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i ->
            val p = pages[i]
            Column(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(140.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(p.icon, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(32.dp))
                Text(p.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    p.body,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                val sel = pager.currentPage == i
                Box(
                    Modifier.padding(4.dp).size(if (sel) 22.dp else 8.dp, 8.dp).clip(CircleShape)
                        .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        val last = pager.currentPage == pages.size - 1
        Button(
            onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (last) "شروع" else "بعدی") }
    }
}
