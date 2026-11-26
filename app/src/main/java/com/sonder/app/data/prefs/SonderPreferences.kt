package com.sonder.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { AUTO, LIGHT, DARK }

data class SonderSettings(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val appLockEnabled: Boolean = false,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 21,
    val reminderMinute: Int = 0,
    val lastWhisperIso: String? = null,
    val lastBackupAt: Long = 0L,
    val dismissedWhisperIso: String? = null
)

private val Context.sonderDataStore by preferencesDataStore(name = "sonder_prefs")

class SonderPreferences(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val LOCK = booleanPreferencesKey("app_lock_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val REMINDER = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val WHISPER = stringPreferencesKey("last_whisper_iso")
        val BACKUP_AT = longPreferencesKey("last_backup_at")
    }

    val settings: Flow<SonderSettings> = context.sonderDataStore.data.map { prefs ->
        SonderSettings(
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME] ?: ThemeMode.AUTO.name)
            }.getOrDefault(ThemeMode.AUTO),
            appLockEnabled = prefs[Keys.LOCK] ?: false,
            pinHash = prefs[Keys.PIN_HASH],
            pinSalt = prefs[Keys.PIN_SALT],
            reminderEnabled = prefs[Keys.REMINDER] ?: false,
            reminderHour = prefs[Keys.REMINDER_HOUR] ?: 21,
            reminderMinute = prefs[Keys.REMINDER_MINUTE] ?: 0,
            lastWhisperIso = prefs[Keys.WHISPER],
            lastBackupAt = prefs[Keys.BACKUP_AT] ?: 0L
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.sonderDataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setAppLock(enabled: Boolean) {
        context.sonderDataStore.edit { it[Keys.LOCK] = enabled }
    }

    suspend fun setPin(hash: String, salt: String) {
        context.sonderDataStore.edit {
            it[Keys.PIN_HASH] = hash
            it[Keys.PIN_SALT] = salt
        }
    }

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        context.sonderDataStore.edit {
            it[Keys.REMINDER] = enabled
            it[Keys.REMINDER_HOUR] = hour
            it[Keys.REMINDER_MINUTE] = minute
        }
    }

    suspend fun setLastWhisperIso(iso: String) {
        context.sonderDataStore.edit { it[Keys.WHISPER] = iso }
    }

    suspend fun markBackupNow(timestamp: Long = System.currentTimeMillis()) {
        context.sonderDataStore.edit { it[Keys.BACKUP_AT] = timestamp }
    }
}
