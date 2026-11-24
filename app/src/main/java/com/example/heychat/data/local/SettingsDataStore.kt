package com.example.heychat.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hey_chat_settings")

enum class AppTheme {
    SYSTEM, LIGHT, DARK
}

class SettingsDataStore(private val context: Context) {

    companion object {
        val THEME_KEY = stringPreferencesKey("app_theme")
        val WALLPAPER_KEY = stringPreferencesKey("chat_wallpaper")
        val NOTIF_MESSAGE_KEY = booleanPreferencesKey("notif_message")
        val NOTIF_GROUP_KEY = booleanPreferencesKey("notif_group")
        val NOTIF_CALL_KEY = booleanPreferencesKey("notif_call")
        val NOTIF_SOUND_KEY = booleanPreferencesKey("notif_sound")
        val NOTIF_VIBRATE_KEY = booleanPreferencesKey("notif_vibrate")
    }

    val themeFlow: Flow<AppTheme> = context.dataStore.data.map { preferences ->
        val themeName = preferences[THEME_KEY] ?: AppTheme.SYSTEM.name
        runCatching { AppTheme.valueOf(themeName) }.getOrDefault(AppTheme.SYSTEM)
    }

    val wallpaperFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[WALLPAPER_KEY] ?: "DEFAULT"
    }

    val notifMessageFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIF_MESSAGE_KEY] ?: true
    }

    val notifGroupFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIF_GROUP_KEY] ?: true
    }

    val notifCallFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIF_CALL_KEY] ?: true
    }

    val notifSoundFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIF_SOUND_KEY] ?: true
    }

    val notifVibrateFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIF_VIBRATE_KEY] ?: true
    }

    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme.name
        }
    }

    suspend fun setWallpaper(wallpaper: String) {
        context.dataStore.edit { preferences ->
            preferences[WALLPAPER_KEY] = wallpaper
        }
    }

    suspend fun setNotifSetting(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
