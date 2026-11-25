package com.dayora.app.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Single source of truth for dates in Dayora.
 * - Storage (Room SQLite): ISO-8601 YYYY-MM-DD for exact SQL sorting.
 * - Display & Exports: DD-MM-YYYY everywhere the user sees a date.
 */
object DateDisplayFormatter {

    private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val userDisplayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    fun toStorageIso(date: LocalDate): String = date.format(isoFormatter)

    fun fromStorageIso(isoDate: String): LocalDate = LocalDate.parse(isoDate, isoFormatter)

    fun toDisplayString(date: LocalDate): String = date.format(userDisplayFormatter)

    fun isoToDisplayString(isoDate: String): String = toDisplayString(fromStorageIso(isoDate))

    fun backupFilename(date: LocalDate = LocalDate.now()): String =
        "dayora_backup_${toDisplayString(date)}.json"

    fun currentTimezoneId(): String = ZoneId.systemDefault().id

    /**
     * Smart Midnight Grace: returns true ONLY between 12:00 AM and 4:00 AM
     * when yesterday has no entry yet.
     */
    fun shouldOfferMidnightGrace(
        nowTime: LocalTime,
        yesterdayHasEntry: Boolean
    ): Boolean {
        val isLateNight = nowTime.hour in 0..3
        return isLateNight && !yesterdayHasEntry
    }
}
