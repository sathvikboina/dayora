package com.sonder.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sonder.app.data.local.dao.MomentDao
import com.sonder.app.data.local.entity.ChapterEntity
import com.sonder.app.data.local.entity.MomentEmbeddingEntity
import com.sonder.app.data.local.entity.MomentEntity
import com.sonder.app.data.local.entity.MomentPhotoEntity
import com.sonder.app.data.local.entity.MomentTagEntity
import com.sonder.app.data.local.entity.OpenWhenEnvelopeEntity

/**
 * v2 database. The file name stays `sonder.db` on purpose: existing v1 vaults
 * are upgraded in place by [MIGRATION_1_2] instead of starting empty.
 */
@Database(
    entities = [
        MomentEntity::class,
        MomentTagEntity::class,
        MomentPhotoEntity::class,
        ChapterEntity::class,
        OpenWhenEnvelopeEntity::class,
        MomentEmbeddingEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class SonderDatabase : RoomDatabase() {
    abstract fun momentDao(): MomentDao

    companion object {
        @Volatile
        private var INSTANCE: SonderDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `moment` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `local_date` TEXT NOT NULL,
                        `timezone_id` TEXT NOT NULL,
                        `weather_score` REAL,
                        `is_rain_room` INTEGER NOT NULL,
                        `is_unsent` INTEGER NOT NULL,
                        `counts_as_good` INTEGER NOT NULL,
                        `body_text` TEXT,
                        `audio_file_name` TEXT,
                        `audio_duration_ms` INTEGER,
                        `ml_suggested_weather` REAL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_moment_local_date` ON `moment` (`local_date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_moment_is_rain_room_is_unsent` ON `moment` (`is_rain_room`, `is_unsent`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `moment_tag` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `moment_id` INTEGER NOT NULL,
                        `tag_name` TEXT NOT NULL,
                        `normalized_name` TEXT NOT NULL,
                        `tag_type` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `user_confirmed` INTEGER NOT NULL,
                        FOREIGN KEY(`moment_id`) REFERENCES `moment`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_moment_tag_moment_id` ON `moment_tag` (`moment_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_moment_tag_normalized_name_tag_type` ON `moment_tag` (`normalized_name`, `tag_type`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `moment_photo` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `moment_id` INTEGER NOT NULL,
                        `file_name` TEXT NOT NULL,
                        `caption` TEXT,
                        `sort_order` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`moment_id`) REFERENCES `moment`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_moment_photo_moment_id` ON `moment_photo` (`moment_id`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chapter` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `color_hex` TEXT NOT NULL,
                        `pattern_style` TEXT NOT NULL,
                        `start_date` TEXT NOT NULL,
                        `end_date` TEXT,
                        `planned_end_date` TEXT,
                        `cover_photo_uri` TEXT,
                        `last_checkin_date` TEXT,
                        `created_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `open_when_envelope` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title_hint` TEXT,
                        `unlock_date` TEXT NOT NULL,
                        `linked_chapter_id` INTEGER,
                        `body_text` TEXT,
                        `audio_file_name` TEXT,
                        `is_opened` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `moment_embedding` (
                        `moment_id` INTEGER NOT NULL,
                        `model_version` TEXT NOT NULL,
                        `vector_blob` BLOB NOT NULL,
                        `computed_at` INTEGER NOT NULL,
                        PRIMARY KEY(`moment_id`),
                        FOREIGN KEY(`moment_id`) REFERENCES `moment`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                // ---- v1 -> v2 data mapping (no row is lost) ----
                db.execSQL(
                    """
                    INSERT INTO `moment` (
                        `id`, `local_date`, `timezone_id`, `weather_score`, `is_rain_room`,
                        `is_unsent`, `counts_as_good`, `body_text`, `audio_file_name`,
                        `audio_duration_ms`, `ml_suggested_weather`, `created_at`, `updated_at`
                    )
                    SELECT
                        `id`,
                        `local_date`,
                        `timezone_id`,
                        CASE `day_state`
                            WHEN 'BRIGHT' THEN 0.85
                            WHEN 'WARM' THEN 0.50
                            WHEN 'ORDINARY' THEN 0.0
                            WHEN 'HEAVY' THEN -0.80
                            ELSE NULL
                        END,
                        CASE WHEN `is_rain_room` != 0 OR `day_state` = 'HEAVY' THEN 1 ELSE 0 END,
                        0,
                        `is_good_day`,
                        `reason_text`,
                        NULL,
                        NULL,
                        CASE `ml_suggested_state`
                            WHEN 'BRIGHT' THEN 0.85
                            WHEN 'WARM' THEN 0.50
                            WHEN 'ORDINARY' THEN 0.0
                            WHEN 'HEAVY' THEN -0.80
                            ELSE NULL
                        END,
                        `created_at`,
                        `updated_at`
                    FROM `daily_entry`
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `moment_tag` (
                        `id`, `moment_id`, `tag_name`, `normalized_name`, `tag_type`, `source`, `user_confirmed`
                    )
                    SELECT
                        `id`,
                        `entry_id`,
                        TRIM(`tag`),
                        LOWER(TRIM(`tag`)),
                        CASE `tag_type` WHEN 'TOPIC' THEN 'THING' ELSE `tag_type` END,
                        CASE `source` WHEN 'ML_AUTO' THEN 'ML_NOTICED' ELSE `source` END,
                        `user_confirmed`
                    FROM `entry_tag`
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `moment_embedding` (`moment_id`, `model_version`, `vector_blob`, `computed_at`)
                    SELECT `entry_id`, `model_version`, `vector_blob`, `computed_at`
                    FROM `entry_embedding`
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE IF EXISTS `entry_embedding`")
                db.execSQL("DROP TABLE IF EXISTS `entry_tag`")
                db.execSQL("DROP TABLE IF EXISTS `daily_entry`")
            }
        }

        fun getInstance(context: Context): SonderDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SonderDatabase::class.java,
                    "sonder.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
