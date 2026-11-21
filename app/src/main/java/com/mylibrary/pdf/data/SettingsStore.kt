package com.mylibrary.pdf.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val sort: SortOrder = SortOrder.NEWEST,
    val view: ViewMode = ViewMode.GRID,
    val nightReading: Boolean = false,
    val onboardingDone: Boolean = false,
)

class SettingsStore(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val sort = stringPreferencesKey("sort")
        val view = stringPreferencesKey("view")
        val night = booleanPreferencesKey("night")
        val onboarding = booleanPreferencesKey("onboarding_done")
    }

    private inline fun <reified T : Enum<T>> parse(s: String?, def: T): T =
        enumValues<T>().firstOrNull { it.name == s } ?: def

    val settings: Flow<AppSettings> = context.dataStore.data.map { p: Preferences ->
        AppSettings(
            theme = parse(p[K.theme], ThemeMode.SYSTEM),
            sort = parse(p[K.sort], SortOrder.NEWEST),
            view = parse(p[K.view], ViewMode.GRID),
            nightReading = p[K.night] ?: false,
            onboardingDone = p[K.onboarding] ?: false,
        )
    }

    suspend fun setTheme(v: ThemeMode) { context.dataStore.edit { it[K.theme] = v.name } }
    suspend fun setSort(v: SortOrder) { context.dataStore.edit { it[K.sort] = v.name } }
    suspend fun setView(v: ViewMode) { context.dataStore.edit { it[K.view] = v.name } }
    suspend fun setNight(v: Boolean) { context.dataStore.edit { it[K.night] = v } }
    suspend fun setOnboardingDone() { context.dataStore.edit { it[K.onboarding] = true } }
}
