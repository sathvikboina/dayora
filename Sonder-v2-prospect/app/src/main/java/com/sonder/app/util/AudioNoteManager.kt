package com.sonder.app.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.UUID

class AudioNoteManager(private val context: Context) {

    companion object {
        const val MAX_DURATION_MS = 90_000L
    }

    private var recorder: MediaRecorder? = null
    private var currentFileName: String? = null
    private var startMs: Long = 0L

    private fun vaultDir(): File =
        File(context.filesDir, "audio_vault").apply { mkdirs() }

    fun startRecording(): String {
        stopAndDiscard()
        val fileName = "\${UUID.randomUUID()}.m4a"
        val outFile = File(vaultDir(), fileName)

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
        return fileName
    }

    fun stopRecording(): Pair<String?, Long> {
        val mr = recorder ?: return null to 0L
        val fileName = currentFileName
        val elapsed = (System.currentTimeMillis() - startMs).coerceIn(0L, MAX_DURATION_MS)
        runCatching { mr.stop() }
        runCatching { mr.release() }
        recorder = null
        currentFileName = null
        startMs = 0L
        return fileName to elapsed
    }

    fun stopAndDiscard() {
        val mr = recorder
        val fileName = currentFileName
        recorder = null
        currentFileName = null
        startMs = 0L
        runCatching { mr?.stop() }
        runCatching { mr?.release() }
        fileName?.let { deleteAudio(it) }
    }

    fun fileFor(fileName: String): File = File(vaultDir(), fileName)

    fun deleteAudio(fileName: String) {
        runCatching { fileFor(fileName).delete() }
    }
}
