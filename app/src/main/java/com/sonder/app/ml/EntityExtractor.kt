package com.sonder.app.ml

import com.sonder.app.data.local.entity.EntryTagEntity

/**
 * Very small on-device entity extractor. It looks for recurring people
 * (capitalised names), known places, and common activities so Memory Threads
 * work without any network call or heavy NER model.
 */
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
        "chai", "coffee", "walk", "run", "run", "workout", "coding", "build",
        "project", "music", "guitar", "reading", "book", "cooking", "cricket",
        "football", "movie", "game", "gaming", "dance", "yoga", "meditation"
    )

    fun extract(text: String, existingTags: Set<String> = emptySet()): List<EntryTagEntity> {
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
            val canonical = when (word) {
                "café" -> "cafe"
                else -> word
            }
            when (canonical) {
                in placeWords -> found.putIfAbsent(canonical.replaceFirstChar { it.uppercase() }, "PLACE")
                in activityWords -> found.putIfAbsent(canonical.replaceFirstChar { it.uppercase() }, "TOPIC")
            }
        }

        return found
            .filterKeys { it !in existingTags }
            .map { (tag, type) ->
                EntryTagEntity(
                    entryId = 0L,
                    tag = tag,
                    tagType = type,
                    source = "ML_AUTO",
                    userConfirmed = true
                )
            }
            .take(5)
    }
}
