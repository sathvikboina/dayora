package com.dayora.app

import com.dayora.app.util.DateDisplayFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DateDisplayFormatterTest {

    @Test
    fun formatsIsoToDdMmYyyy() {
        val date = LocalDate.of(2026, 11, 25)
        assertEquals("2026-11-25", DateDisplayFormatter.toStorageIso(date))
        assertEquals("25-11-2026", DateDisplayFormatter.toDisplayString(date))
        assertEquals("25-11-2026", DateDisplayFormatter.isoToDisplayString("2026-11-25"))
        assertEquals("dayora_backup_25-11-2026.json", DateDisplayFormatter.backupFilename(date))
    }

    @Test
    fun midnightGraceTriggersOnlyLateNightWhenYesterdayEmpty() {
        assertTrue(DateDisplayFormatter.shouldOfferMidnightGrace(LocalTime.of(0, 30), yesterdayHasEntry = false))
        assertFalse(DateDisplayFormatter.shouldOfferMidnightGrace(LocalTime.of(0, 30), yesterdayHasEntry = true))
        assertFalse(DateDisplayFormatter.shouldOfferMidnightGrace(LocalTime.of(9, 0), yesterdayHasEntry = false))
    }
}
