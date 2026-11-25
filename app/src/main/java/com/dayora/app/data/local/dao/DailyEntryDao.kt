package com.dayora.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.dayora.app.data.local.entity.DailyEntryEntity
import com.dayora.app.data.local.entity.EntryTagEntity
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

    @Query("SELECT * FROM daily_entry WHERE is_rain_room = 1 ORDER BY local_date DESC")
    fun observeRainRoomEntries(): Flow<List<DailyEntryEntity>>

    @Query("SELECT * FROM daily_entry WHERE is_good_day = 1 AND reason_text IS NOT NULL AND reason_text != '' ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomGoodMemory(): DailyEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: DailyEntryEntity): Long

    @Query("DELETE FROM entry_tag WHERE entry_id = :entryId")
    suspend fun clearTagsForEntry(entryId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<EntryTagEntity>)

    @Transaction
    suspend fun saveEntryWithSparks(entry: DailyEntryEntity, sparks: List<String>): Long {
        val existing = getByDate(entry.localDate)
        val rowToSave = if (existing != null) {
            entry.copy(id = existing.id, createdAt = existing.createdAt, updatedAt = System.currentTimeMillis())
        } else {
            entry
        }
        val entryId = upsertEntry(rowToSave)
        clearTagsForEntry(entryId)
        if (sparks.isNotEmpty()) {
            insertTags(sparks.map { tag ->
                EntryTagEntity(entryId = entryId, tag = tag, tagType = "SPARK", source = "USER")
            })
        }
        return entryId
    }

    @Query("DELETE FROM daily_entry WHERE id = :entryId")
    suspend fun deleteEntry(entryId: Long)
}
