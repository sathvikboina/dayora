package com.sonder.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * v2 core: a calendar date can hold many Moments (a stressful morning and a
 * golden evening live honestly on the same day). `weatherScore` is a nullable
 * float in [-1.0, +1.0]; null means the user left the canvas untinted (Mist).
 */
@Entity(
    tableName = "moment",
    indices = [
        Index(value = ["local_date"]),
        Index(value = ["is_rain_room", "is_unsent"])
    ]
)
data class MomentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "local_date")
    val localDate: String,

    @ColumnInfo(name = "timezone_id")
    val timezoneId: String,

    @ColumnInfo(name = "weather_score")
    val weatherScore: Float? = null,

    @ColumnInfo(name = "is_rain_room")
    val isRainRoom: Boolean = false,

    @ColumnInfo(name = "is_unsent")
    val isUnsent: Boolean = false,

    @ColumnInfo(name = "counts_as_good")
    val countsAsGood: Boolean = false,

    @ColumnInfo(name = "body_text")
    val bodyText: String? = null,

    @ColumnInfo(name = "audio_file_name")
    val audioFileName: String? = null,

    @ColumnInfo(name = "audio_duration_ms")
    val audioDurationMs: Long? = null,

    @ColumnInfo(name = "ml_suggested_weather")
    val mlSuggestedWeather: Float? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Editable tags. `normalized_name` is the case-insensitive merge key, so
 * "Arshi" and "arshi" are the same tag. `user_confirmed = 0` means the
 * suggestion was dismissed and must never resurface.
 */
@Entity(
    tableName = "moment_tag",
    foreignKeys = [
        ForeignKey(
            entity = MomentEntity::class,
            parentColumns = ["id"],
            childColumns = ["moment_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["moment_id"]),
        Index(value = ["normalized_name", "tag_type"])
    ]
)
data class MomentTagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "moment_id")
    val momentId: Long,

    @ColumnInfo(name = "tag_name")
    val tagName: String,

    @ColumnInfo(name = "normalized_name")
    val normalizedName: String = tagName.trim().lowercase(),

    @ColumnInfo(name = "tag_type")
    val tagType: String = TAG_TYPE_SPARK,

    @ColumnInfo(name = "source")
    val source: String = SOURCE_USER,

    @ColumnInfo(name = "user_confirmed")
    val userConfirmed: Boolean = true
) {
    companion object {
        const val TAG_TYPE_SPARK = "SPARK"
        const val TAG_TYPE_PERSON = "PERSON"
        const val TAG_TYPE_PLACE = "PLACE"
        const val TAG_TYPE_THING = "THING"

        const val SOURCE_USER = "USER"
        const val SOURCE_ML_NOTICED = "ML_NOTICED"
    }
}

/**
 * A life stretch ("End-Sem Exams", "Goa Trip"). `pattern_style` is the
 * non-colour accessibility marker rendered beneath calendar day cells.
 */
@Entity(tableName = "chapter")
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "color_hex")
    val colorHex: String = "8A7A66",

    @ColumnInfo(name = "pattern_style")
    val patternStyle: String = PATTERN_SOLID,

    @ColumnInfo(name = "start_date")
    val startDate: String,

    @ColumnInfo(name = "end_date")
    val endDate: String? = null,

    @ColumnInfo(name = "planned_end_date")
    val plannedEndDate: String? = null,

    @ColumnInfo(name = "cover_photo_uri")
    val coverPhotoUri: String? = null,

    @ColumnInfo(name = "last_checkin_date")
    val lastCheckinDate: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    fun covers(isoDate: String): Boolean =
        isoDate >= startDate && (endDate == null || isoDate <= endDate)

    companion object {
        const val PATTERN_SOLID = "SOLID"
        const val PATTERN_DASHED = "DASHED"
        const val PATTERN_DOTTED = "DOTTED"
    }
}

/**
 * "Open When" time-locked envelope. The body is stored locally but never shown
 * until `unlock_date` has passed.
 */
@Entity(tableName = "open_when_envelope")
data class OpenWhenEnvelopeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "title_hint")
    val titleHint: String? = null,

    @ColumnInfo(name = "unlock_date")
    val unlockDate: String,

    @ColumnInfo(name = "linked_chapter_id")
    val linkedChapterId: Long? = null,

    @ColumnInfo(name = "body_text")
    val bodyText: String? = null,

    @ColumnInfo(name = "audio_file_name")
    val audioFileName: String? = null,

    @ColumnInfo(name = "is_opened")
    val isOpened: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 384-dimensional on-device embedding. v2 still uses the deterministic hash
 * embedder as the default (no model asset is bundled); the model_version column
 * lets a future MiniLM INT8 pack coexist and regenerate vectors.
 */
@Entity(
    tableName = "moment_embedding",
    foreignKeys = [
        ForeignKey(
            entity = MomentEntity::class,
            parentColumns = ["id"],
            childColumns = ["moment_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MomentEmbeddingEntity(
    @PrimaryKey
    @ColumnInfo(name = "moment_id")
    val momentId: Long,

    @ColumnInfo(name = "model_version")
    val modelVersion: String,

    @ColumnInfo(name = "vector_blob", typeAffinity = ColumnInfo.BLOB)
    val vectorBlob: ByteArray,

    @ColumnInfo(name = "computed_at")
    val computedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MomentEmbeddingEntity) return false
        return momentId == other.momentId &&
            modelVersion == other.modelVersion &&
            vectorBlob.contentEquals(other.vectorBlob)
    }

    override fun hashCode(): Int {
        var result = momentId.hashCode()
        result = 31 * result + modelVersion.hashCode()
        result = 31 * result + vectorBlob.contentHashCode()
        return result
    }
}

/**
 * Optional photos on a moment. Up to three are allowed per moment (the capture
 * sheet enforces the cap). Each photo can carry a short caption. Files are
 * copied into app-private storage from the Android Photo Picker, so no storage
 * permission is ever requested.
 */
@Entity(
    tableName = "moment_photo",
    foreignKeys = [
        ForeignKey(
            entity = MomentEntity::class,
            parentColumns = ["id"],
            childColumns = ["moment_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["moment_id"])]
)
data class MomentPhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "moment_id")
    val momentId: Long,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "caption")
    val caption: String? = null,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/** The day's glyph is emergent from its Moments, never an averaged score. */
enum class EmergentDayWeather(
    val label: String,
    val countsAsGood: Boolean
) {
    GOLDEN_SUN("Golden Sun", true),
    PART_SUN_PART_RAIN("Part-Sun, Part-Rain", true),
    DUSK_RAIN("Dusk Rain", false),
    QUIET_MIST("Quiet Mist", false);

    companion object {
        fun derive(dayMoments: List<MomentEntity>): EmergentDayWeather? {
            val visible = dayMoments.filter { !it.isUnsent }
            if (visible.isEmpty()) return null
            val hasSun = visible.any { (it.weatherScore ?: 0f) > 0.24f || it.countsAsGood }
            val hasRain = visible.any { (it.weatherScore ?: 0f) <= -0.25f || it.isRainRoom }
            return when {
                hasSun && hasRain -> PART_SUN_PART_RAIN
                hasSun -> GOLDEN_SUN
                hasRain -> DUSK_RAIN
                else -> QUIET_MIST
            }
        }
    }
}
