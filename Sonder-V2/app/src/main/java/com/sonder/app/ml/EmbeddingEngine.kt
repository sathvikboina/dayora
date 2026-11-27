package com.sonder.app.ml

import kotlin.math.ln
import kotlin.math.sqrt

object EmbeddingEngine {
    const val DIMENSIONS = 384

    private val stopWords = setOf(
        "the", "a", "an", "and", "or", "but", "so", "because", "with", "without",
        "to", "of", "in", "on", "at", "for", "from", "by", "as", "is", "was",
        "were", "are", "be", "been", "being", "it", "this", "that", "these",
        "those", "i", "me", "my", "we", "our", "you", "your", "he", "she",
        "they", "them", "his", "her", "their", "had", "have", "has", "do",
        "did", "just", "really", "very", "today", "day", "about", "after",
        "before", "then", "than", "too", "also", "not", "no", "more", "most"
    )

    fun embed(text: String): FloatArray {
        val vector = FloatArray(DIMENSIONS)
        val tokens = text.lowercase()
            .split(Regex("[^a-z0-9']+"))
            .filter { it.length > 1 && it !in stopWords }
        if (tokens.isEmpty()) return vector

        val counts = HashMap<String, Int>(tokens.size * 2)
        tokens.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        for (i in 0 until tokens.size - 1) {
            val bigram = tokens[i] + "_" + tokens[i + 1]
            counts[bigram] = (counts[bigram] ?: 0) + 1
        }

        counts.forEach { (term, count) ->
            val index = Math.floorMod(term.hashCode(), DIMENSIONS)
            val sign = if (Math.floorDiv(term.hashCode(), DIMENSIONS) % 2 == 0) 1f else -1f
            vector[index] += sign * (1f + ln(count.toFloat()))
        }

        var norm = 0f
        for (value in vector) norm += value * value
        norm = sqrt(norm)
        if (norm > 0f) {
            for (i in vector.indices) vector[i] /= norm
        }
        return vector
    }
}
