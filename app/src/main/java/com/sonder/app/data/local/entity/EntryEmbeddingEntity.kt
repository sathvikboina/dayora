package com.sonder.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Stores 384-dim Float32 sentence embeddings (~1.5 KB/entry)
 * for 100% on-device Semantic Heavy-Day Rescue & Semantic Search.
 */
@Entity(
    tableName = "entry_embedding",
    foreignKeys = [
        ForeignKey(
            entity = DailyEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EntryEmbeddingEntity(
    @PrimaryKey
    @ColumnInfo(name = "entry_id")
    val entryId: Long,

    @ColumnInfo(name = "model_version")
    val modelVersion: String,

    @ColumnInfo(name = "vector_blob", typeAffinity = ColumnInfo.BLOB)
    val vectorBlob: ByteArray,

    @ColumnInfo(name = "computed_at")
    val computedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EntryEmbeddingEntity) return false
        return entryId == other.entryId &&
            modelVersion == other.modelVersion &&
            vectorBlob.contentEquals(other.vectorBlob)
    }

    override fun hashCode(): Int {
        var result = entryId.hashCode()
        result = 31 * result + modelVersion.hashCode()
        result = 31 * result + vectorBlob.contentHashCode()
        return result
    }
}
