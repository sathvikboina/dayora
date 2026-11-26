package com.sonder.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sonder.app.data.local.SonderDatabase
import com.sonder.app.data.local.entity.DailyEntryEntity
import com.sonder.app.data.local.entity.DayState
import com.sonder.app.data.local.entity.EntryEmbeddingEntity
import com.sonder.app.data.local.entity.EntryTagEntity
import com.sonder.app.data.prefs.SonderPreferences
import com.sonder.app.data.prefs.SonderSettings
import com.sonder.app.data.prefs.ThemeMode
import com.sonder.app.ml.EmbeddingEngine
import com.sonder.app.ml.EntityExtractor
import com.sonder.app.ml.LightweightLexiconToneRouter
import com.sonder.app.ml.VectorMath
import com.sonder.app.reminder.ReminderScheduler
import com.sonder.app.security.PinHasher
import com.sonder.app.util.BackupManager
import com.sonder.app.util.DateDisplayFormatter
import com.sonder.app.util.ImportedEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class RescueMemory(
    val entry: DailyEntryEntity,
    val score: Float
)

data class RestoreReport(
    val imported: Int,
    val skipped: Int
)

class SonderViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val dao = SonderDatabase.getInstance(application).dailyEntryDao()
    private val prefs = SonderPreferences(application)
    val toneRouter = LightweightLexiconToneRouter()

    val entries = dao.observeAllEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags = dao.observeAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rainRoomEntries = dao.observeRainRoomEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val goodDayCount = dao.observeGoodDayCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val firstEntryIso = dao.observeFirstEntryIsoDate()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings = prefs.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun todayIso(): String = DateDisplayFormatter.toStorageIso(LocalDate.now())

    // -----------------------------------------------------------------
    // Saving
    // -----------------------------------------------------------------

    suspend fun saveEntry(
        date: LocalDate,
        state: DayState?,
        countAsGood: Boolean,
        reason: String,
        sparks: Set<String>,
        existing: DailyEntryEntity?,
        suggestedState: DayState? = null
    ): Long = withContext(Dispatchers.IO) {
        val isRainRoom = state == DayState.HEAVY && !countAsGood
        val trimmed = reason.trim()
        val entry = DailyEntryEntity(
            id = existing?.id ?: 0L,
            localDate = DateDisplayFormatter.toStorageIso(date),
            timezoneId = DateDisplayFormatter.currentTimezoneId(),
            dayState = state,
            isGoodDay = countAsGood,
            isRainRoom = isRainRoom,
            reasonText = trimmed.ifBlank { null },
            mlSuggestedState = suggestedState ?: existing?.mlSuggestedState,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val dismissed = tags.value
            .filter { !it.userConfirmed }
            .map { it.tag.lowercase() }
            .toSet()
        val sparkTags = sparks.map { tag ->
            EntryTagEntity(entryId = 0L, tag = tag, tagType = "SPARK", source = "USER")
        }
        val autoTags = EntityExtractor.extract(trimmed, sparks)
            .filter { it.tag.lowercase() !in dismissed }
        val entryId = dao.saveEntryWithTags(entry, sparkTags + autoTags)
        storeEmbedding(entryId, trimmed, sparks)
        entryId
    }

    private suspend fun storeEmbedding(entryId: Long, reason: String, sparks: Set<String>) {
        val source = (reason + " " + sparks.joinToString(" ")).trim()
        if (source.isBlank()) {
            dao.deleteEmbedding(entryId)
            return
        }
        val vector = EmbeddingEngine.embed(source)
        if (vector.all { it == 0f }) return
        dao.upsertEmbedding(
            EntryEmbeddingEntity(
                entryId = entryId,
                modelVersion = MODEL_VERSION,
                vectorBlob = VectorMath.floatArrayToBytes(vector)
            )
        )
    }

    suspend fun deleteEntry(entry: DailyEntryEntity) = withContext(Dispatchers.IO) {
        dao.deleteEntry(entry.id)
    }

    suspend fun restoreDeleted(entry: DailyEntryEntity, entryTags: List<EntryTagEntity>) =
        withContext(Dispatchers.IO) {
            dao.saveEntryWithTags(entry, entryTags)
            storeEmbedding(
                entry.id,
                entry.reasonText.orEmpty(),
                entryTags.filter { it.tagType == "SPARK" }.map { it.tag }.toSet()
            )
        }

    suspend fun letGo(entry: DailyEntryEntity) = withContext(Dispatchers.IO) {
        val cleared = entry.copy(reasonText = null, updatedAt = System.currentTimeMillis())
        val existingTags = dao.getTagsForEntry(entry.id)
        dao.saveEntryWithTags(cleared, existingTags)
        dao.deleteEmbedding(entry.id)
    }

    // -----------------------------------------------------------------
    // Semantic Heavy-Day Rescue
    // -----------------------------------------------------------------

    suspend fun findRescue(heavy: DailyEntryEntity): RescueMemory? = withContext(Dispatchers.IO) {
        val text = heavy.reasonText?.trim().orEmpty()
        if (text.isBlank()) return@withContext null
        val candidates = dao.getGoodMemories()
            .filter { it.id != heavy.id && !it.reasonText.isNullOrBlank() }
        if (candidates.isEmpty()) return@withContext null

        val stored = dao.getAllEmbeddings().associateBy { it.entryId }
        val query = EmbeddingEngine.embed(text)
        if (query.all { it == 0f }) return@withContext null

        var best: RescueMemory? = null
        candidates.forEach { candidate ->
            val vector = stored[candidate.id]
                ?.takeIf { it.modelVersion == MODEL_VERSION }
                ?.let { VectorMath.bytesToFloatArray(it.vectorBlob) }
                ?: EmbeddingEngine.embed(candidate.reasonText.orEmpty())
            if (vector.size != query.size || vector.all { it == 0f }) return@forEach
            val score = VectorMath.cosineSimilarity(query, vector)
            if (best == null || score > best!!.score) {
                best = RescueMemory(candidate, score)
            }
        }
        best?.takeIf { it.score >= 0.10f }
    }

    // -----------------------------------------------------------------
    // Memory Whisper
    // -----------------------------------------------------------------

    suspend fun findWhisper(): DailyEntryEntity? = withContext(Dispatchers.IO) {
        val current = prefs.settings.first()
        val cooldownIso = current.lastWhisperIso
        if (cooldownIso != null) {
            val last = runCatching { DateDisplayFormatter.fromStorageIso(cooldownIso) }.getOrNull()
            if (last != null && ChronoUnit.DAYS.between(last, LocalDate.now()) < 30) {
                return@withContext null
            }
        }
        val memories = dao.getGoodMemories().filter { it.reasonText?.isNotBlank() == true }
        if (memories.size < 15) return@withContext null

        val today = LocalDate.now()
        val seasonal = memories.filter { memory ->
            val date = runCatching { DateDisplayFormatter.fromStorageIso(memory.localDate) }.getOrNull()
            date != null && date.month == today.month && kotlin.math.abs(date.dayOfMonth - today.dayOfMonth) <= 10
        }
        val pool = if (seasonal.isNotEmpty()) seasonal else memories
        val chosen = pool.random()
        prefs.setLastWhisperIso(DateDisplayFormatter.toStorageIso(today))
        chosen
    }

    // -----------------------------------------------------------------
    // Settings
    // -----------------------------------------------------------------

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun enableLock(pin: String) {
        viewModelScope.launch {
            val salt = PinHasher.newSalt()
            prefs.setPin(PinHasher.hash(pin, salt), salt)
            prefs.setAppLock(true)
        }
    }

    fun disableLock() {
        viewModelScope.launch { prefs.setAppLock(false) }
    }

    fun updatePin(pin: String) {
        viewModelScope.launch {
            val salt = PinHasher.newSalt()
            prefs.setPin(PinHasher.hash(pin, salt), salt)
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val current = prefs.settings.first()
        return PinHasher.verify(pin, current.pinSalt, current.pinHash)
    }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            prefs.setReminder(enabled, hour, minute)
            if (enabled) {
                ReminderScheduler.schedule(app, hour, minute)
            } else {
                ReminderScheduler.cancel(app)
            }
        }
    }

    fun rescheduleReminderIfNeeded() {
        viewModelScope.launch {
            val current = prefs.settings.first()
            if (current.reminderEnabled) {
                ReminderScheduler.schedule(app, current.reminderHour, current.reminderMinute)
            }
        }
    }

    // -----------------------------------------------------------------
    // Export / restore
    // -----------------------------------------------------------------

    suspend fun exportCsv(uri: Uri) = withContext(Dispatchers.IO) {
        BackupManager.exportCsv(app, uri, dao.getAllEntries(), dao.getAllTags())
    }

    suspend fun exportEncrypted(uri: Uri, password: String) = withContext(Dispatchers.IO) {
        BackupManager.exportEncrypted(
            app,
            uri,
            dao.getAllEntries(),
            dao.getAllTags(),
            password
        )
        prefs.markBackupNow()
    }

    suspend fun restoreEncrypted(uri: Uri, password: String): RestoreReport =
        withContext(Dispatchers.IO) {
            val imported = BackupManager.restoreEncrypted(app, uri, password)
            var restored = 0
            imported.forEach { item ->
                val existing = dao.getByDate(item.entry.localDate)
                if (existing != null && existing.updatedAt >= item.entry.updatedAt) {
                    return@forEach
                }
                dao.saveEntryWithTags(item.entry.copy(id = 0L), item.tags)
                val saved = dao.getByDate(item.entry.localDate)
                if (saved != null) {
                    storeEmbedding(
                        saved.id,
                        saved.reasonText.orEmpty(),
                        item.tags.filter { it.tagType == "SPARK" }.map { it.tag }.toSet()
                    )
                }
                restored++
            }
            RestoreReport(restored, imported.size - restored)
        }

    suspend fun deleteAllData() = withContext(Dispatchers.IO) {
        dao.deleteAllEntries()
        prefs.markBackupNow(0L)
    }

    suspend fun tagsForEntry(entryId: Long): List<EntryTagEntity> = withContext(Dispatchers.IO) {
        dao.getTagsForEntry(entryId)
    }

    suspend fun getEntry(isoDate: String): DailyEntryEntity? = withContext(Dispatchers.IO) {
        dao.getByDate(isoDate)
    }

    companion object {
        const val MODEL_VERSION = "sonder-hash-384-v1"
    }
}
