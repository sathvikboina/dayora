package com.sonder.app.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.UUID

/**
 * Local voice notes: AAC-LC in an .m4a container, 16 kHz mono, capped at 90 s.
 * Files live in filesDir/audio_vault under random UUID names, so a filename
 * never leaks a date or a title.
 */
class AudioNoteManager(private val context: Context) {

    companion object {
        const val MAX_DURATION_MS = 90_000L
    }

    private var recorder: MediaRecorder? = null
    private var currentFileName: String? = null
    private var startMs: Long = 0L
    private var accumulatedMs: Long = 0L
    private var paused: Boolean = false
    private var player: MediaPlayer? = null

    private fun vaultDir(): File = File(context.filesDir, "audio_vault").apply { mkdirs() }

    fun startRecording(): String? {
        stopAndDiscard()
        val fileName = "${UUID.randomUUID()}.m4a"
        val outFile = File(vaultDir(), fileName)
        return runCatching {
            @Suppress("DEPRECATION")
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioChannels(1)
            mr.setAudioSamplingRate(16_000)
            mr.setAudioEncodingBitRate(24_000)
            mr.setMaxDuration(MAX_DURATION_MS.toInt())
            mr.setOutputFile(outFile.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            currentFileName = fileName
            startMs = System.currentTimeMillis()
            accumulatedMs = 0L
            paused = false
            fileName
        }.getOrElse {
            runCatching { outFile.delete() }
            null
        }
    }

    fun isRecording(): Boolean = recorder != null

    fun isPaused(): Boolean = paused

    fun elapsedMs(): Long {
        if (recorder == null) return 0L
        val live = if (paused) 0L else (System.currentTimeMillis() - startMs).coerceAtLeast(0L)
        return (accumulatedMs + live).coerceIn(0L, MAX_DURATION_MS)
    }

    fun pauseRecording() {
        val mr = recorder ?: return
        if (paused) return
        runCatching { mr.pause() }
        accumulatedMs = (accumulatedMs + (System.currentTimeMillis() - startMs))
            .coerceIn(0L, MAX_DURATION_MS)
        paused = true
    }

    fun resumeRecording() {
        val mr = recorder ?: return
        if (!paused) return
        runCatching { mr.resume() }
        startMs = System.currentTimeMillis()
        paused = false
    }

    fun stopRecording(): Pair<String?, Long> {
        val mr = recorder ?: return null to 0L
        val fileName = currentFileName
        val elapsed = elapsedMs()
        if (!paused) {
            accumulatedMs = elapsed
        }
        runCatching { mr.stop() }
        runCatching { mr.release() }
        recorder = null
        currentFileName = null
        startMs = 0L
        accumulatedMs = 0L
        paused = false
        if (fileName != null) {
            val file = fileFor(fileName)
            if (!file.exists() || file.length() == 0L) {
                runCatching { file.delete() }
                return null to 0L
            }
        }
        return fileName to elapsed
    }

    fun stopAndDiscard() {
        val mr = recorder
        val fileName = currentFileName
        recorder = null
        currentFileName = null
        startMs = 0L
        accumulatedMs = 0L
        paused = false
        runCatching { mr?.stop() }
        runCatching { mr?.release() }
        fileName?.let { deleteAudio(it) }
    }

    fun fileFor(fileName: String): File = File(vaultDir(), fileName)

    fun exists(fileName: String): Boolean = fileFor(fileName).exists()

    fun deleteAudio(fileName: String) {
        runCatching { fileFor(fileName).delete() }
    }

    fun play(fileName: String, onFinished: () -> Unit = {}) {
        stopPlayback()
        val file = fileFor(fileName)
        if (!file.exists()) return
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    stopPlayback()
                    onFinished()
                }
                prepare()
                start()
            }
        }.onFailure { stopPlayback() }
    }

    fun stopPlayback() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    fun playbackPositionMs(): Int =
        runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)

    fun playbackDurationMs(): Int =
        runCatching { player?.duration ?: 0 }.getOrDefault(0)

    fun seekPlaybackTo(positionMs: Int) {
        runCatching { player?.seekTo(positionMs.coerceAtLeast(0)) }
    }

    fun isPlaying(): Boolean =
        runCatching { player?.isPlaying == true }.getOrDefault(false)

    fun release() {
        stopAndDiscard()
        stopPlayback()
    }
}
