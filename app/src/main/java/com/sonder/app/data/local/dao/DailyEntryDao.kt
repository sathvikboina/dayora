package com.sonder.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sonder.app.data.local.entity.DailyEntryEntity
import com.sonder.app.data.local.entity.EntryEmbeddingEntity
import com.sonder.app.data.local.entity.EntryTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyEntryDao {

    /**
     * Live Good-Day Counter — ALWAYS computed directly from SQLite so it can never drift.
     */
    @Query("SELECT COUNT(*) FROM daily_entry WHERE is_good_day = 1")
    fun observeGoodDayCount(): Flow<Int>

    @Query("SELECT MIN(local_date) FROM daily_entry")
    fun observeFirstEntryIsoDate(): Flow<String?>

    @Query("SELECT * FROM daily_entry WHERE local_date = :isoDate LIMIT 1")
    fun observeByDate(isoDate: String): Flow<DailyEntryEntity?>

    @Query("SELECT * FROM daily_entry WHERE local_date = :isoDate LIMIT 1")
    suspend fun getByDate(isoDate: String): DailyEntryEntity?

    @Query("SELECT * FROM daily_entry ORDER BY local_date DESC")
    fun observeAllEntries(): Flow<List<DailyEntryEntity>>

    @Query("SELECT * FROM daily_entry ORDER BY local_date DESC")
    suspend fun getAllEntries(): List<DailyEntryEntity>

    @Query("SELECT * FROM daily_entry WHERE is_rain_room = 1 ORDER BY local_date DESC")
    fun observeRainRoomEntries(): Flow<List<DailyEntryEntity>>

    @Query("SELECT * FROM daily_entry WHERE is_good_day = 1 AND reason_text IS NOT NULL AND reason_text != '' ORDER BY local_date DESC")
    suspend fun getGoodMemories(): List<DailyEntryEntity>

    @Query("SELECT * FROM daily_entry WHERE is_good_day = 1 AND reason_text IS NOT NULL AND reason_text != '' ORDER BY local_date DESC")
    fun observeGoodMemories(): Flow<List<DailyEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: DailyEntryEntity): Long

    @Query("DELETE FROM entry_tag WHERE entry_id = :entryId")
    suspend fun clearTagsForEntry(entryId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<EntryTagEntity>)

    @Query("SELECT * FROM entry_tag")
    fun observeAllTags(): Flow<List<EntryTagEntity>>

    @Query("SELECT * FROM entry_tag")
    suspend fun getAllTags(): List<EntryTagEntity>

    @Query("SELECT * FROM entry_tag WHERE entry_id = :entryId")
    suspend fun getTagsForEntry(entryId: Long): List<EntryTagEntity>

    @Transaction
    suspend fun saveEntryWithTags(entry: DailyEntryEntity, tags: List<EntryTagEntity>): Long {
        val existing = getByDate(entry.localDate)
        val rowToSave = if (existing != null) {
            entry.copy(
                id = existing.id,
                createdAt = existing.createdAt,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            entry.copy(updatedAt = System.currentTimeMillis())
        }
        val entryId = upsertEntry(rowToSave)
        clearTagsForEntry(entryId)
        if (tags.isNotEmpty()) {
            insertTags(tags.map { it.copy(entryId = entryId, id = 0L) })
        }
        return entryId
    }

    @Transaction
    suspend fun saveEntryWithSparks(entry: DailyEntryEntity, sparks: List<String>): Long {
        return saveEntryWithTags(
            entry = entry,
            tags = sparks.map { tag ->
                EntryTagEntity(entryId = 0L, tag = tag, tagType = "SPARK", source = "USER")
            }
        )
    }

    @Query("DELETE FROM daily_entry WHERE id = :entryId")
    suspend fun deleteEntry(entryId: Long)

    @Query("DELETE FROM daily_entry")
    suspend fun deleteAllEntries()

    // ---------------------------------------------------------------
    // On-device semantic embeddings
    // ---------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmbedding(embedding: EntryEmbeddingEntity)

    @Query("SELECT * FROM entry_embedding")
    suspend fun getAllEmbeddings(): List<EntryEmbeddingEntity>

    @Query("SELECT * FROM entry_embedding WHERE entry_id = :entryId")
    suspend fun getEmbeddingForEntry(entryId: Long): EntryEmbeddingEntity?

    @Query("DELETE FROM entry_embedding WHERE entry_id = :entryId")
    suspend fun deleteEmbedding(entryId: Long)
}
