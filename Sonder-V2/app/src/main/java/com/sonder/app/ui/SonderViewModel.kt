package com.sonder.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sonder.app.data.local.SonderDatabase
import com.sonder.app.data.local.entity.ChapterEntity
import com.sonder.app.data.local.entity.EmergentDayWeather
import com.sonder.app.data.local.entity.MomentEmbeddingEntity
import com.sonder.app.data.local.entity.MomentEntity
import com.sonder.app.data.local.entity.MomentTagEntity
import com.sonder.app.data.local.entity.OpenWhenEnvelopeEntity
import com.sonder.app.data.prefs.SonderPreferences
import com.sonder.app.data.prefs.SonderSettings
import com.sonder.app.data.prefs.ThemeMode
import com.sonder.app.ml.EmbeddingEngine
import com.sonder.app.ml.EntityExtractor
import com.sonder.app.ml.VectorMath
import com.sonder.app.reminder.ReminderScheduler
import com.sonder.app.security.PinHasher
import com.sonder.app.ui.capture.CaptureDraft
import com.sonder.app.util.AudioNoteManager
import com.sonder.app.util.BackupManager
import com.sonder.app.util.DateDisplayFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

enum class Screen(val route: String, val label: String) {
    HOME("home", "Home"),
    CALENDAR("calendar", "Calendar"),
    THREADS("threads", "Threads"),
    RAIN_ROOM("rain_room", "Rain Room"),
    SETTINGS("settings", "Settings")
}

data class ThreadSummary(
    val name: String,
    val normalizedName: String,
    val tagType: String,
    val count: Int,
    val lastSeenIso: String,
    val recentExcerpts: List<Pair<String, String>>,
    val hasUnconfirmed: Boolean
)

data class SonderUiState(
    val goodDayCount: Int = 0,
    val totalMomentCount: Int = 0,
    val firstMomentDate: LocalDate? = null,
    val allMoments: List<MomentEntity> = emptyList(),
    val todayMoments: List<MomentEntity> = emptyList(),
    val yesterdayHasEntry: Boolean = false,
    val rainRoomMoments: List<MomentEntity> = emptyList(),
    val unsentMoments: List<MomentEntity> = emptyList(),
    val allTags: List<MomentTagEntity> = emptyList(),
    val chapters: List<ChapterEntity> = emptyList(),
    val envelopes: List<OpenWhenEnvelopeEntity> = emptyList(),
    val unlockedEnvelopes: List<OpenWhenEnvelopeEntity> = emptyList(),
    val threads: List<ThreadSummary> = emptyList(),
    val settings: SonderSettings = SonderSettings(),
    val isUnlocked: Boolean = true,
    val showWelcomeBack: Boolean = false,
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDayMoments: List<MomentEntity> = emptyList(),
    val captureDate: LocalDate? = null,
    val captureExistingMoment: MomentEntity? = null,
    val captureForceRainRoom: Boolean = false,
    val statusMessage: String? = null,
    val semanticMatches: List<MomentEntity> = emptyList()
)

class SonderViewModel(app: Application) : AndroidViewModel(app) {

    private val db = SonderDatabase.getInstance(app)
    private val dao = db.momentDao()
    private val prefs = SonderPreferences(app)
    private val audioManager = AudioNoteManager(app)

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _captureDate = MutableStateFlow<LocalDate?>(null)
    private val _captureExistingMoment = MutableStateFlow<MomentEntity?>(null)
    private val _captureForceRainRoom = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _isUnlocked = MutableStateFlow<Boolean?>(null)
    private val _semanticMatches = MutableStateFlow<List<MomentEntity>>(emptyList())
    private val _showWelcomeBack = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val initialSettings = prefs.settings.first()
            if (_isUnlocked.value == null) {
                _isUnlocked.value = !initialSettings.appLockEnabled || initialSettings.pinHash == null
            }
            val todayIso = DateDisplayFormatter.toStorageIso(LocalDate.now())
            val yesterdayIso = DateDisplayFormatter.toStorageIso(LocalDate.now().minusDays(1))
            val latestIso = withContext(Dispatchers.IO) { dao.getLatestMomentIsoDate() }
            if (latestIso != null && latestIso < yesterdayIso && initialSettings.lastWelcomeDateIso != todayIso) {
                val daysAway = ChronoUnit.DAYS.between(
                    DateDisplayFormatter.fromStorageIso(latestIso),
                    LocalDate.now()
                )
                if (daysAway >= 2) {
                    _showWelcomeBack.value = true
                }
            }
        }
    }

    val uiState: StateFlow<SonderUiState> = combine(
        dao.observeGoodDayCount(),
        dao.observeTotalMomentCount(),
        dao.observeFirstMomentIsoDate(),
        dao.observeAllMoments(),
        dao.observeRainRoomMoments(),
        dao.observeUnsentMoments(),
        dao.observeAllTags(),
        dao.observeChapters(),
        dao.observeEnvelopes(),
        prefs.settings
    ) { values ->
        val goodDays = values[0] as Int
        val totalMoments = values[1] as Int
        val firstIso = values[2] as String?
        @Suppress("UNCHECKED_CAST")
        val allMoments = values[3] as List<MomentEntity>
        @Suppress("UNCHECKED_CAST")
        val rainMoments = values[4] as List<MomentEntity>
        @Suppress("UNCHECKED_CAST")
        val unsentMoments = values[5] as List<MomentEntity>
        @Suppress("UNCHECKED_CAST")
        val allTags = values[6] as List<MomentTagEntity>
        @Suppress("UNCHECKED_CAST")
        val chapters = values[7] as List<ChapterEntity>
        @Suppress("UNCHECKED_CAST")
        val envelopes = values[8] as List<OpenWhenEnvelopeEntity>
        val settings = values[9] as SonderSettings

        val todayIso = DateDisplayFormatter.toStorageIso(LocalDate.now())
        val yesterdayIso = DateDisplayFormatter.toStorageIso(LocalDate.now().minusDays(1))
        val selectedIso = DateDisplayFormatter.toStorageIso(_selectedDate.value)

        val momentsById = allMoments.associateBy { it.id }
        val threads = buildThreads(allTags, momentsById)
        val unlockedEnvs = envelopes.filter { !it.isOpened && it.unlockDate <= todayIso }

        SonderUiState(
            goodDayCount = goodDays,
            totalMomentCount = totalMoments,
            firstMomentDate = firstIso?.let { DateDisplayFormatter.fromStorageIso(it) },
            allMoments = allMoments,
            todayMoments = allMoments.filter { it.localDate == todayIso },
            yesterdayHasEntry = allMoments.any { it.localDate == yesterdayIso },
            rainRoomMoments = rainMoments,
            unsentMoments = unsentMoments,
            allTags = allTags,
            chapters = chapters,
            envelopes = envelopes,
            unlockedEnvelopes = unlockedEnvs,
            threads = threads,
            settings = settings,
            isUnlocked = _isUnlocked.value ?: (!settings.appLockEnabled || settings.pinHash == null),
            showWelcomeBack = _showWelcomeBack.value,
            selectedDate = _selectedDate.value,
            selectedDayMoments = allMoments.filter { it.localDate == selectedIso },
            captureDate = _captureDate.value,
            captureExistingMoment = _captureExistingMoment.value,
            captureForceRainRoom = _captureForceRainRoom.value,
            statusMessage = _statusMessage.value,
            semanticMatches = _semanticMatches.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SonderUiState()
    )

    private fun buildThreads(
        tags: List<MomentTagEntity>,
        momentsById: Map<Long, MomentEntity>
    ): List<ThreadSummary> {
        return tags
            .filter { it.userConfirmed || it.source == "ML_NOTICED" }
            .groupBy { "\${it.tagType}:\${it.normalizedName}" }
            .mapNotNull { (_, group) ->
                val confirmedOrNoticed = group.filter { it.userConfirmed || it.source == "ML_NOTICED" }
                if (confirmedOrNoticed.isEmpty()) return@mapNotNull null
                val canonical = confirmedOrNoticed.firstOrNull { it.userConfirmed }?.tagName
                    ?: confirmedOrNoticed.first().tagName
                val type = confirmedOrNoticed.first().tagType
                val norm = confirmedOrNoticed.first().normalizedName
                val linkedMoments = confirmedOrNoticed.mapNotNull { momentsById[it.momentId] }
                    .distinctBy { it.id }
                    .sortedByDescending { it.localDate }
                if (linkedMoments.isEmpty()) return@mapNotNull null
                val excerpts = linkedMoments.take(3).map { m ->
                    DateDisplayFormatter.isoToDisplayString(m.localDate) to
                        (m.bodyText?.takeIf { it.isNotBlank() } ?: "Audio moment")
                }
                ThreadSummary(
                    name = canonical,
                    normalizedName = norm,
                    tagType = type,
                    count = linkedMoments.size,
                    lastSeenIso = linkedMoments.first().localDate,
                    recentExcerpts = excerpts,
                    hasUnconfirmed = confirmedOrNoticed.any { !it.userConfirmed }
                )
            }
            .sortedByDescending { it.count }
    }

    fun dismissWelcomeBack() {
        _showWelcomeBack.value = false
        viewModelScope.launch {
            prefs.setLastWelcomeDateIso(DateDisplayFormatter.toStorageIso(LocalDate.now()))
        }
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun openCapture(
        date: LocalDate = LocalDate.now(),
        existing: MomentEntity? = null,
        forceRainRoom: Boolean = false
    ) {
        _captureDate.value = date
        _captureExistingMoment.value = existing
        _captureForceRainRoom.value = forceRainRoom
    }

    fun closeCapture() {
        _captureDate.value = null
        _captureExistingMoment.value = null
        _captureForceRainRoom.value = false
    }

    fun markSwipeHintSeen() {
        viewModelScope.launch { prefs.markSwipeHintSeen() }
    }

    fun saveMoment(draft: CaptureDraft) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val existing = _captureExistingMoment.value
                val effectiveDate = if (existing == null && draft.date == LocalDate.now()) {
                    val yesterdayIso = DateDisplayFormatter.toStorageIso(LocalDate.now().minusDays(1))
                    val yesterdayHas = dao.getAllMomentsIncludingUnsent().any { it.localDate == yesterdayIso }
                    if (DateDisplayFormatter.shouldOfferMidnightGrace(LocalTime.now(), yesterdayHas)) {
                        LocalDate.now().minusDays(1)
                    } else {
                        draft.date
                    }
                } else {
                    draft.date
                }
                val isoDate = DateDisplayFormatter.toStorageIso(effectiveDate)

                if (draft.sealUntilDate != null) {
                    dao.upsertEnvelope(
                        OpenWhenEnvelopeEntity(
                            titleHint = draft.sealHint,
                            unlockDate = DateDisplayFormatter.toStorageIso(draft.sealUntilDate),
                            bodyText = draft.bodyText.ifBlank { null },
                            audioFileName = draft.audioFileName,
                            isOpened = false,
                            createdAt = now
                        )
                    )
                    return@withContext
                }

                val isRain = (draft.weatherScore ?: 0f) <= -0.25f || _captureForceRainRoom.value
                val entity = MomentEntity(
                    id = existing?.id ?: 0L,
                    localDate = isoDate,
                    timezoneId = existing?.timezoneId ?: DateDisplayFormatter.currentTimezoneId(),
                    weatherScore = draft.weatherScore,
                    isRainRoom = isRain,
                    isUnsent = draft.isUnsent,
                    countsAsGood = if (draft.isUnsent) false else draft.countAsGood,
                    bodyText = draft.bodyText.ifBlank { null },
                    audioFileName = draft.audioFileName,
                    audioDurationMs = draft.audioDurationMs,
                    mlSuggestedWeather = draft.suggestedWeather,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )

                val userTags = draft.sparks.map { spark ->
                    MomentTagEntity(
                        momentId = entity.id,
                        tagName = spark.trim(),
                        normalizedName = spark.trim().lowercase(),
                        tagType = "SPARK",
                        source = "USER",
                        userConfirmed = true
                    )
                }
                val noticedTags = EntityExtractor.extract(draft.bodyText, draft.sparks)
                val momentId = dao.saveMomentWithTags(entity, userTags + noticedTags)

                val embedSource = draft.bodyText.ifBlank { draft.sparks.joinToString(" ") }
                if (embedSource.isNotBlank()) {
                    val vec = EmbeddingEngine.embed(embedSource)
                    dao.upsertEmbedding(
                        MomentEmbeddingEntity(
                            momentId = momentId,
                            modelVersion = "sonder-hash-v2",
                            vectorBlob = VectorMath.floatArrayToBytes(vec),
                            computedAt = now
                        )
                    )
                }
            }
            closeCapture()
            _statusMessage.value = "Saved"
        }
    }

    fun deleteMoment(moment: MomentEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                moment.audioFileName?.let { audioManager.deleteAudio(it) }
                dao.deleteMomentById(moment.id)
            }
            closeCapture()
            _statusMessage.value = "Deleted"
        }
    }

    fun confirmTag(tagId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { dao.confirmTag(tagId) }
        }
    }

    fun dismissTag(tagId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { dao.dismissTag(tagId) }
        }
    }

    fun renameTagGlobally(oldNormalized: String, newName: String) {
        val clean = newName.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.renameTagGlobally(oldNormalized, clean, clean.lowercase())
            }
            _statusMessage.value = "Renamed to \$clean"
        }
    }

    fun createChapter(title: String, colorHex: String, patternStyle: String, plannedEndDate: String?) {
        val clean = title.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.upsertChapter(
                    ChapterEntity(
                        title = clean,
                        colorHex = colorHex,
                        patternStyle = patternStyle,
                        startDate = DateDisplayFormatter.toStorageIso(LocalDate.now()),
                        plannedEndDate = plannedEndDate?.takeIf { it.isNotBlank() }
                    )
                )
            }
            _statusMessage.value = "Chapter started"
        }
    }

    fun closeChapter(chapter: ChapterEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.updateChapter(
                    chapter.copy(endDate = DateDisplayFormatter.toStorageIso(LocalDate.now()))
                )
            }
            _statusMessage.value = "Chapter closed"
        }
    }

    fun openEnvelope(envelope: OpenWhenEnvelopeEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.updateEnvelope(envelope.copy(isOpened = true))
            }
        }
    }

    fun searchSemantic(query: String) {
        if (query.isBlank()) {
            _semanticMatches.value = emptyList()
            return
        }
        viewModelScope.launch {
            val matches = withContext(Dispatchers.IO) {
                val qVec = EmbeddingEngine.embed(query)
                val embeddings = dao.getAllEmbeddings()
                val momentsById = dao.observeAllMoments().first().associateBy { it.id }
                embeddings
                    .mapNotNull { emb ->
                        val moment = momentsById[emb.momentId] ?: return@mapNotNull null
                        val sim = VectorMath.cosineSimilarity(qVec, VectorMath.bytesToFloatArray(emb.vectorBlob))
                        moment to sim
                    }
                    .filter { it.second > 0.08f }
                    .sortedByDescending { it.second }
                    .take(8)
                    .map { it.first }
            }
            _semanticMatches.value = matches
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setupPin(pin: String) {
        if (pin.length < 4) return
        viewModelScope.launch {
            val salt = PinHasher.newSalt()
            val hash = PinHasher.hash(pin, salt)
            prefs.setPin(hash, salt)
            prefs.setAppLock(true)
            _isUnlocked.value = true
            _statusMessage.value = "App lock enabled"
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setAppLock(enabled)
            if (!enabled) _isUnlocked.value = true
        }
    }

    fun unlockWithPin(pin: String): Boolean {
        val current = uiState.value.settings
        val ok = PinHasher.verify(pin, current.pinSalt, current.pinHash)
        if (ok) _isUnlocked.value = true
        return ok
    }

    fun unlockWithBiometric() {
        _isUnlocked.value = true
    }

    fun lockNow() {
        if (uiState.value.settings.appLockEnabled && uiState.value.settings.pinHash != null) {
            _isUnlocked.value = false
        }
    }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            prefs.setReminder(enabled, hour, minute)
            val ctx = getApplication<Application>()
            if (enabled) ReminderScheduler.schedule(ctx, hour, minute)
            else ReminderScheduler.cancel(ctx)
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val moments = dao.getAllMomentsIncludingUnsent()
                val tags = dao.getAllTags()
                BackupManager.exportCsv(getApplication(), uri, moments, tags)
                prefs.markBackupNow()
            }
            _statusMessage.value = "Exported CSV"
        }
    }

    fun exportEncrypted(uri: Uri, password: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val moments = dao.getAllMomentsIncludingUnsent()
                val tags = dao.getAllTags()
                BackupManager.exportEncrypted(getApplication(), uri, moments, tags, password)
                prefs.markBackupNow()
            }
            _statusMessage.value = "Saved encrypted backup"
        }
    }

    fun restoreEncrypted(uri: Uri, password: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val imported = BackupManager.restoreEncrypted(getApplication(), uri, password)
                    imported.forEach { item ->
                        dao.saveMomentWithTags(item.moment, item.tags)
                    }
                    imported.size
                }
            }
            _statusMessage.value = result.fold(
                onSuccess = { "Restored \$it moments" },
                onFailure = { "Restore failed — check password or file" }
            )
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val moments = dao.getAllMomentsIncludingUnsent()
                moments.forEach { it.audioFileName?.let { f -> audioManager.deleteAudio(f) } }
                dao.deleteAllMoments()
            }
            _statusMessage.value = "All entries deleted"
        }
    }

    fun dayWeather(moments: List<MomentEntity>): EmergentDayWeather? =
        EmergentDayWeather.derive(moments)
}
