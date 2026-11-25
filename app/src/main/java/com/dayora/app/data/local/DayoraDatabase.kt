package com.dayora.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dayora.app.data.local.dao.DailyEntryDao
import com.dayora.app.data.local.entity.DailyEntryEntity
import com.dayora.app.data.local.entity.EntryEmbeddingEntity
import com.dayora.app.data.local.entity.EntryTagEntity

@Database(
    entities = [
        DailyEntryEntity::class,
        EntryTagEntity::class,
        EntryEmbeddingEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DayoraConverters::class)
abstract class DayoraDatabase : RoomDatabase() {
    abstract fun dailyEntryDao(): DailyEntryDao

    companion object {
        @Volatile
        private var INSTANCE: DayoraDatabase? = null

        fun getInstance(context: Context): DayoraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DayoraDatabase::class.java,
                    "dayora.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
