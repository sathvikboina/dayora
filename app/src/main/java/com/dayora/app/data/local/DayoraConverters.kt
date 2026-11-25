package com.dayora.app.data.local

import androidx.room.TypeConverter
import com.dayora.app.data.local.entity.DayState

class DayoraConverters {
    @TypeConverter
    fun fromDayState(state: DayState?): String? = state?.name

    @TypeConverter
    fun toDayState(value: String?): DayState? = value?.let { DayState.valueOf(it) }
}
