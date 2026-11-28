package com.sonder.app.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sonder.app.data.local.entity.DayState
import com.sonder.app.data.local.entity.MomentEntity
import com.sonder.app.data.local.entity.MomentPhotoEntity
import com.sonder.app.data.local.entity.MomentTagEntity
import com.sonder.app.ml.EntityExtractor
import com.sonder.app.ml.LightweightLexiconToneRouter
import com.sonder.app.util.AudioNoteManager
import com.sonder.app.util.DateDisplayFormatter
import com.sonder.app.util.PhotoVaultManager
import com.sonder.app.ui.components.PhotoThumbnail
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class PhotoSlot(
    val fileName: String? = null,
    val uriString: String? = null,
    val caption: String = ""
)

data class PhotoDraft(
    val fileName: String? = null,
    val uriString: String? = null,
    val caption: String = ""
)

data class CaptureDraft(
    val date: LocalDate,
    val bodyText: String,
    val weatherScore: Float?,
    val countAsGood: Boolean,
    val sparks: Set<String>,
    val isUnsent: Boolean,
    val audioFileName: String?,
    val audioDurationMs: Long?,
    val suggestedWeather: Float?,
    val photos: List<PhotoDraft> = emptyList(),
    val sealUntilDate: LocalDate? = null,
    val sealHint: String? = null
)

private val RainTop = Color(0xFF1B2230)
private val RainBottom = Color(0xFF283347)
private val SunTop = Color(0xFFFDF3E7)
private val SunBottom = Color(0xFFE5A645)

private fun rainAmount(weather: Float): Float = (-weather).coerceIn(0f, 1f)
private fun sunAmount(weather: Float): Float = weather.coerceIn(0f, 1f)

/**
 * The Capture sheet is itself the weather: drag the header / canvas left for
 * Dusk Rain, right for Golden Sun, rest anywhere in between for Mist. The text
 * field keeps its own vertical scroll and never loses cursor selection.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CaptureSheet(
    date: LocalDate,
    existing: MomentEntity?,
    existingSparks: Set<String> = emptySet(),
    existingTags: List<MomentTagEntity> = emptyList(),
    existingPhotos: List<MomentPhotoEntity> = emptyList(),
    forceRainRoom: Boolean = false,
    forceUnsent: Boolean = false,
    showSwipeHint: Boolean = false,
    onSwipeHintConsumed: () -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (CaptureDraft) -> Unit,
    onDelete: ((MomentEntity) -> Unit)? = null,
    onLetGo: ((MomentEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val toneRouter = remember { LightweightLexiconToneRouter() }
    val audio = remember { AudioNoteManager(context) }
    var photoSlots by remember {
        mutableStateOf(existingPhotos.map { PhotoSlot(fileName = it.fileName, caption = it.caption.orEmpty()) })
    }
    var captionTarget by remember { mutableStateOf<Int?>(null) }

    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) audio.startRecording() }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && photoSlots.size < PhotoVaultManager.MAX_PHOTOS_PER_MOMENT) {
            photoSlots = photoSlots + PhotoSlot(uriString = uri.toString())
            captionTarget = photoSlots.lastIndex
        }
    }

    DisposableEffect(Unit) {
        onDispose { audio.release() }
    }

    val initialWeather = when {
        existing?.weatherScore != null -> existing.weatherScore ?: 0f
        existing?.isRainRoom == true || forceRainRoom -> -0.85f
        else -> 0f
    }

    var weather by remember { mutableFloatStateOf(initialWeather) }
    var userSwiped by remember { mutableStateOf(false) }
    var bodyText by remember { mutableStateOf(existing?.bodyText.orEmpty()) }
    var selectedSparks by remember { mutableStateOf(existingSparks) }
    var sparkQuery by remember { mutableStateOf("") }
    var countAsGood by remember {
        mutableStateOf(existing?.countsAsGood ?: (initialWeather > 0.24f))
    }
    var isUnsent by remember { mutableStateOf(existing?.isUnsent ?: forceUnsent) }
    var audioFileName by remember { mutableStateOf(existing?.audioFileName) }
    var audioDurationMs by remember { mutableStateOf(existing?.audioDurationMs) }
    var recording by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var playbackMs by remember { mutableStateOf(0) }
    var playbackDurationMs by remember { mutableStateOf(0) }
    var suggestedWeather by remember { mutableStateOf<Float?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLetGoConfirm by remember { mutableStateOf(false) }
    var showSealDialog by remember { mutableStateOf(false) }
    var canvasWidth by remember { mutableFloatStateOf(1f) }
    var lastDetent by remember { mutableStateOf(0) }

    val animatedWeather by animateFloatAsState(
        targetValue = weather,
        label = "weather_canvas"
    )
    val rain = rainAmount(animatedWeather)
    val sun = sunAmount(animatedWeather)
    val topColor = when {
        rain > 0f -> lerp(MaterialTheme.colorScheme.surface, RainTop, rain)
        sun > 0f -> lerp(MaterialTheme.colorScheme.surface, SunTop, sun)
        else -> MaterialTheme.colorScheme.surface
    }
    val bottomColor = when {
        rain > 0f -> lerp(MaterialTheme.colorScheme.surface, RainBottom, rain)
        sun > 0f -> lerp(MaterialTheme.colorScheme.surfaceVariant, SunBottom, sun)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onCanvas = if (rain > 0.55f) Color(0xFFE6ECF5) else MaterialTheme.colorScheme.onSurface

    LaunchedEffect(recording, paused) {
        while (recording && !paused) {
            recordSeconds = (audio.elapsedMs() / 1000L).toInt().coerceIn(0, 90)
            delay(250)
        }
    }

    LaunchedEffect(playing, audioFileName) {
        if (playing) {
            playbackDurationMs = audio.playbackDurationMs()
            while (playing) {
                playbackMs = audio.playbackPositionMs()
                if (playbackDurationMs <= 0) playbackDurationMs = audio.playbackDurationMs()
                delay(200)
            }
        } else {
            playbackMs = 0
        }
    }

    val suggestions = remember(bodyText, selectedSparks, existingTags) {
        val known = selectedSparks + existingTags.map { it.tagName }
        EntityExtractor.extract(bodyText, known)
            .map { it.name }
            .distinct()
            .take(3)
    }

    fun startRecording() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        audioFileName?.let { audio.deleteAudio(it) }
        audioFileName = null
        audioDurationMs = null
        if (audio.startRecording() != null) {
            recording = true
            paused = false
            recordSeconds = 0
        }
    }

    fun stopRecording() {
        val (name, duration) = audio.stopRecording()
        recording = false
        paused = false
        if (name != null) {
            audioFileName = name
            audioDurationMs = duration
        }
    }

    fun weatherDetent(value: Float) {
        val detent = when {
            value <= -0.6f -> -1
            value >= 0.6f -> 1
            else -> 0
        }
        if (detent != lastDetent) {
            lastDetent = detent
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 30.dp)
        ) {
            // Dedicated weather drag layer (header + atmospheric band).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { canvasWidth = it.width.toFloat().coerceAtLeast(1f) }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { userSwiped = true },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                weather = (weather + (dragAmount / canvasWidth) * 2f)
                                    .coerceIn(-1f, 1f)
                                weatherDetent(weather)
                            }
                        )
                    }
                    .semantics {
                        contentDescription =
                            "Weather canvas. Swipe left for rain, right for sun."
                    }
            ) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                existing != null -> "This moment"
                                forceRainRoom -> "Something heavy"
                                else -> "A moment from today"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = onCanvas
                        )
                        Text(
                            text = DateDisplayFormatter.toDisplayString(date),
                            style = MaterialTheme.typography.bodyMedium,
                            color = onCanvas.copy(alpha = 0.65f)
                        )
                    }
                    IconButton(onClick = { showSealDialog = true }) {
                        Icon(
                            Icons.Outlined.MailOutline,
                            contentDescription = "Seal as an Open When envelope",
                            tint = onCanvas.copy(alpha = 0.75f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .padding(horizontal = 2.dp)
                ) {
                    // A thin atmospheric rail: no numbers, just a soft position cue.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .align(Alignment.Center)
                            .background(onCanvas.copy(alpha = 0.18f), RoundedCornerShape(2.dp))
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = ((weather + 1f) / 2f * 300f).dp)
                            .align(Alignment.CenterStart)
                            .height(8.dp)
                            .fillMaxWidth(0.06f)
                            .background(onCanvas.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    )
                }
                if (showSwipeHint) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = onSwipeHintConsumed,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "← rain   ·   swipe the canvas   ·   sun →",
                            style = MaterialTheme.typography.labelSmall,
                            color = onCanvas.copy(alpha = 0.7f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Text body
            OutlinedTextField(
                value = bodyText,
                onValueChange = { text ->
                    bodyText = text
                    val suggestion = toneRouter.suggestDayState(text)?.toWeatherScore()
                    suggestedWeather = suggestion
                    if (!userSwiped && suggestion != null && kotlin.math.abs(suggestion) > 0.24f) {
                        weather = suggestion
                    }
                },
                placeholder = {
                    Text(
                        if (isUnsent) "What you will never send…" else "Let it out, however it comes…",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 12
            )

            if (suggestedWeather != null && !userSwiped) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "The canvas is reading the tone of your words. Swipe to change it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // Audio note
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (recording) {
                    IconButton(onClick = { stopRecording() }) {
                        Icon(Icons.Outlined.Stop, contentDescription = "Stop recording")
                    }
                    IconButton(onClick = {
                        if (paused) {
                            audio.resumeRecording()
                            paused = false
                        } else {
                            audio.pauseRecording()
                            paused = true
                            recordSeconds = (audio.elapsedMs() / 1000L).toInt().coerceIn(0, 90)
                        }
                    }) {
                        Icon(
                            if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                            contentDescription = if (paused) "Resume recording" else "Pause recording"
                        )
                    }
                    Text(
                        text = (if (paused) "Paused · " else "") +
                            "%d:%02d / 1:30".format(recordSeconds / 60, recordSeconds % 60),
                        style = MaterialTheme.typography.labelSmall
                    )
                } else {
                    IconButton(onClick = { startRecording() }) {
                        Icon(Icons.Outlined.Mic, contentDescription = "Record a voice note")
                    }
                    if (audioFileName != null) {
                        Text(
                            text = "Voice note · %d s".format((audioDurationMs ?: 0L) / 1000L),
                            style = MaterialTheme.typography.labelSmall
                        )
                        IconButton(onClick = {
                            if (playing) {
                                audio.stopPlayback()
                                playing = false
                            } else {
                                playing = true
                                audio.play(audioFileName!!) { playing = false }
                            }
                        }) {
                            Icon(
                                if (playing) Icons.Outlined.VolumeOff else Icons.Outlined.PlayArrow,
                                contentDescription = if (playing) "Stop playback" else "Play voice note"
                            )
                        }
                        TextButton(onClick = {
                            audio.stopPlayback()
                            playing = false
                            audioFileName?.let { audio.deleteAudio(it) }
                            audioFileName = null
                            audioDurationMs = null
                            playbackMs = 0
                        }) { Text("Remove") }
                    } else {
                        Text(
                            text = "Voice note (up to 90s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            if (playing && audioFileName != null) {
                val total = playbackDurationMs.coerceAtLeast(1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "%d:%02d".format(
                            (playbackMs / 1000) / 60,
                            (playbackMs / 1000) % 60
                        ),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Slider(
                        value = (playbackMs.coerceIn(0, total)).toFloat(),
                        onValueChange = {
                            playbackMs = it.toInt()
                            audio.seekPlaybackTo(it.toInt())
                        },
                        valueRange = 0f..total.toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    Text(
                        text = "%d:%02d".format((total / 1000) / 60, (total / 1000) % 60),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Photos (optional, up to 3)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PHOTOS · UP TO 3",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
                if (photoSlots.size < PhotoVaultManager.MAX_PHOTOS_PER_MOMENT) {
                    TextButton(onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) {
                        Icon(
                            Icons.Outlined.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Add")
                    }
                }
            }
            if (photoSlots.isEmpty()) {
                Text(
                    text = "A picture, and a few words about it. Entirely optional.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    photoSlots.forEachIndexed { index, slot ->
                        Column(modifier = Modifier.width(96.dp)) {
                            Box {
                                PhotoThumbnail(
                                    uriString = slot.uriString,
                                    fileName = slot.fileName,
                                    modifier = Modifier
                                        .size(width = 96.dp, height = 84.dp),
                                    maxDimension = 320
                                )
                                IconButton(
                                    onClick = {
                                        photoSlots = photoSlots.filterIndexed { i, _ -> i != index }
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd).size(26.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Remove photo",
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                            TextButton(
                                onClick = { captionTarget = index },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = slot.caption.ifBlank { "Add a note" },
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }

            // Sparks
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "WHO OR WHAT WAS IN IT?",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            val chips = (defaultSparks + existingTags.filter { it.tagType == MomentTagEntity.TAG_TYPE_SPARK }
                .map { it.tagName }).distinct()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                chips.forEach { spark ->
                    val selected = spark in selectedSparks
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedSparks = if (selected) selectedSparks - spark
                            else selectedSparks + spark
                        },
                        label = { Text(spark) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = sparkQuery,
                onValueChange = { sparkQuery = it.take(40) },
                label = { Text("Add someone or something") },
                placeholder = { Text("Start typing — your tags autocomplete") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            val autocomplete = remember(sparkQuery, existingTags, selectedSparks) {
                val query = sparkQuery.trim()
                if (query.isEmpty()) {
                    emptyList()
                } else {
                    (existingTags.map { it.tagName } + defaultSparks)
                        .distinct()
                        .filter { it.contains(query, ignoreCase = true) && it !in selectedSparks }
                        .take(5)
                }
            }
            if (autocomplete.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    autocomplete.forEach { suggestion ->
                        AssistChip(
                            onClick = {
                                selectedSparks = selectedSparks + suggestion
                                sparkQuery = ""
                            },
                            label = { Text(suggestion) }
                        )
                    }
                }
            } else if (sparkQuery.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                AssistChip(
                    onClick = {
                        selectedSparks = selectedSparks + sparkQuery.trim()
                        sparkQuery = ""
                    },
                    label = { Text("Add “${sparkQuery.trim()}”") }
                )
            }

            if (suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "WE NOTICED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { tag ->
                        AssistChip(
                            onClick = { selectedSparks = selectedSparks + tag },
                            label = { Text(tag) }
                        )
                    }
                }
            }

            // Count + unsent
            Spacer(modifier = Modifier.height(16.dp))
            ToggleRow(
                title = "Count this as a good moment",
                subtitle = if (weather <= -0.25f) "Heavy moments rest quietly in the Rain Room." else null,
                checked = countAsGood && !isUnsent,
                enabled = !isUnsent,
                onCheckedChange = { countAsGood = it }
            )
            ToggleRow(
                title = "Keep unsent",
                subtitle = "Private drawer — excluded from Home, counts and Threads.",
                checked = isUnsent,
                enabled = true,
                onCheckedChange = {
                    isUnsent = it
                    if (it) countAsGood = false
                }
            )

            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (existing != null && onDelete != null) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (existing != null && existing.isRainRoom && onLetGo != null &&
                    !existing.bodyText.isNullOrBlank()
                ) {
                    TextButton(onClick = { showLetGoConfirm = true }) { Text("Let it go") }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Button(
                    onClick = {
                        if (recording) stopRecording()
                        val trimmed = bodyText.trim()
                        val hasVoice = audioFileName != null
                        if (trimmed.isBlank() && !hasVoice && selectedSparks.isEmpty()) return@Button
                        audio.stopPlayback()
                        onSave(
                            CaptureDraft(
                                date = date,
                                bodyText = trimmed,
                                weatherScore = weather.takeUnless { kotlin.math.abs(it) < 0.06f },
                                countAsGood = countAsGood && !isUnsent,
                                sparks = selectedSparks,
                                isUnsent = isUnsent,
                                audioFileName = audioFileName,
                                audioDurationMs = audioDurationMs,
                                suggestedWeather = suggestedWeather,
                                photos = photoSlots.map {
                                    PhotoDraft(it.fileName, it.uriString, it.caption)
                                }
                            )
                        )
                    }
                ) {
                    Text(if (existing != null) "Update" else "Save")
                }
            }
        }
    }

    if (showDeleteConfirm && existing != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this moment?") },
            text = { Text("This memory will be removed from your vault.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete?.invoke(existing)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Keep") }
            }
        )
    }

    if (showLetGoConfirm && existing != null) {
        AlertDialog(
            onDismissRequest = { showLetGoConfirm = false },
            title = { Text("Let this go?") },
            text = {
                Text(
                    "The words and any voice note will be cleared. Only the date " +
                        "and its quiet mark remain."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLetGoConfirm = false
                    onLetGo?.invoke(existing)
                }) { Text("Let it go") }
            },
            dismissButton = {
                TextButton(onClick = { showLetGoConfirm = false }) { Text("Keep it") }
            }
        )
    }

    if (showSealDialog) {
        SealEnvelopeDialog(
            initialText = bodyText,
            onDismiss = { showSealDialog = false },
            onSeal = seal@{ unlockDate, hint ->
                if (bodyText.isBlank() && audioFileName == null) {
                    showSealDialog = false
                    return@seal
                }
                showSealDialog = false
                onSave(
                    CaptureDraft(
                        date = date,
                        bodyText = bodyText.trim(),
                        weatherScore = weather.takeUnless { kotlin.math.abs(it) < 0.06f },
                        countAsGood = false,
                        sparks = selectedSparks,
                        isUnsent = false,
                        audioFileName = audioFileName,
                        audioDurationMs = audioDurationMs,
                        suggestedWeather = suggestedWeather,
                        photos = photoSlots.map {
                            PhotoDraft(it.fileName, it.uriString, it.caption)
                        },
                        sealUntilDate = unlockDate,
                        sealHint = hint
                    )
                )
            }
        )
    }

    captionTarget?.let { index ->
        if (index in photoSlots.indices) {
            PhotoCaptionDialog(
                initial = photoSlots[index].caption,
                onDismiss = { captionTarget = null },
                onSave = { caption ->
                    photoSlots = photoSlots.mapIndexed { i, slot ->
                        if (i == index) slot.copy(caption = caption) else slot
                    }
                    captionTarget = null
                }
            )
        }
    }
}

@Composable
private fun PhotoCaptionDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var caption by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("A few words about this photo") },
        text = {
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it.take(160) },
                label = { Text("Note (optional)") },
                placeholder = { Text("The light on the terrace") },
                minLines = 2,
                maxLines = 4
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(caption.trim()) }) { Text("Keep note") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SealEnvelopeDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onSeal: (LocalDate, String) -> Unit
) {
    var hint by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<LocalDate?>(null) }
    val today = LocalDate.now()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = today.plusDays(30)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seal until later") },
        text = {
            Column {
                Text(
                    "This moment stays unreadable until the date you choose. " +
                        "A quiet notification will appear that morning.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = hint,
                    onValueChange = { hint = it },
                    label = { Text("A hint (optional)") },
                    placeholder = { Text("Open after finals") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7L, 30L, 90L).forEach { days ->
                        AssistChip(
                            onClick = { chosen = today.plusDays(days) },
                            label = { Text("+$days days") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(onClick = { showPicker = true }) {
                    Text(
                        chosen?.let { "Open on ${DateDisplayFormatter.toDisplayString(it)}" }
                            ?: "Choose a date…"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val target = chosen ?: Instant.ofEpochMilli(
                        pickerState.selectedDateMillis
                            ?: System.currentTimeMillis() + 30L * 86_400_000L
                    ).atZone(ZoneId.systemDefault()).toLocalDate()
                    onSeal(target, hint.trim())
                }
            ) { Text("Seal it") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        chosen = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    }
                    showPicker = false
                }) { Text("Use date") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private val defaultSparks = listOf(
    "Friends",
    "Family",
    "Peace",
    "Good Food",
    "Small Win",
    "Outdoors",
    "Learning",
    "Just Felt Good"
)

private fun DayState.toWeatherScore(): Float = when (this) {
    DayState.BRIGHT -> 0.85f
    DayState.WARM -> 0.50f
    DayState.ORDINARY -> 0.0f
    DayState.HEAVY -> -0.80f
}
