package com.sonder.app.ml

import com.sonder.app.data.local.entity.MomentTagEntity

/**
 * Tiny on-device entity extractor: capitalised people, known places and common
 * activities. Everything is returned as an unconfirmed "We noticed…" suggestion
 * so it can never overwrite what the user wrote.
 */
object EntityExtractor {

    data class ExtractedTag(
        val name: String,
        val type: String
    )

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

    fun extract(text: String, existingTags: Set<String> = emptySet()): List<ExtractedTag> {
        if (text.isBlank()) return emptyList()
        val found = LinkedHashMap<String, String>()
        val existingNormalized = existingTags.map { it.trim().lowercase() }.toSet()

        Regex("\\b[A-Z][a-z]{2,}\\b").findAll(text).forEach { match ->
            val word = match.value
            if (word !in commonCapitalised && word.lowercase() !in placeWords) {
                found.putIfAbsent(word, MomentTagEntity.TAG_TYPE_PERSON)
            }
        }

        val words = text.lowercase().split(Regex("[^a-z0-9']+"))
        words.forEach { word ->
            val canonical = when (word) {
                "café" -> "cafe"
                else -> word
            }
            when (canonical) {
                in placeWords -> found.putIfAbsent(
                    canonical.replaceFirstChar { it.uppercase() },
                    MomentTagEntity.TAG_TYPE_PLACE
                )
                in activityWords -> found.putIfAbsent(
                    canonical.replaceFirstChar { it.uppercase() },
                    MomentTagEntity.TAG_TYPE_THING
                )
            }
        }

        return found
            .filterKeys { it.trim().lowercase() !in existingNormalized }
            .map { (tag, type) -> ExtractedTag(tag, type) }
            .take(5)
    }
}
