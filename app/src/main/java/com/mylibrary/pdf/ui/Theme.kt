package com.mylibrary.pdf.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import com.mylibrary.pdf.data.ThemeMode

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF7B4B2A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCBE),
    onPrimaryContainer = Color(0xFF2B1604),
    secondary = Color(0xFF6F5B47),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6DFC8),
    onSecondaryContainer = Color(0xFF271A0B),
    background = Color(0xFFFBF5EC),
    onBackground = Color(0xFF2B2118),
    surface = Color(0xFFFBF5EC),
    onSurface = Color(0xFF2B2118),
    surfaceVariant = Color(0xFFEFE1D1),
    onSurfaceVariant = Color(0xFF50443A),
    outline = Color(0xFF82746A),
    error = Color(0xFFBA1A1A),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFE8B98A),
    onPrimary = Color(0xFF442A0F),
    primaryContainer = Color(0xFF5E3C1F),
    onPrimaryContainer = Color(0xFFFFDCBE),
    secondary = Color(0xFFD9C2AA),
    onSecondary = Color(0xFF3C2E1E),
    secondaryContainer = Color(0xFF54442F),
    onSecondaryContainer = Color(0xFFF6DFC8),
    background = Color(0xFF1B1511),
    onBackground = Color(0xFFEDE0D4),
    surface = Color(0xFF1B1511),
    onSurface = Color(0xFFEDE0D4),
    surfaceVariant = Color(0xFF3A3029),
    onSurfaceVariant = Color(0xFFD3C4B8),
    outline = Color(0xFF9C8E82),
    error = Color(0xFFFFB4AB),
)

/** اگر فونت وزیرمتن در assets/fonts باشد استفاده می‌شود، وگرنه فونت سیستم. */
private fun loadFontFamily(context: Context): FontFamily {
    return try {
        val assets = context.assets
        val files = assets.list("fonts")?.toSet() ?: emptySet()
        val list = ArrayList<Font>()
        if ("Vazirmatn-Regular.ttf" in files) {
            list.add(Font("fonts/Vazirmatn-Regular.ttf", assets, FontWeight.Normal))
            list.add(Font("fonts/Vazirmatn-Regular.ttf", assets, FontWeight.Medium))
        }
        if ("Vazirmatn-Bold.ttf" in files) {
            list.add(Font("fonts/Vazirmatn-Bold.ttf", assets, FontWeight.SemiBold))
            list.add(Font("fonts/Vazirmatn-Bold.ttf", assets, FontWeight.Bold))
        }
        if (list.isEmpty()) FontFamily.Default else FontFamily(list)
    } catch (e: Throwable) {
        FontFamily.Default
    }
}

private fun typographyOf(f: FontFamily): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = f),
        displayMedium = d.displayMedium.copy(fontFamily = f),
        displaySmall = d.displaySmall.copy(fontFamily = f),
        headlineLarge = d.headlineLarge.copy(fontFamily = f),
        headlineMedium = d.headlineMedium.copy(fontFamily = f),
        headlineSmall = d.headlineSmall.copy(fontFamily = f),
        titleLarge = d.titleLarge.copy(fontFamily = f),
        titleMedium = d.titleMedium.copy(fontFamily = f),
        titleSmall = d.titleSmall.copy(fontFamily = f),
        bodyLarge = d.bodyLarge.copy(fontFamily = f),
        bodyMedium = d.bodyMedium.copy(fontFamily = f),
        bodySmall = d.bodySmall.copy(fontFamily = f),
        labelLarge = d.labelLarge.copy(fontFamily = f),
        labelMedium = d.labelMedium.copy(fontFamily = f),
        labelSmall = d.labelSmall.copy(fontFamily = f),
    )
}

@Composable
fun MyLibraryTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val family = remember { loadFontFamily(context) }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val ic = WindowCompat.getInsetsController(window, view)
            ic.isAppearanceLightStatusBars = !dark
            ic.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = remember(family) { typographyOf(family) },
            content = content,
        )
    }
}

/** رنگ‌های چوب قفسه */
fun woodColors(dark: Boolean): Pair<Color, Color> =
    if (dark) Color(0xFF6B4A36) to Color(0xFF3E2A1E) else Color(0xFFA9794F) to Color(0xFF7A5232)
