package com.sonder.app.data.local

import androidx.room.TypeConverter
import com.sonder.app.data.local.entity.DayState

class SonderConverters {
    @TypeConverter
    fun fromDayState(state: DayState?): String? = state?.name

    @TypeConverter
    fun toDayState(value: String?): DayState? = value?.let { DayState.valueOf(it) }
}
