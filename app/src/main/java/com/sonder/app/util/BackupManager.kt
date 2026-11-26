package com.sonder.app.util

import android.content.Context
import android.net.Uri
import com.sonder.app.data.local.entity.DailyEntryEntity
import com.sonder.app.data.local.entity.DayState
import com.sonder.app.data.local.entity.EntryTagEntity
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

data class ImportedEntry(
    val entry: DailyEntryEntity,
    val tags: List<EntryTagEntity>
)

/**
 * CSV export and password-protected AES-256-GCM backup/restore via the
 * Android Storage Access Framework. Embeddings are intentionally omitted from
 * backups (they are recomputed locally on restore) keeping files tiny.
 */
object BackupManager {

    private const val SCHEMA_VERSION = 1
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    fun exportCsv(
        context: Context,
        uri: Uri,
        entries: List<DailyEntryEntity>,
        tags: List<EntryTagEntity>
    ) {
        val tagsByEntry = tags.groupBy { it.entryId }
        val builder = StringBuilder()
        builder.append("Date,Day State,Counts As Good,Rain Room,Reason,Sparks,Notes\n")
        entries.sortedBy { it.localDate }.forEach { entry ->
            val sparks = tagsByEntry[entry.id]
                .orEmpty()
                .filter { it.tagType == "SPARK" }
                .joinToString("; ") { it.tag }
            val notes = tagsByEntry[entry.id]
                .orEmpty()
                .filter { it.tagType != "SPARK" }
                .joinToString("; ") { "${it.tag} (${it.tagType})" }
            builder
                .append(csv(DateDisplayFormatter.isoToDisplayString(entry.localDate))).append(',')
                .append(csv(entry.dayState?.label.orEmpty())).append(',')
                .append(if (entry.isGoodDay) "Yes" else "No").append(',')
                .append(if (entry.isRainRoom) "Yes" else "No").append(',')
                .append(csv(entry.reasonText.orEmpty())).append(',')
                .append(csv(sparks)).append(',')
                .append(csv(notes)).append('\n')
        }
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(builder.toString().toByteArray(Charsets.UTF_8))
        }
    }

    fun exportEncrypted(
        context: Context,
        uri: Uri,
        entries: List<DailyEntryEntity>,
        tags: List<EntryTagEntity>,
        password: String
    ) {
        val tagsByEntry = tags.groupBy { it.entryId }
        val payload = JSONObject().apply {
            put("schema_version", SCHEMA_VERSION)
            put("exported_at", System.currentTimeMillis())
            put("entries", JSONArray().apply {
                entries.sortedBy { it.localDate }.forEach { entry ->
                    put(JSONObject().apply {
                        put("local_date", entry.localDate)
                        put("timezone_id", entry.timezoneId)
                        put("day_state", entry.dayState?.name ?: JSONObject.NULL)
                        put("is_good_day", entry.isGoodDay)
                        put("is_rain_room", entry.isRainRoom)
                        put("reason_text", entry.reasonText ?: JSONObject.NULL)
                        put("created_at", entry.createdAt)
                        put("updated_at", entry.updatedAt)
                        put("tags", JSONArray().apply {
                            tagsByEntry[entry.id].orEmpty().forEach { tag ->
                                put(JSONObject().apply {
                                    put("tag", tag.tag)
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

    fun restoreEncrypted(context: Context, uri: Uri, password: String): List<ImportedEntry> {
        val raw = context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
        } ?: error("Could not open backup file")
        val envelope = JSONObject(raw)
        require(envelope.optInt("schema_version", 0) == SCHEMA_VERSION) {
            "Unsupported backup version"
        }
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
        val entries = root.getJSONArray("entries")
        val result = ArrayList<ImportedEntry>(entries.length())
        for (i in 0 until entries.length()) {
            val json = entries.getJSONObject(i)
            val entry = DailyEntryEntity(
                localDate = json.getString("local_date"),
                timezoneId = json.optString("timezone_id", DateDisplayFormatter.currentTimezoneId()),
                dayState = json.optString("day_state").takeIf { it.isNotBlank() && it != "null" }
                    ?.let { runCatching { DayState.valueOf(it) }.getOrNull() },
                isGoodDay = json.optBoolean("is_good_day", false),
                isRainRoom = json.optBoolean("is_rain_room", false),
                reasonText = json.optString("reason_text").takeIf { it.isNotBlank() && it != "null" },
                createdAt = json.optLong("created_at", System.currentTimeMillis()),
                updatedAt = json.optLong("updated_at", System.currentTimeMillis())
            )
            val tags = ArrayList<EntryTagEntity>()
            val tagArray = json.optJSONArray("tags") ?: JSONArray()
            for (t in 0 until tagArray.length()) {
                val tagJson = tagArray.getJSONObject(t)
                tags += EntryTagEntity(
                    entryId = 0L,
                    tag = tagJson.getString("tag"),
                    tagType = tagJson.optString("tag_type", "SPARK"),
                    source = tagJson.optString("source", "USER"),
                    userConfirmed = tagJson.optBoolean("user_confirmed", true)
                )
            }
            result += ImportedEntry(entry, tags)
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
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}
