package com.sonder.app.ui

import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sonder.app.MainActivity
import com.sonder.app.R
import com.sonder.app.SonderApp
import com.sonder.app.data.local.SonderDatabase
import com.sonder.app.data.local.entity.ChapterEntity
import com.sonder.app.data.local.entity.EmergentDayWeather
import com.sonder.app.data.local.entity.MomentEmbeddingEntity
import com.sonder.app.data.local.entity.MomentEntity
import com.sonder.app.data.local.entity.MomentPhotoEntity
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
import com.sonder.app.util.ChapterRecap
import com.sonder.app.util.DateDisplayFormatter
import com.sonder.app.util.RecapCardRenderer
import com.sonder.app.util.PhotoVaultManager
import com.sonder.app.widget.SonderWidgetProvider
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
    val momentIds: List<Long>,
    val hasUnconfirmed: Boolean
)

/**
 * A "We noticed…" card. These are deliberately soft: nothing is written to the
 * user's memory until they tap confirm.
 */
data class Insight(
    val name: String,
    val detail: String,
    val tagType: String,
    val isEarly: Boolean = false
)

data class RestoreReport(
    val restored: Int,
    val chapters: Int,
    val envelopes: Int,
    val skipped: Int
)

data class SonderUiState(
    val goodDayCount: Int = 0,
    val totalMomentCount: Int = 0,
    val firstMomentDate: LocalDate? = null,
    val allMoments: List<MomentEntity> = emptyList(),
    val todayMoments: List<MomentEntity> = emptyList(),
    val yesterdayHasMoment: Boolean = false,
    val rainRoomMoments: List<MomentEntity> = emptyList(),
    val unsentMoments: List<MomentEntity> = emptyList(),
    val allTags: List<MomentTagEntity> = emptyList(),
    val photosByMoment: Map<Long, List<MomentPhotoEntity>> = emptyMap(),
    val chapters: List<ChapterEntity> = emptyList(),
    val envelopes: List<OpenWhenEnvelopeEntity> = emptyList(),
    val unlockedEnvelopes: List<OpenWhenEnvelopeEntity> = emptyList(),
    val threads: List<ThreadSummary> = emptyList(),
    val insights: List<Insight> = emptyList(),
    val settings: SonderSettings = SonderSettings(),
    val showWelcomeBack: Boolean = false,
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDayMoments: List<MomentEntity> = emptyList(),
    val captureDate: LocalDate? = null,
    val captureExistingMoment: MomentEntity? = null,
    val captureForceRainRoom: Boolean = false,
    val statusMessage: String? = null,
    val semanticMatches: List<MomentEntity> = emptyList(),
    val reReadMoment: MomentEntity? = null,
    val vaultUnlocked: Boolean = false,
    val vaultPinConfigured: Boolean = false
)

class SonderViewModel(app: Application) : AndroidViewModel(app) {

    private val db = SonderDatabase.getInstance(app)
    private val dao = db.momentDao()
    private val prefs = SonderPreferences(app)
    val audioManager = AudioNoteManager(app)
    val photoManager = PhotoVaultManager(app)

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _captureDate = MutableStateFlow<LocalDate?>(null)
    private val _captureExistingMoment = MutableStateFlow<MomentEntity?>(null)
    private val _captureForceRainRoom = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _semanticMatches = MutableStateFlow<List<MomentEntity>>(emptyList())
    private val _showWelcomeBack = MutableStateFlow(false)
    private val _reReadMoment = MutableStateFlow<MomentEntity?>(null)
    private val _vaultUnlocked = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val initial = prefs.settings.first()
            _vaultUnlocked.value = !initial.vaultLockEnabled || initial.vaultPinHash == null
            checkWelcomeBack(initial)
            notifyUnlockedEnvelopes(initial)
        }
    }

    private suspend fun checkWelcomeBack(settings: SonderSettings) {
        val todayIso = DateDisplayFormatter.toStorageIso(LocalDate.now())
        if (settings.lastWelcomeIso == todayIso) return
        val latestIso = withContext(Dispatchers.IO) { dao.getLatestMomentIsoDate() } ?: return
        val daysAway = runCatching {
            ChronoUnit.DAYS.between(
                DateDisplayFormatter.fromStorageIso(latestIso),
                LocalDate.now()
            )
        }.getOrDefault(0L)
        if (daysAway >= 5L) _showWelcomeBack.value = true
    }

    private suspend fun notifyUnlockedEnvelopes(settings: SonderSettings) {
        val todayIso = DateDisplayFormatter.toStorageIso(LocalDate.now())
        if (settings.lastEnvelopeNoticeIso == todayIso) return
        val ready = withContext(Dispatchers.IO) {
            dao.getAllEnvelopes().filter { !it.isOpened && it.unlockDate <= todayIso }
        }
        if (ready.isEmpty()) return
        val context = getApplication<Application>()
        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, SonderApp.CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("An envelope is ready to open")
            .setContentText(
                if (ready.size == 1) {
                    "From ${DateDisplayFormatter.isoToDisplayString(ready.first().unlockDate)}"
                } else {
                    "${ready.size} sealed notes are waiting"
                }
            )
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setShowWhen(false)
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                .notify(ENVELOPE_NOTIFICATION_ID, notification)
        }
        prefs.setLastEnvelopeNoticeIso(todayIso)
    }

    val uiState: StateFlow<SonderUiState> = combine(
        dao.observeGoodDayCount(),
        dao.observeTotalMomentCount(),
        dao.observeFirstMomentIsoDate(),
        dao.observeAllMoments(),
        dao.observeRainRoomMoments(),
        dao.observeUnsentMoments(),
        dao.observeAllTags(),
        dao.observeAllPhotos(),
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
        val allPhotos = values[7] as List<MomentPhotoEntity>
        @Suppress("UNCHECKED_CAST")
        val chapters = values[8] as List<ChapterEntity>
        @Suppress("UNCHECKED_CAST")
        val envelopes = values[9] as List<OpenWhenEnvelopeEntity>
        val settings = values[10] as SonderSettings

        val todayIso = DateDisplayFormatter.toStorageIso(LocalDate.now())
        val yesterdayIso = DateDisplayFormatter.toStorageIso(LocalDate.now().minusDays(1))
        val selectedIso = DateDisplayFormatter.toStorageIso(_selectedDate.value)
        val momentsById = allMoments.associateBy { it.id }
        val visibleTags = allTags.filter { tag -> momentsById.containsKey(tag.momentId) }

        SonderUiState(
            goodDayCount = goodDays,
            totalMomentCount = totalMoments,
            firstMomentDate = firstIso?.let { runCatching { DateDisplayFormatter.fromStorageIso(it) }.getOrNull() },
            allMoments = allMoments,
            todayMoments = allMoments.filter { it.localDate == todayIso },
            yesterdayHasMoment = allMoments.any { it.localDate == yesterdayIso },
            rainRoomMoments = rainMoments,
            unsentMoments = unsentMoments,
            allTags = visibleTags,
            photosByMoment = allPhotos.groupBy { it.momentId },
            chapters = chapters,
            envelopes = envelopes,
            unlockedEnvelopes = envelopes.filter { !it.isOpened && it.unlockDate <= todayIso },
            threads = buildThreads(visibleTags, momentsById),
            insights = buildInsights(allMoments, visibleTags),
            settings = settings,
            showWelcomeBack = _showWelcomeBack.value,
            selectedDate = _selectedDate.value,
            selectedDayMoments = allMoments.filter { it.localDate == selectedIso },
            captureDate = _captureDate.value,
            captureExistingMoment = _captureExistingMoment.value,
            captureForceRainRoom = _captureForceRainRoom.value,
            statusMessage = _statusMessage.value,
            semanticMatches = _semanticMatches.value,
            reReadMoment = _reReadMoment.value,
            vaultUnlocked = _vaultUnlocked.value,
            vaultPinConfigured = settings.vaultLockEnabled && settings.vaultPinHash != null
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
            .filter { it.userConfirmed || it.source == MomentTagEntity.SOURCE_ML_NOTICED }
            .groupBy { "${it.tagType}:${it.normalizedName}" }
            .mapNotNull { (_, group) ->
                val canonical = group.firstOrNull { it.userConfirmed }?.tagName ?: group.first().tagName
                val linked = group.mapNotNull { momentsById[it.momentId] }
                    .distinctBy { it.id }
                    .sortedByDescending { it.localDate }
                if (linked.isEmpty()) return@mapNotNull null
                ThreadSummary(
                    name = canonical,
                    normalizedName = group.first().normalizedName,
                    tagType = group.first().tagType,
                    count = linked.size,
                    lastSeenIso = linked.first().localDate,
                    recentExcerpts = linked.take(3).map { m ->
                        DateDisplayFormatter.isoToDisplayString(m.localDate) to
                            (m.bodyText?.takeIf { it.isNotBlank() } ?: "Audio moment")
                    },
                    momentIds = linked.map { it.id },
                    hasUnconfirmed = group.any { !it.userConfirmed }
                )
            }
            .sortedByDescending { it.count }
    }

    /**
     * Guardrailed observations: at least 8 matching Moments and a 15% lift over
     * the user's own sunlit baseline. Nothing is written until confirmed.
     */
    private fun buildInsights(
        moments: List<MomentEntity>,
        tags: List<MomentTagEntity>
    ): List<Insight> {
        if (moments.size < 8) return emptyList()
        val momentsById = moments.associateBy { it.id }
        val sunlit = moments.filter { (it.weatherScore ?: 0f) > 0.24f || it.countsAsGood }
        if (sunlit.isEmpty()) return emptyList()
        val baseline = sunlit.size.toFloat() / moments.size

        val byTag = tags
            .filter { it.userConfirmed }
            .groupBy { it.normalizedName to it.tagType }

        return byTag.mapNotNull { (key, group) ->
            val (normalized, type) = key
            val linked = group.mapNotNull { momentsById[it.momentId] }.distinctBy { it.id }
            if (linked.size < 8) return@mapNotNull null
            val sunnyWithTag = linked.count { (it.weatherScore ?: 0f) > 0.24f || it.countsAsGood }
            val lift = sunnyWithTag.toFloat() / linked.size
            if (lift < baseline * 1.15f) return@mapNotNull null
            val name = group.firstOrNull { it.userConfirmed }?.tagName ?: normalized
            Insight(
                name = name,
                detail = "$name appears on ${(lift * 100).toInt()}% of your sunlit moments.",
                tagType = type,
                isEarly = moments.size < 30
            )
        }.distinctBy { it.name.lowercase() }.take(2)
    }

    // ------------------------------------------------------------------
    // Capture
    // ------------------------------------------------------------------

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

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }

    fun saveMoment(draft: CaptureDraft) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { persistMoment(draft) }
            }
            closeCapture()
            refreshWidget()
            _statusMessage.value = saved.fold(
                onSuccess = { result ->
                    when {
                        result == SaveResult.SEALED -> "Envelope sealed."
                        draft.isUnsent -> "Kept in your unsent drawer."
                        draft.weatherScore != null && draft.weatherScore <= -0.25f -> "Set down in the Rain Room."
                        else -> "Saved to your vault."
                    }
                },
                onFailure = { "Could not save — try again." }
            )
        }
    }

    private suspend fun persistMoment(draft: CaptureDraft): SaveResult {
        val now = System.currentTimeMillis()
        val existing = _captureExistingMoment.value
        val effectiveDate = resolveDate(draft, existing)
        val isoDate = DateDisplayFormatter.toStorageIso(effectiveDate)

        if (draft.sealUntilDate != null) {
            dao.upsertEnvelope(
                OpenWhenEnvelopeEntity(
                    titleHint = draft.sealHint?.takeIf { it.isNotBlank() },
                    unlockDate = DateDisplayFormatter.toStorageIso(draft.sealUntilDate),
                    bodyText = draft.bodyText.ifBlank { null },
                    audioFileName = draft.audioFileName,
                    isOpened = false,
                    createdAt = now
                )
            )
            return SaveResult.SEALED
        }

        val weather = draft.weatherScore
        val isRain = !draft.isUnsent && ((weather ?: 0f) <= -0.25f || _captureForceRainRoom.value)
        val entity = MomentEntity(
            id = existing?.id ?: 0L,
            localDate = isoDate,
            timezoneId = existing?.timezoneId ?: DateDisplayFormatter.currentTimezoneId(),
            weatherScore = weather,
            isRainRoom = isRain,
            isUnsent = draft.isUnsent,
            countsAsGood = !draft.isUnsent && draft.countAsGood,
            bodyText = draft.bodyText.ifBlank { null },
            audioFileName = draft.audioFileName,
            audioDurationMs = draft.audioDurationMs,
            mlSuggestedWeather = draft.suggestedWeather,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )

        val dismissed = dao.getAllTags()
            .filter { !it.userConfirmed && it.source == MomentTagEntity.SOURCE_ML_NOTICED }
            .map { it.normalizedName }
            .toSet()

        val userTags = draft.sparks.map { spark ->
            MomentTagEntity(
                momentId = entity.id,
                tagName = spark.trim(),
                normalizedName = spark.trim().lowercase(),
                tagType = MomentTagEntity.TAG_TYPE_SPARK,
                source = MomentTagEntity.SOURCE_USER,
                userConfirmed = true
            )
        }.filter { it.tagName.isNotBlank() }

        val noticed = EntityExtractor.extract(draft.bodyText, draft.sparks)
            .filter { it.name.trim().lowercase() !in dismissed }
            .map { found ->
                MomentTagEntity(
                    momentId = entity.id,
                    tagName = found.name,
                    normalizedName = found.name.trim().lowercase(),
                    tagType = found.type,
                    source = MomentTagEntity.SOURCE_ML_NOTICED,
                    userConfirmed = false
                )
            }

        val momentId = dao.saveMomentWithTags(entity, userTags + noticed)

        val previousPhotos = if (existing != null) dao.getPhotosForMoment(existing.id) else emptyList()
        val keptFileNames = mutableSetOf<String>()
        val savedPhotos = draft.photos.mapNotNull { slot ->
            val fileName = slot.fileName
                ?: slot.uriString?.let { photoManager.copyFromUri(Uri.parse(it)) }
                ?: return@mapNotNull null
            keptFileNames += fileName
            MomentPhotoEntity(
                momentId = momentId,
                fileName = fileName,
                caption = slot.caption.trim().takeIf { it.isNotBlank() }
            )
        }
        dao.savePhotosForMoment(momentId, savedPhotos)
        previousPhotos
            .filter { it.fileName !in keptFileNames }
            .forEach { photoManager.deletePhoto(it.fileName) }

        val source = draft.bodyText.ifBlank { draft.sparks.joinToString(" ") }
        if (source.isNotBlank()) {
            val vector = EmbeddingEngine.embed(source)
            if (vector.any { it != 0f }) {
                dao.upsertEmbedding(
                    MomentEmbeddingEntity(
                        momentId = momentId,
                        modelVersion = MODEL_VERSION,
                        vectorBlob = VectorMath.floatArrayToBytes(vector),
                        computedAt = now
                    )
                )
            }
        } else {
            dao.deleteEmbedding(momentId)
        }
        return SaveResult.SAVED
    }

    private suspend fun resolveDate(draft: CaptureDraft, existing: MomentEntity?): LocalDate {
        if (existing != null) return draft.date
        if (draft.date != LocalDate.now()) return draft.date
        val yesterdayIso = DateDisplayFormatter.toStorageIso(LocalDate.now().minusDays(1))
        val yesterdayHas = dao.getAllMomentsIncludingUnsent().any { it.localDate == yesterdayIso }
        return if (DateDisplayFormatter.shouldOfferMidnightGrace(LocalTime.now(), yesterdayHas)) {
            LocalDate.now().minusDays(1)
        } else {
            draft.date
        }
    }

    private enum class SaveResult { SAVED, SEALED }

    fun deleteMoment(moment: MomentEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                moment.audioFileName?.let { audioManager.deleteAudio(it) }
                dao.getPhotosForMoment(moment.id).forEach { photoManager.deletePhoto(it.fileName) }
                dao.deleteMomentById(moment.id)
            }
            closeCapture()
            refreshWidget()
            _statusMessage.value = "Deleted."
        }
    }

    fun letGo(moment: MomentEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                moment.audioFileName?.let { audioManager.deleteAudio(it) }
                dao.getPhotosForMoment(moment.id).forEach { photoManager.deletePhoto(it.fileName) }
                dao.letGoOfMoment(moment)
            }
            closeCapture()
            refreshWidget()
            _statusMessage.value = "Let go. Only the quiet date remains."
        }
    }

    // ------------------------------------------------------------------
    // Actors / tags
    // ------------------------------------------------------------------

    fun confirmTag(tagId: Long) {
        viewModelScope.launch { withContext(Dispatchers.IO) { dao.confirmTag(tagId) } }
    }

    fun dismissTag(tagId: Long) {
        viewModelScope.launch { withContext(Dispatchers.IO) { dao.dismissTag(tagId) } }
    }

    fun renameTagGlobally(oldNormalized: String, newName: String) {
        val clean = newName.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.renameTagGlobally(oldNormalized, clean, clean.lowercase())
            }
            _statusMessage.value = "Renamed to $clean."
        }
    }

    fun mergeTag(sourceNormalized: String, targetNormalized: String) {
        if (sourceNormalized == targetNormalized) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { dao.mergeTags(sourceNormalized, targetNormalized) }
            _statusMessage.value = "Merged."
        }
    }

    // ------------------------------------------------------------------
    // Chapters
    // ------------------------------------------------------------------

    fun createChapter(
        title: String,
        colorHex: String,
        patternStyle: String,
        plannedEndDate: String?
    ) {
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
            _statusMessage.value = "Chapter started."
        }
    }

    fun closeChapter(chapter: ChapterEntity, endDate: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.updateChapter(
                    chapter.copy(endDate = DateDisplayFormatter.toStorageIso(endDate))
                )
            }
            _statusMessage.value = "Chapter closed."
        }
    }

    fun setChapterCover(chapter: ChapterEntity, uri: Uri) {
        viewModelScope.launch {
            val newName = withContext(Dispatchers.IO) {
                val copied = photoManager.copyFromUri(uri)
                if (copied != null) {
                    chapter.coverPhotoUri?.let { photoManager.deletePhoto(it) }
                }
                copied
            }
            if (newName == null) {
                _statusMessage.value = "Could not add that photo."
                return@launch
            }
            withContext(Dispatchers.IO) {
                dao.updateChapter(chapter.copy(coverPhotoUri = newName))
            }
            _statusMessage.value = "Chapter cover updated."
        }
    }

    fun chapterRecapText(chapter: ChapterEntity, onReady: (String, java.io.File?) -> Unit) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                ChapterRecap.build(
                    chapter = chapter,
                    moments = dao.getAllMomentsIncludingUnsent(),
                    tags = dao.getAllTags()
                )
            }
            val card = withContext(Dispatchers.IO) {
                runCatching {
                    RecapCardRenderer.render(getApplication(), chapter.title, text)
                }.getOrNull()
            }
            onReady(text, card)
        }
    }

    fun keepChapterGoing(chapter: ChapterEntity) {        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.updateChapter(
                    chapter.copy(lastCheckinDate = DateDisplayFormatter.toStorageIso(LocalDate.now()))
                )
            }
        }
    }

    fun deleteChapter(chapter: ChapterEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                chapter.coverPhotoUri?.let { photoManager.deletePhoto(it) }
                dao.deleteChapter(chapter.id)
            }
        }
    }

    // ------------------------------------------------------------------
    // Open When envelopes
    // ------------------------------------------------------------------

    fun openEnvelope(envelope: OpenWhenEnvelopeEntity) {
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                if (envelope.isOpened) return@withContext "Envelope already opened."
                val now = System.currentTimeMillis()
                val body = envelope.bodyText?.takeIf { it.isNotBlank() }
                if (body == null && envelope.audioFileName == null) {
                    dao.updateEnvelope(envelope.copy(isOpened = true))
                    return@withContext "Envelope opened. It was a quiet mark."
                }
                val moment = MomentEntity(
                    localDate = DateDisplayFormatter.toStorageIso(LocalDate.now()),
                    timezoneId = DateDisplayFormatter.currentTimezoneId(),
                    weatherScore = null,
                    isRainRoom = false,
                    isUnsent = false,
                    countsAsGood = false,
                    bodyText = body,
                    audioFileName = envelope.audioFileName,
                    createdAt = now,
                    updatedAt = now
                )
                val momentId = dao.saveMomentWithTags(moment, emptyList())
                if (!body.isNullOrBlank()) {
                    val vector = EmbeddingEngine.embed(body)
                    if (vector.any { it != 0f }) {
                        dao.upsertEmbedding(
                            MomentEmbeddingEntity(
                                momentId = momentId,
                                modelVersion = MODEL_VERSION,
                                vectorBlob = VectorMath.floatArrayToBytes(vector),
                                computedAt = now
                            )
                        )
                    }
                }
                // The Moment now owns the audio file; the envelope keeps only its record.
                dao.updateEnvelope(envelope.copy(isOpened = true, audioFileName = null))
                "Envelope opened and filed as a Moment."
            }
            refreshWidget()
            _statusMessage.value = message
        }
    }

    fun deleteEnvelope(envelope: OpenWhenEnvelopeEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                envelope.audioFileName?.let { audioManager.deleteAudio(it) }
                dao.deleteEnvelope(envelope.id)
            }
        }
    }

    // ------------------------------------------------------------------
    // Semantic search / re-read
    // ------------------------------------------------------------------

    fun searchSemantic(query: String) {
        if (query.isBlank()) {
            _semanticMatches.value = emptyList()
            return
        }
        viewModelScope.launch {
            val matches = withContext(Dispatchers.IO) {
                val queryVector = EmbeddingEngine.embed(query)
                if (queryVector.all { it == 0f }) return@withContext emptyList()
                val embeddings = dao.getAllEmbeddings()
                val momentsById = dao.getAllMomentsIncludingUnsent()
                    .filter { !it.isUnsent }
                    .associateBy { it.id }
                embeddings
                    .mapNotNull { embedding ->
                        val moment = momentsById[embedding.momentId] ?: return@mapNotNull null
                        val score = VectorMath.cosineSimilarity(
                            queryVector,
                            VectorMath.bytesToFloatArray(embedding.vectorBlob)
                        )
                        moment to score
                    }
                    .filter { it.second > 0.08f }
                    .sortedByDescending { it.second }
                    .take(8)
                    .map { it.first }
            }
            _semanticMatches.value = matches
        }
    }

    fun rollReRead() {
        viewModelScope.launch {
            val chosen = withContext(Dispatchers.IO) {
                val cutoff = LocalDate.now().minusDays(30)
                val pool = dao.getAllReadableMoments().filter {
                    val date = runCatching { DateDisplayFormatter.fromStorageIso(it.localDate) }.getOrNull()
                    date != null && date.isBefore(cutoff)
                }
                if (pool.isEmpty()) null else pool.random()
            }
            _reReadMoment.value = chosen
            if (chosen == null) _statusMessage.value = "Not enough memories yet for a re-read."
        }
    }

    fun dismissReRead() {
        _reReadMoment.value = null
    }

    fun dismissWelcomeBack() {
        _showWelcomeBack.value = false
        viewModelScope.launch {
            prefs.setLastWelcomeIso(DateDisplayFormatter.toStorageIso(LocalDate.now()))
        }
    }

    // ------------------------------------------------------------------
    // Audio
    // ------------------------------------------------------------------

    fun startAudioRecording(): String? = audioManager.startRecording()

    fun stopAudioRecording(): Pair<String?, Long> = audioManager.stopRecording()

    fun deleteAudio(fileName: String) = audioManager.deleteAudio(fileName)

    fun playAudio(fileName: String) = audioManager.play(fileName) {}

    fun stopAudioPlayback() = audioManager.stopPlayback()

    // ------------------------------------------------------------------
    // Vault
    // ------------------------------------------------------------------

    fun unlockVault(pin: String): Boolean {
        val current = uiState.value.settings
        val ok = PinHasher.verify(pin, current.vaultPinSalt, current.vaultPinHash)
        if (ok) _vaultUnlocked.value = true
        return ok
    }

    fun lockVault() {
        val settings = uiState.value.settings
        if (settings.vaultLockEnabled && settings.vaultPinHash != null) {
            _vaultUnlocked.value = false
        }
    }

    fun setVaultPin(pin: String) {
        if (pin.length < 4) return
        viewModelScope.launch {
            val salt = PinHasher.newSalt()
            prefs.setVaultPin(PinHasher.hash(pin, salt), salt)
            _vaultUnlocked.value = true
            _statusMessage.value = "Vault lock enabled."
        }
    }

    fun disableVaultLock() {
        viewModelScope.launch {
            prefs.clearVaultPin()
            _vaultUnlocked.value = true
            _statusMessage.value = "Vault lock removed."
        }
    }

    // ------------------------------------------------------------------
    // App lock / settings
    // ------------------------------------------------------------------

    fun setupPin(pin: String) {
        if (pin.length < 4) return
        viewModelScope.launch {
            val salt = PinHasher.newSalt()
            prefs.setPin(PinHasher.hash(pin, salt), salt)
            prefs.setAppLock(true)
            _statusMessage.value = "App lock enabled."
        }
    }

    fun updatePin(pin: String) {
        if (pin.length < 4) return
        viewModelScope.launch {
            val salt = PinHasher.newSalt()
            prefs.setPin(PinHasher.hash(pin, salt), salt)
            _statusMessage.value = "PIN updated."
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setAppLock(enabled) }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val current = prefs.settings.first()
        return PinHasher.verify(pin, current.pinSalt, current.pinHash)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            prefs.setReminder(enabled, hour, minute)
            val context = getApplication<Application>()
            if (enabled) ReminderScheduler.schedule(context, hour, minute)
            else ReminderScheduler.cancel(context)
        }
    }

    fun rescheduleReminderIfNeeded() {
        viewModelScope.launch {
            val current = prefs.settings.first()
            if (current.reminderEnabled) {
                ReminderScheduler.schedule(
                    getApplication(),
                    current.reminderHour,
                    current.reminderMinute
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Backup
    // ------------------------------------------------------------------

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                BackupManager.exportCsv(
                    getApplication(),
                    uri,
                    dao.getAllMomentsIncludingUnsent(),
                    dao.getAllTags()
                )
            }
            _statusMessage.value = "Exported CSV."
        }
    }

    fun exportEncrypted(uri: Uri, password: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    BackupManager.exportEncrypted(
                        getApplication(),
                        uri,
                        dao.getAllMomentsIncludingUnsent(),
                        dao.getAllTags(),
                        dao.getAllPhotos(),
                        dao.getAllChapters(),
                        dao.getAllEnvelopes(),
                        password
                    )
                    prefs.markBackupNow()
                }
            }
            _statusMessage.value = result.fold(
                onSuccess = { "Saved .sonder backup." },
                onFailure = { "Backup failed." }
            )
        }
    }

    fun restoreEncrypted(uri: Uri, password: String) {
        viewModelScope.launch {
            val report = withContext(Dispatchers.IO) {
                runCatching {
                    val bundle = BackupManager.restoreEncrypted(getApplication(), uri, password)
                    var restored = 0
                    var skipped = 0
                    val existingKeys = dao.getAllMomentsIncludingUnsent()
                        .map { it.localDate to it.createdAt }
                        .toMutableSet()
                    bundle.moments.forEach { item ->
                        val key = item.moment.localDate to item.moment.createdAt
                        if (key in existingKeys) {
                            skipped++
                            return@forEach
                        }
                        val momentId = dao.saveMomentWithTags(item.moment.copy(id = 0L), item.tags)
                        if (item.photos.isNotEmpty()) {
                            dao.savePhotosForMoment(
                                momentId,
                                item.photos.map { photo ->
                                    photo.copy(id = 0L, momentId = momentId)
                                }
                            )
                        }
                        val source = item.moment.bodyText.orEmpty()
                        if (source.isNotBlank()) {
                            val vector = EmbeddingEngine.embed(source)
                            if (vector.any { it != 0f }) {
                                dao.upsertEmbedding(
                                    MomentEmbeddingEntity(
                                        momentId = momentId,
                                        modelVersion = MODEL_VERSION,
                                        vectorBlob = VectorMath.floatArrayToBytes(vector)
                                    )
                                )
                            }
                        }
                        existingKeys += key
                        restored++
                    }
                    bundle.chapters.forEach { dao.upsertChapter(it.chapter.copy(id = 0L)) }
                    bundle.envelopes.forEach { dao.upsertEnvelope(it.envelope.copy(id = 0L)) }
                    RestoreReport(restored, bundle.chapters.size, bundle.envelopes.size, skipped)
                }
            }
            _statusMessage.value = report.fold(
                onSuccess = {
                    "Restored ${it.restored} moments, ${it.chapters} chapters, ${it.envelopes} envelopes."
                },
                onFailure = { "Restore failed — check password or file." }
            )
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.getAllMomentsIncludingUnsent().forEach { moment ->
                    moment.audioFileName?.let { audioManager.deleteAudio(it) }
                }
                dao.getAllPhotos().forEach { photoManager.deletePhoto(it.fileName) }
                dao.getAllChapters().forEach { chapter ->
                    chapter.coverPhotoUri?.let { photoManager.deletePhoto(it) }
                    dao.deleteChapter(chapter.id)
                }
                dao.getAllEnvelopes().forEach { envelope ->
                    envelope.audioFileName?.let { audioManager.deleteAudio(it) }
                }
                dao.deleteAllMoments()
            }
            _statusMessage.value = "All entries deleted."
            refreshWidget()
        }
    }

    fun dayWeather(moments: List<MomentEntity>): EmergentDayWeather? =
        EmergentDayWeather.derive(moments)

    fun weatherFor(moment: MomentEntity): EmergentDayWeather? =
        EmergentDayWeather.derive(listOf(moment))

    private fun refreshWidget() {
        runCatching { SonderWidgetProvider.refresh(getApplication()) }
    }

    override fun onCleared() {
        audioManager.release()
        super.onCleared()
    }

    companion object {
        const val MODEL_VERSION = "sonder-hash-384-v2"
        private const val ENVELOPE_NOTIFICATION_ID = 4211
    }
}
