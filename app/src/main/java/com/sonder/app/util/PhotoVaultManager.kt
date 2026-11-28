package com.sonder.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Optional photos attached to a Moment. Files are copied into
 * filesDir/photo_vault under opaque UUID names, so nothing in the filename
 * leaks a date, place or person. Nothing ever leaves the device.
 *
 * The Android Photo Picker is used by the UI, so this manager never needs a
 * storage permission.
 */
class PhotoVaultManager(private val context: Context) {

    companion object {
        /** Hard cap on a single stored photo; keeps the vault and backups lean. */
        const val MAX_STORED_BYTES = 12L * 1024L * 1024L
        const val MAX_PHOTOS_PER_MOMENT = 3
    }

    private fun vaultDir(): File = File(context.filesDir, "photo_vault").apply { mkdirs() }

    fun copyFromUri(uri: Uri): String? {
        val fileName = "${UUID.randomUUID()}.jpg"
        val target = File(vaultDir(), fileName)
        return runCatching {
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            stream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_STORED_BYTES) error("Photo is too large")
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (target.length() == 0L) {
                target.delete()
                null
            } else {
                fileName
            }
        }.getOrElse {
            runCatching { target.delete() }
            null
        }
    }

    fun fileFor(fileName: String): File = File(vaultDir(), fileName)

    fun exists(fileName: String): Boolean = fileFor(fileName).exists()

    fun deletePhoto(fileName: String) {
        runCatching { fileFor(fileName).delete() }
    }

    /**
     * Memory-frugal decode for thumbnails/full previews. Never loads a whole
     * camera photo into memory at full resolution.
     */
    fun decode(fileName: String, maxDimension: Int = 720): Bitmap? {
        val file = fileFor(fileName)
        if (!file.exists()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            var width = bounds.outWidth
            var height = bounds.outHeight
            while (width / 2 >= maxDimension || height / 2 >= maxDimension) {
                width /= 2
                height /= 2
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, options)
        }.getOrNull()
    }

    fun decodeUri(uri: Uri, maxDimension: Int = 720): Bitmap? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        var width = bounds.outWidth
        var height = bounds.outHeight
        while (width / 2 >= maxDimension || height / 2 >= maxDimension) {
            width /= 2
            height /= 2
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    fun allFileNames(): Set<String> =
        runCatching { vaultDir().listFiles()?.map { it.name }?.toSet().orEmpty() }.getOrDefault(emptySet())
}
