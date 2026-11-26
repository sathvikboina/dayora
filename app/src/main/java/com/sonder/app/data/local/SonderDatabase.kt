package com.sonder.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sonder.app.data.local.dao.DailyEntryDao
import com.sonder.app.data.local.entity.DailyEntryEntity
import com.sonder.app.data.local.entity.EntryEmbeddingEntity
import com.sonder.app.data.local.entity.EntryTagEntity

@Database(
    entities = [
        DailyEntryEntity::class,
        EntryTagEntity::class,
        EntryEmbeddingEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(SonderConverters::class)
abstract class SonderDatabase : RoomDatabase() {
    abstract fun dailyEntryDao(): DailyEntryDao

    companion object {
        @Volatile
        private var INSTANCE: SonderDatabase? = null

        fun getInstance(context: Context): SonderDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SonderDatabase::class.java,
                    "sonder.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
