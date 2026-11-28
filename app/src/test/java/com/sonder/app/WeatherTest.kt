package com.sonder.app

import com.sonder.app.data.local.entity.EmergentDayWeather
import com.sonder.app.data.local.entity.MomentEntity
import com.sonder.app.ml.EntityExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {

    private fun moment(weather: Float?, rain: Boolean = false, unsent: Boolean = false) =
        MomentEntity(
            localDate = "2026-11-27",
            timezoneId = "Asia/Kolkata",
            weatherScore = weather,
            isRainRoom = rain,
            isUnsent = unsent
        )

    @Test
    fun aHeavyMorningNeverErasesAGoldenEvening() {
        val day = listOf(moment(-0.8f, rain = true), moment(0.7f))
        assertEquals(EmergentDayWeather.PART_SUN_PART_RAIN, EmergentDayWeather.derive(day))
    }

    @Test
    fun sunnyOnlyIsGoldenSun() {
        assertEquals(
            EmergentDayWeather.GOLDEN_SUN,
            EmergentDayWeather.derive(listOf(moment(0.5f)))
        )
    }

    @Test
    fun rainOnlyIsDuskRain() {
        assertEquals(
            EmergentDayWeather.DUSK_RAIN,
            EmergentDayWeather.derive(listOf(moment(-0.6f, rain = true)))
        )
    }

    @Test
    fun untintedMomentsAreQuietMist() {
        assertEquals(
            EmergentDayWeather.QUIET_MIST,
            EmergentDayWeather.derive(listOf(moment(null)))
        )
    }

    @Test
    fun unsentNotesAreExcludedFromTheDayGlyph() {
        assertEquals(
            null,
            EmergentDayWeather.derive(listOf(moment(-1f, rain = true, unsent = true)))
        )
    }
}

class EntityExtractorTest {

    @Test
    fun findsPeoplePlacesAndThings() {
        val tags = EntityExtractor.extract("Chai with Arshi at the terrace, then coding")
        val names = tags.map { it.name }
        assertTrue(names.any { it == "Arshi" })
        assertTrue(names.any { it == "Terrace" })
        assertTrue(names.any { it == "Chai" || it == "Coding" })
    }

    @Test
    fun doesNotRepeatTagsAlreadyOnTheMoment() {
        val tags = EntityExtractor.extract("Chai with Arshi", setOf("arshi"))
        assertTrue(tags.none { it.name == "Arshi" })
    }
}
