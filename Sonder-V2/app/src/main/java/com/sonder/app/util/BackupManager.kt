package com.sonder.app.util

import android.content.Context
import android.net.Uri
import com.sonder.app.data.local.entity.MomentEntity
import com.sonder.app.data.local.entity.MomentTagEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
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
    val tags: List<MomentTagEntity>
)

object BackupManager {

    private const val SCHEMA_VERSION = 2
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    fun exportCsv(
        context: Context,
        uri: Uri,
        moments: List<MomentEntity>,
        tags: List<MomentTagEntity>
    ) {
        val tagsByMoment = tags.groupBy { it.momentId }
        val builder = StringBuilder()
        builder.append("Date,Weather Score,Counts As Good,Rain Room,Unsent,Text,Audio File,Tags
")
        moments.sortedBy { it.localDate }.forEach { m ->
            val tagText = tagsByMoment[m.id]
                .orEmpty()
                .filter { it.userConfirmed }
                .joinToString("; ") { "\${it.tagName} (\${it.tagType})" }
            builder
                .append(csv(DateDisplayFormatter.isoToDisplayString(m.localDate))).append(',')
                .append(csv(m.weatherScore?.let { "%.2f".format(it) }.orEmpty())).append(',')
                .append(if (m.countsAsGood) "Yes" else "No").append(',')
                .append(if (m.isRainRoom) "Yes" else "No").append(',')
                .append(if (m.isUnsent) "Yes" else "No").append(',')
                .append(csv(m.bodyText.orEmpty())).append(',')
                .append(csv(m.audioFileName.orEmpty())).append(',')
                .append(csv(tagText)).append('
')
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
        password: String
    ) {
        val tagsByMoment = tags.groupBy { it.momentId }
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
                        put("tags", JSONArray().apply {
                            tagsByMoment[m.id].orEmpty().forEach { tag ->
                                put(JSONObject().apply {
                                    put("tag_name", tag.tagName)
                                    put("normalized_name", tag.normalizedName)
                                    put("tag_type", tag.tagType)
                                    put("source", tag.source)
                                    put("user_confirmed", tag.userConfirmed)
                                })
                            }
                        })
                    })
                }
            })
        }.toString()

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

    fun restoreEncrypted(context: Context, uri: Uri, password: String): List<ImportedMoment> {
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
        val plain = cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        val root = JSONObject(plain)
        val array = root.optJSONArray("moments") ?: root.optJSONArray("entries") ?: JSONArray()
        val result = ArrayList<ImportedMoment>(array.length())
        for (i in 0 until array.length()) {
            val json = array.getJSONObject(i)
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
                isRainRoom = json.optBoolean("is_rain_room", false),
                isUnsent = json.optBoolean("is_unsent", false),
                countsAsGood = json.optBoolean("counts_as_good", json.optBoolean("is_good_day", false)),
                bodyText = (json.optString("body_text").takeIf { it.isNotBlank() && it != "null" }
                    ?: json.optString("reason_text").takeIf { it.isNotBlank() && it != "null" }),
                audioFileName = json.optString("audio_file_name").takeIf { it.isNotBlank() && it != "null" },
                audioDurationMs = if (json.has("audio_duration_ms") && !json.isNull("audio_duration_ms")) json.optLong("audio_duration_ms") else null,
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
                        tagType = tagJson.optString("tag_type", "SPARK"),
                        source = tagJson.optString("source", "USER"),
                        userConfirmed = tagJson.optBoolean("user_confirmed", true)
                    )
                }
            }
            result += ImportedMoment(moment, tags)
        }
        return result
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun csv(value: String): String {
        val escaped = value.replace(""", """")
        return if (escaped.any { it == ',' || it == '"' || it == '
' || it == '' }) {
            ""\$escaped""
        } else {
            escaped
        }
    }
}
