package com.dayora.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_entry",
    indices = [Index(value = ["local_date"], unique = true)]
)
data class DailyEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "local_date")
    val localDate: String, // ISO YYYY-MM-DD

    @ColumnInfo(name = "timezone_id")
    val timezoneId: String,

    @ColumnInfo(name = "day_state")
    val dayState: DayState?,

    @ColumnInfo(name = "is_good_day")
    val isGoodDay: Boolean,

    @ColumnInfo(name = "is_rain_room")
    val isRainRoom: Boolean,

    @ColumnInfo(name = "reason_text")
    val reasonText: String? = null,

    @ColumnInfo(name = "ml_suggested_state")
    val mlSuggestedState: DayState? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
