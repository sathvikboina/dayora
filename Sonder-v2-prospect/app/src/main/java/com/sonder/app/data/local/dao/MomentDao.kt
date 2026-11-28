package com.sonder.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sonder.app.data.local.entity.ChapterEntity
import com.sonder.app.data.local.entity.MomentEmbeddingEntity
import com.sonder.app.data.local.entity.MomentEntity
import com.sonder.app.data.local.entity.MomentTagEntity
import com.sonder.app.data.local.entity.OpenWhenEnvelopeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MomentDao {

    @Query("SELECT COUNT(DISTINCT local_date) FROM moment WHERE is_unsent = 0 AND counts_as_good = 1")
    fun observeGoodDayCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM moment WHERE is_unsent = 0")
    fun observeTotalMomentCount(): Flow<Int>

    @Query("SELECT MIN(local_date) FROM moment WHERE is_unsent = 0")
    fun observeFirstMomentIsoDate(): Flow<String?>

    @Query("SELECT MAX(local_date) FROM moment WHERE is_unsent = 0")
    suspend fun getLatestMomentIsoDate(): String?

    @Query("SELECT * FROM moment WHERE is_unsent = 0 ORDER BY local_date DESC, created_at DESC")
    fun observeAllMoments(): Flow<List<MomentEntity>>

    @Query("SELECT * FROM moment ORDER BY local_date DESC, created_at DESC")
    suspend fun getAllMomentsIncludingUnsent(): List<MomentEntity>

    @Query("SELECT * FROM moment WHERE local_date = :isoDate AND is_unsent = 0 ORDER BY created_at ASC")
    fun observeMomentsForDate(isoDate: String): Flow<List<MomentEntity>>

    @Query("SELECT * FROM moment WHERE is_unsent = 0 AND is_rain_room = 1 ORDER BY local_date DESC, created_at DESC")
    fun observeRainRoomMoments(): Flow<List<MomentEntity>>

    @Query("SELECT * FROM moment WHERE is_unsent = 1 ORDER BY created_at DESC")
    fun observeUnsentMoments(): Flow<List<MomentEntity>>

    @Query("SELECT * FROM moment WHERE is_unsent = 0 AND counts_as_good = 1 AND body_text IS NOT NULL AND body_text != '' ORDER BY local_date DESC")
    suspend fun getGoodMemories(): List<MomentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMoment(moment: MomentEntity): Long

    @Query("DELETE FROM moment WHERE id = :momentId")
    suspend fun deleteMomentById(momentId: Long)

    @Query("DELETE FROM moment")
    suspend fun deleteAllMoments()

    @Query("DELETE FROM moment_tag WHERE moment_id = :momentId")
    suspend fun clearTagsForMoment(momentId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<MomentTagEntity>)

    @Query("SELECT * FROM moment_tag")
    fun observeAllTags(): Flow<List<MomentTagEntity>>

    @Query("SELECT * FROM moment_tag")
    suspend fun getAllTags(): List<MomentTagEntity>

    @Query("SELECT * FROM moment_tag WHERE moment_id = :momentId")
    suspend fun getTagsForMoment(momentId: Long): List<MomentTagEntity>

    @Query("UPDATE moment_tag SET tag_name = :newName, normalized_name = :normalized WHERE normalized_name = :oldNormalized")
    suspend fun renameTagGlobally(oldNormalized: String, newName: String, normalized: String)

    @Query("UPDATE moment_tag SET user_confirmed = 1 WHERE id = :tagId")
    suspend fun confirmTag(tagId: Long)

    @Query("UPDATE moment_tag SET user_confirmed = 0 WHERE id = :tagId")
    suspend fun dismissTag(tagId: Long)

    @Transaction
    suspend fun saveMomentWithTags(moment: MomentEntity, tags: List<MomentTagEntity>): Long {
        val momentId = upsertMoment(moment.copy(updatedAt = System.currentTimeMillis()))
        clearTagsForMoment(momentId)
        if (tags.isNotEmpty()) {
            insertTags(tags.map {
                val clean = it.tagName.trim()
                it.copy(
                    id = 0L,
                    momentId = momentId,
                    tagName = clean,
                    normalizedName = clean.lowercase()
                )
            })
        }
        return momentId
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmbedding(embedding: MomentEmbeddingEntity)

    @Query("SELECT * FROM moment_embedding")
    suspend fun getAllEmbeddings(): List<MomentEmbeddingEntity>

    @Query("DELETE FROM moment_embedding WHERE moment_id = :momentId")
    suspend fun deleteEmbedding(momentId: Long)

    @Query("SELECT * FROM chapter ORDER BY start_date DESC")
    fun observeChapters(): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapter ORDER BY start_date DESC")
    suspend fun getAllChapters(): List<ChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChapter(chapter: ChapterEntity): Long

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Query("DELETE FROM chapter WHERE id = :chapterId")
    suspend fun deleteChapter(chapterId: Long)

    @Query("SELECT * FROM open_when_envelope ORDER BY unlock_date ASC")
    fun observeEnvelopes(): Flow<List<OpenWhenEnvelopeEntity>>

    @Query("SELECT * FROM open_when_envelope ORDER BY unlock_date ASC")
    suspend fun getAllEnvelopes(): List<OpenWhenEnvelopeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEnvelope(envelope: OpenWhenEnvelopeEntity): Long

    @Update
    suspend fun updateEnvelope(envelope: OpenWhenEnvelopeEntity)
}
