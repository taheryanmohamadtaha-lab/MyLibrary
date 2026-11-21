package com.mylibrary.pdf.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mylibrary.pdf.MyLibraryApp
import com.mylibrary.pdf.data.ThemeMode
import com.mylibrary.pdf.ui.screens.MainScreen
import com.mylibrary.pdf.ui.screens.OnboardingScreen
import com.mylibrary.pdf.ui.screens.ReaderScreen

@Composable
fun AppRoot(intentVm: IntentViewModel) {
    val app = LocalContext.current.applicationContext as MyLibraryApp
    val vm: LibraryViewModel = viewModel(factory = viewModelFactory { initializer { LibraryViewModel(app.container) } })
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings

    MyLibraryTheme(s?.theme ?: ThemeMode.SYSTEM) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (s != null) {
                val nav = rememberNavController()
                val start = remember { if (s.onboardingDone) "main" else "onboarding" }
                NavHost(navController = nav, startDestination = start) {
                    composable("onboarding") {
                        OnboardingScreen(onDone = {
                            vm.finishOnboarding()
                            nav.navigate("main") { popUpTo("onboarding") { inclusive = true } }
                        })
                    }
                    composable("main") {
                        MainScreen(vm = vm, onOpenBook = { id -> nav.navigate("reader/$id") })
                    }
                    composable(
                        "reader/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    ) { entry ->
                        ReaderScreen(
                            bookId = entry.arguments?.getLong("id") ?: -1L,
                            onBack = { nav.popBackStack() },
                        )
                    }
                }

                val incoming by intentVm.uri.collectAsStateWithLifecycle()
                val uri = incoming
                if (uri != null) {
                    AlertDialog(
                        onDismissRequest = { intentVm.uri.value = null },
                        title = { Text("افزودن به کتابخانه؟") },
                        text = {
                            Text("این PDF از برنامه‌ای دیگر باز شده است. برای مطالعه، یک نسخه از آن در کتابخانه شما ذخیره می‌شود تا بدون اینترنت و حتی با حذف فایل اصلی در دسترس بماند.")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                intentVm.uri.value = null
                                vm.importAndOpen(uri) { id -> nav.navigate("reader/$id") }
                            }) { Text("افزودن و باز کردن") }
                        },
                        dismissButton = {
                            TextButton(onClick = { intentVm.uri.value = null }) { Text("انصراف") }
                        },
                    )
                }
            }
        }
    }
}
