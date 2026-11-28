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
    val vaultLockEnabled: Boolean = false,
    val vaultPinHash: String? = null,
    val vaultPinSalt: String? = null,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 21,
    val reminderMinute: Int = 0,
    val lastWhisperIso: String? = null,
    val lastBackupAt: Long = 0L,
    val dismissedWhisperIso: String? = null,
    val swipeHintSeen: Boolean = false,
    val lastWelcomeIso: String? = null,
    val lastEnvelopeNoticeIso: String? = null
)

private val Context.sonderDataStore by preferencesDataStore(name = "sonder_prefs")

class SonderPreferences(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val LOCK = booleanPreferencesKey("app_lock_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val VAULT_LOCK = booleanPreferencesKey("vault_lock_enabled")
        val VAULT_PIN_HASH = stringPreferencesKey("vault_pin_hash")
        val VAULT_PIN_SALT = stringPreferencesKey("vault_pin_salt")
        val REMINDER = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val WHISPER = stringPreferencesKey("last_whisper_iso")
        val BACKUP_AT = longPreferencesKey("last_backup_at")
        val DISMISSED_WHISPER = stringPreferencesKey("dismissed_whisper_iso")
        val SWIPE_HINT_SEEN = booleanPreferencesKey("swipe_hint_seen")
        val WELCOME_ISO = stringPreferencesKey("last_welcome_iso")
        val ENVELOPE_NOTICE = stringPreferencesKey("last_envelope_notice_iso")
    }

    val settings: Flow<SonderSettings> = context.sonderDataStore.data.map { prefs ->
        SonderSettings(
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME] ?: ThemeMode.AUTO.name)
            }.getOrDefault(ThemeMode.AUTO),
            appLockEnabled = prefs[Keys.LOCK] ?: false,
            pinHash = prefs[Keys.PIN_HASH],
            pinSalt = prefs[Keys.PIN_SALT],
            vaultLockEnabled = prefs[Keys.VAULT_LOCK] ?: false,
            vaultPinHash = prefs[Keys.VAULT_PIN_HASH],
            vaultPinSalt = prefs[Keys.VAULT_PIN_SALT],
            reminderEnabled = prefs[Keys.REMINDER] ?: false,
            reminderHour = prefs[Keys.REMINDER_HOUR] ?: 21,
            reminderMinute = prefs[Keys.REMINDER_MINUTE] ?: 0,
            lastWhisperIso = prefs[Keys.WHISPER],
            lastBackupAt = prefs[Keys.BACKUP_AT] ?: 0L,
            dismissedWhisperIso = prefs[Keys.DISMISSED_WHISPER],
            swipeHintSeen = prefs[Keys.SWIPE_HINT_SEEN] ?: false,
            lastWelcomeIso = prefs[Keys.WELCOME_ISO],
            lastEnvelopeNoticeIso = prefs[Keys.ENVELOPE_NOTICE]
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

    suspend fun setVaultLock(enabled: Boolean) {
        context.sonderDataStore.edit { it[Keys.VAULT_LOCK] = enabled }
    }

    suspend fun setVaultPin(hash: String, salt: String) {
        context.sonderDataStore.edit {
            it[Keys.VAULT_PIN_HASH] = hash
            it[Keys.VAULT_PIN_SALT] = salt
            it[Keys.VAULT_LOCK] = true
        }
    }

    suspend fun clearVaultPin() {
        context.sonderDataStore.edit {
            it.remove(Keys.VAULT_PIN_HASH)
            it.remove(Keys.VAULT_PIN_SALT)
            it[Keys.VAULT_LOCK] = false
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

    suspend fun setDismissedWhisperIso(iso: String) {
        context.sonderDataStore.edit { it[Keys.DISMISSED_WHISPER] = iso }
    }

    suspend fun markBackupNow(timestamp: Long = System.currentTimeMillis()) {
        context.sonderDataStore.edit { it[Keys.BACKUP_AT] = timestamp }
    }

    suspend fun markSwipeHintSeen() {
        context.sonderDataStore.edit { it[Keys.SWIPE_HINT_SEEN] = true }
    }

    suspend fun setLastWelcomeIso(iso: String) {
        context.sonderDataStore.edit { it[Keys.WELCOME_ISO] = iso }
    }

    suspend fun setLastEnvelopeNoticeIso(iso: String) {
        context.sonderDataStore.edit { it[Keys.ENVELOPE_NOTICE] = iso }
    }
}
