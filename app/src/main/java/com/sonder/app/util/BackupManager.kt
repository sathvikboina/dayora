package com.sonder.app.util

import android.content.Context
import android.net.Uri
import com.sonder.app.data.local.entity.ChapterEntity
import com.sonder.app.data.local.entity.MomentEntity
import com.sonder.app.data.local.entity.MomentPhotoEntity
import com.sonder.app.data.local.entity.MomentTagEntity
import com.sonder.app.data.local.entity.OpenWhenEnvelopeEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class ImportedMoment(
    val moment: MomentEntity,
    val tags: List<MomentTagEntity>,
    val photos: List<MomentPhotoEntity> = emptyList()
)

data class ImportedChapter(
    val chapter: ChapterEntity
)

data class ImportedEnvelope(
    val envelope: OpenWhenEnvelopeEntity
)

data class RestoreBundle(
    val moments: List<ImportedMoment>,
    val chapters: List<ImportedChapter>,
    val envelopes: List<ImportedEnvelope>
)

/**
 * Export/import for the v2 vault.
 *
 * The encrypted `.sonder` archive is a single AES-256-GCM blob whose plaintext
 * JSON holds moments, tags, chapters and envelopes, with audio clips embedded
 * as base64. Embeddings are intentionally omitted; they are regenerated
 * silently after restore. A v1 backup file can still be restored.
 */
object BackupManager {

    private const val SCHEMA_VERSION = 2
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val MAX_EMBEDDED_AUDIO_BYTES = 4 * 1024 * 1024
    private const val MAX_EMBEDDED_PHOTO_BYTES = 6 * 1024 * 1024

    fun exportCsv(
        context: Context,
        uri: Uri,
        moments: List<MomentEntity>,
        tags: List<MomentTagEntity>
    ) {
        val tagsByMoment = tags.groupBy { it.momentId }
        val builder = StringBuilder()
        builder.append("Date,Time,Weather,Counts As Good,Rain Room,Unsent,Text,Audio Seconds,Tags\n")
        moments.sortedBy { it.localDate }.forEach { m ->
            val tagText = tagsByMoment[m.id]
                .orEmpty()
                .filter { it.userConfirmed }
                .joinToString("; ") { "${it.tagName} (${it.tagType})" }
            builder
                .append(csv(DateDisplayFormatter.isoToDisplayString(m.localDate))).append(',')
                .append(csv(timeOfDay(m.createdAt))).append(',')
                .append(csv(m.weatherScore?.let { "%.2f".format(it) }.orEmpty())).append(',')
                .append(if (m.countsAsGood) "Yes" else "No").append(',')
                .append(if (m.isRainRoom) "Yes" else "No").append(',')
                .append(if (m.isUnsent) "Yes" else "No").append(',')
                .append(csv(m.bodyText.orEmpty())).append(',')
                .append(csv(m.audioDurationMs?.let { "%.0f".format(it / 1000f) }.orEmpty())).append(',')
                .append(csv(tagText)).append('\n')
        }
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(builder.toString().toByteArray(Charsets.UTF_8))
        }
    }

    fun exportEncrypted(
        context: Context,
        uri: Uri,
        moments: List<MomentEntity>,
        tags: List<MomentTagEntity>,
        photos: List<MomentPhotoEntity>,
        chapters: List<ChapterEntity>,
        envelopes: List<OpenWhenEnvelopeEntity>,
        password: String
    ) {
        val tagsByMoment = tags.groupBy { it.momentId }
        val photosByMoment = photos.groupBy { it.momentId }
        val payload = JSONObject().apply {
            put("schema_version", SCHEMA_VERSION)
            put("exported_at", System.currentTimeMillis())
            put("moments", JSONArray().apply {
                moments.sortedBy { it.localDate }.forEach { m ->
                    put(JSONObject().apply {
                        put("local_date", m.localDate)
                        put("timezone_id", m.timezoneId)
                        put("weather_score", m.weatherScore?.toDouble() ?: JSONObject.NULL)
                        put("is_rain_room", m.isRainRoom)
                        put("is_unsent", m.isUnsent)
                        put("counts_as_good", m.countsAsGood)
                        put("body_text", m.bodyText ?: JSONObject.NULL)
                        put("audio_file_name", m.audioFileName ?: JSONObject.NULL)
                        put("audio_duration_ms", m.audioDurationMs ?: JSONObject.NULL)
                        put("created_at", m.createdAt)
                        put("updated_at", m.updatedAt)
                        audioBase64(context, m.audioFileName)?.let { put("audio_base64", it) }
                        put("tags", JSONArray().apply {
                            tagsByMoment[m.id].orEmpty().forEach { tag ->
                                put(tagJson(tag))
                            }
                        })
                        put("photos", JSONArray().apply {
                            photosByMoment[m.id].orEmpty().forEach { photo ->
                                put(JSONObject().apply {
                                    put("file_name", photo.fileName)
                                    put("caption", photo.caption ?: JSONObject.NULL)
                                    put("sort_order", photo.sortOrder)
                                    put("created_at", photo.createdAt)
                                    photoBase64(context, photo.fileName)?.let {
                                        put("photo_base64", it)
                                    }
                                })
                            }
                        })
                    })
                }
            })
            put("chapters", JSONArray().apply {
                chapters.forEach { c ->
                    put(JSONObject().apply {
                        put("title", c.title)
                        put("color_hex", c.colorHex)
                        put("pattern_style", c.patternStyle)
                        put("start_date", c.startDate)
                        put("end_date", c.endDate ?: JSONObject.NULL)
                        put("planned_end_date", c.plannedEndDate ?: JSONObject.NULL)
                        put("cover_photo_uri", c.coverPhotoUri ?: JSONObject.NULL)
                        c.coverPhotoUri?.let { name ->
                            photoBase64(context, name)?.let { put("cover_photo_base64", it) }
                        }
                        put("last_checkin_date", c.lastCheckinDate ?: JSONObject.NULL)
                        put("created_at", c.createdAt)
                    })
                }
            })
            put("envelopes", JSONArray().apply {
                envelopes.forEach { e ->
                    put(JSONObject().apply {
                        put("title_hint", e.titleHint ?: JSONObject.NULL)
                        put("unlock_date", e.unlockDate)
                        put("body_text", e.bodyText ?: JSONObject.NULL)
                        put("audio_file_name", e.audioFileName ?: JSONObject.NULL)
                        put("is_opened", e.isOpened)
                        put("created_at", e.createdAt)
                        audioBase64(context, e.audioFileName)?.let { put("audio_base64", it) }
                    })
                }
            })
        }.toString()

        writeEncrypted(context, uri, password, payload)
    }

    fun restoreEncrypted(context: Context, uri: Uri, password: String): RestoreBundle {
        val plain = readEncrypted(context, uri, password)
        val root = JSONObject(plain)

        val momentsArray = root.optJSONArray("moments") ?: root.optJSONArray("entries") ?: JSONArray()
        val moments = ArrayList<ImportedMoment>(momentsArray.length())
        for (i in 0 until momentsArray.length()) {
            val json = momentsArray.getJSONObject(i)
            val restoredAudio = restoreAudio(context, json)
            val scoreVal = if (json.isNull("weather_score")) null else json.optDouble("weather_score").toFloat()
            val legacyState = json.optString("day_state")
            val derivedScore = scoreVal ?: when (legacyState) {
                "BRIGHT" -> 0.85f
                "WARM" -> 0.50f
                "ORDINARY" -> 0.0f
                "HEAVY" -> -0.80f
                else -> null
            }
            val moment = MomentEntity(
                localDate = json.getString("local_date"),
                timezoneId = json.optString("timezone_id", DateDisplayFormatter.currentTimezoneId()),
                weatherScore = derivedScore,
                isRainRoom = json.optBoolean("is_rain_room", legacyState == "HEAVY"),
                isUnsent = json.optBoolean("is_unsent", false),
                countsAsGood = json.optBoolean("counts_as_good", json.optBoolean("is_good_day", false)),
                bodyText = (json.optString("body_text").takeIf { it.isNotBlank() && it != "null" }
                    ?: json.optString("reason_text").takeIf { it.isNotBlank() && it != "null" }),
                audioFileName = restoredAudio ?: json.optString("audio_file_name")
                    .takeIf { it.isNotBlank() && it != "null" },
                audioDurationMs = if (json.has("audio_duration_ms") && !json.isNull("audio_duration_ms")) {
                    json.optLong("audio_duration_ms")
                } else {
                    null
                },
                createdAt = json.optLong("created_at", System.currentTimeMillis()),
                updatedAt = json.optLong("updated_at", System.currentTimeMillis())
            )
            val tags = ArrayList<MomentTagEntity>()
            val tagArray = json.optJSONArray("tags") ?: JSONArray()
            for (t in 0 until tagArray.length()) {
                val tagJson = tagArray.getJSONObject(t)
                val name = tagJson.optString("tag_name").ifBlank { tagJson.optString("tag") }
                if (name.isNotBlank()) {
                    tags += MomentTagEntity(
                        momentId = 0L,
                        tagName = name.trim(),
                        normalizedName = name.trim().lowercase(),
                        tagType = tagJson.optString("tag_type", MomentTagEntity.TAG_TYPE_SPARK),
                        source = tagJson.optString("source", MomentTagEntity.SOURCE_USER),
                        userConfirmed = tagJson.optBoolean("user_confirmed", true)
                    )
                }
            }
            val photos = ArrayList<MomentPhotoEntity>()
            val photoArray = json.optJSONArray("photos") ?: JSONArray()
            for (p in 0 until photoArray.length()) {
                val photoJson = photoArray.getJSONObject(p)
                val restoredPhoto = restorePhoto(context, photoJson) ?: continue
                photos += MomentPhotoEntity(
                    momentId = 0L,
                    fileName = restoredPhoto,
                    caption = photoJson.optString("caption").takeIf {
                        it.isNotBlank() && it != "null"
                    },
                    sortOrder = photoJson.optInt("sort_order", photos.size),
                    createdAt = photoJson.optLong("created_at", System.currentTimeMillis())
                )
            }
            moments += ImportedMoment(moment, tags, photos)
        }

        val chapters = ArrayList<ImportedChapter>()
        val chapterArray = root.optJSONArray("chapters") ?: JSONArray()
        for (i in 0 until chapterArray.length()) {
            val json = chapterArray.getJSONObject(i)
            val cover = json.optString("cover_photo_uri")
                .takeIf { it.isNotBlank() && it != "null" }
            cover?.let { restoreChapterCover(context, it, json.optString("cover_photo_base64")) }
            chapters += ImportedChapter(
                ChapterEntity(
                    title = json.getString("title"),
                    colorHex = json.optString("color_hex", "8A7A66"),
                    patternStyle = json.optString("pattern_style", ChapterEntity.PATTERN_SOLID),
                    startDate = json.getString("start_date"),
                    endDate = json.optString("end_date").takeIf { it.isNotBlank() && it != "null" },
                    plannedEndDate = json.optString("planned_end_date").takeIf { it.isNotBlank() && it != "null" },
                    coverPhotoUri = cover,
                    lastCheckinDate = json.optString("last_checkin_date").takeIf { it.isNotBlank() && it != "null" },
                    createdAt = json.optLong("created_at", System.currentTimeMillis())
                )
            )
        }

        val envelopes = ArrayList<ImportedEnvelope>()
        val envelopeArray = root.optJSONArray("envelopes") ?: JSONArray()
        for (i in 0 until envelopeArray.length()) {
            val json = envelopeArray.getJSONObject(i)
            val restoredAudio = restoreAudio(context, json)
            envelopes += ImportedEnvelope(
                OpenWhenEnvelopeEntity(
                    titleHint = json.optString("title_hint").takeIf { it.isNotBlank() && it != "null" },
                    unlockDate = json.getString("unlock_date"),
                    bodyText = json.optString("body_text").takeIf { it.isNotBlank() && it != "null" },
                    audioFileName = restoredAudio ?: json.optString("audio_file_name")
                        .takeIf { it.isNotBlank() && it != "null" },
                    isOpened = json.optBoolean("is_opened", false),
                    createdAt = json.optLong("created_at", System.currentTimeMillis())
                )
            )
        }

        return RestoreBundle(moments, chapters, envelopes)
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun tagJson(tag: MomentTagEntity): JSONObject = JSONObject().apply {
        put("tag_name", tag.tagName)
        put("normalized_name", tag.normalizedName)
        put("tag_type", tag.tagType)
        put("source", tag.source)
        put("user_confirmed", tag.userConfirmed)
    }

    private fun writeEncrypted(context: Context, uri: Uri, password: String, payload: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            deriveKey(password, salt),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        val ciphertext = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
        val envelope = JSONObject().apply {
            put("schema_version", SCHEMA_VERSION)
            put("kdf", "PBKDF2WithHmacSHA256")
            put("iterations", ITERATIONS)
            put("salt", base64(salt))
            put("iv", base64(iv))
            put("ciphertext", base64(ciphertext))
        }
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(envelope.toString().toByteArray(Charsets.UTF_8))
        }
    }

    private fun readEncrypted(context: Context, uri: Uri, password: String): String {
        val raw = context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
        } ?: error("Could not open backup file")
        val envelope = JSONObject(raw)
        val salt = Base64.getDecoder().decode(envelope.getString("salt"))
        val iv = Base64.getDecoder().decode(envelope.getString("iv"))
        val ciphertext = Base64.getDecoder().decode(envelope.getString("ciphertext"))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveKey(password, salt),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun audioBase64(context: Context, fileName: String?): String? {
        if (fileName == null) return null
        val file = File(File(context.filesDir, "audio_vault"), fileName)
        if (!file.exists() || file.length() == 0L || file.length() > MAX_EMBEDDED_AUDIO_BYTES) return null
        return runCatching { base64(file.readBytes()) }.getOrNull()
    }

    private fun restoreAudio(context: Context, json: JSONObject): String? {
        val encoded = json.optString("audio_base64").takeIf { it.isNotBlank() && it != "null" }
            ?: return null
        val proposed = json.optString("audio_file_name")
        val safeName = proposed
            .takeIf { it.matches(Regex("[A-Za-z0-9-]{8,64}\\.m4a")) }
            ?: (java.util.UUID.randomUUID().toString() + ".m4a")
        return runCatching {
            val dir = File(context.filesDir, "audio_vault").apply { mkdirs() }
            val bytes = Base64.getDecoder().decode(encoded)
            File(dir, safeName).writeBytes(bytes)
            safeName
        }.getOrNull()
    }

    private fun photoBase64(context: Context, fileName: String?): String? {
        if (fileName == null) return null
        val file = File(File(context.filesDir, "photo_vault"), fileName)
        if (!file.exists() || file.length() == 0L || file.length() > MAX_EMBEDDED_PHOTO_BYTES) return null
        return runCatching { base64(file.readBytes()) }.getOrNull()
    }

    private fun restoreChapterCover(context: Context, fileName: String, encoded: String?) {
        if (encoded.isNullOrBlank() || encoded == "null") return
        val safeName = fileName.takeIf {
            it.matches(Regex("[A-Za-z0-9-]{8,64}\\.(jpg|jpeg|png|webp)"))
        } ?: return
        val dir = File(context.filesDir, "photo_vault").apply { mkdirs() }
        val target = File(dir, safeName)
        if (target.exists() && target.length() > 0L) return
        runCatching { target.writeBytes(Base64.getDecoder().decode(encoded)) }
    }

    private fun restorePhoto(context: Context, json: JSONObject): String? {
        val encoded = json.optString("photo_base64").takeIf { it.isNotBlank() && it != "null" }
            ?: return null
        val proposed = json.optString("file_name")
        val safeName = proposed
            .takeIf { it.matches(Regex("[A-Za-z0-9-]{8,64}\\.jpg")) }
            ?: (java.util.UUID.randomUUID().toString() + ".jpg")
        return runCatching {
            val dir = File(context.filesDir, "photo_vault").apply { mkdirs() }
            val bytes = Base64.getDecoder().decode(encoded)
            File(dir, safeName).writeBytes(bytes)
            safeName
        }.getOrNull()
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun timeOfDay(epochMs: Long): String {
        val time = java.time.Instant.ofEpochMilli(epochMs)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalTime()
        return "%02d:%02d".format(time.hour, time.minute)
    }

    private fun csv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}
