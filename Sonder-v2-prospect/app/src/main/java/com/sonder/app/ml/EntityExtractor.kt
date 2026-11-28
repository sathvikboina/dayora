package com.sonder.app.ml

import com.sonder.app.data.local.entity.MomentTagEntity

object EntityExtractor {

    private val commonCapitalised = setOf(
        "I", "The", "A", "An", "Today", "Yesterday", "Tomorrow", "Monday", "Tuesday",
        "Wednesday", "Thursday", "Friday", "Saturday", "Sunday", "January", "February",
        "March", "April", "May", "June", "July", "August", "September", "October",
        "November", "December", "Late", "Early", "Morning", "Evening", "Night",
        "Finally", "First", "Then", "After", "Before", "We", "My", "This", "That"
    )

    private val placeWords = setOf(
        "gym", "terrace", "office", "beach", "park", "cafe", "café", "temple",
        "home", "hostel", "college", "school", "library", "market", "station",
        "airport", "restaurant", "studio", "campus", "trip", "travel"
    )

    private val activityWords = setOf(
        "chai", "coffee", "walk", "run", "workout", "coding", "build",
        "project", "music", "guitar", "reading", "book", "cooking", "cricket",
        "football", "movie", "game", "gaming", "dance", "yoga", "meditation"
    )

    fun extract(text: String, existingTags: Set<String> = emptySet()): List<MomentTagEntity> {
        if (text.isBlank()) return emptyList()
        val found = LinkedHashMap<String, String>()

        Regex("\\b[A-Z][a-z]{2,}\\b").findAll(text).forEach { match ->
            val word = match.value
            if (word !in commonCapitalised && word.lowercase() !in placeWords) {
                found.putIfAbsent(word, "PERSON")
            }
        }

        val words = text.lowercase().split(Regex("[^a-z0-9']+"))
        words.forEach { word ->
            val canonical = if (word == "café") "cafe" else word
            when (canonical) {
                in placeWords -> found.putIfAbsent(canonical.replaceFirstChar { it.uppercase() }, "PLACE")
                in activityWords -> found.putIfAbsent(canonical.replaceFirstChar { it.uppercase() }, "THING")
            }
        }

        val existingNormalized = existingTags.map { it.trim().lowercase() }.toSet()
        return found
            .filterKeys { it.trim().lowercase() !in existingNormalized }
            .map { (tag, type) ->
                MomentTagEntity(
                    momentId = 0L,
                    tagName = tag,
                    normalizedName = tag.trim().lowercase(),
                    tagType = type,
                    source = "ML_NOTICED",
                    userConfirmed = false
                )
            }
            .take(5)
    }
}
