package com.dayora.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "entry_tag",
    foreignKeys = [
        ForeignKey(
            entity = DailyEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["entry_id"])]
)
data class EntryTagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "entry_id")
    val entryId: Long,

    @ColumnInfo(name = "tag")
    val tag: String,

    @ColumnInfo(name = "tag_type")
    val tagType: String = "SPARK", // SPARK | PERSON | PLACE | TOPIC

    @ColumnInfo(name = "source")
    val source: String = "USER",   // USER | ML_AUTO

    @ColumnInfo(name = "user_confirmed")
    val userConfirmed: Boolean = true
)
