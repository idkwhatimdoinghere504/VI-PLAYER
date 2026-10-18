package com.viplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsData: DataStore<Preferences> by
    preferencesDataStore(name = "vi_settings")

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentIndex: Int = 0,
    val dynamicColor: Boolean = false,
    val sort: SongSort = SongSort.TITLE
)

/** Small key/value settings store for appearance and library preferences. */
class SettingsStore(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val accentKey = intPreferencesKey("accent_index")
    private val dynamicKey = booleanPreferencesKey("dynamic_color")
    private val sortKey = stringPreferencesKey("song_sort")

    val settings: Flow<AppSettings> = context.settingsData.data.map { p ->
        AppSettings(
            themeMode = p[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            accentIndex = p[accentKey] ?: 0,
            dynamicColor = p[dynamicKey] ?: false,
            sort = p[sortKey]?.let { runCatching { SongSort.valueOf(it) }.getOrNull() }
                ?: SongSort.TITLE
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsData.edit { it[themeKey] = mode.name }
    }

    suspend fun setAccentIndex(index: Int) {
        context.settingsData.edit { it[accentKey] = index }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsData.edit { it[dynamicKey] = enabled }
    }

    suspend fun setSort(sort: SongSort) {
        context.settingsData.edit { it[sortKey] = sort.name }
    }
}
