package com.sonder.app.ml

import com.sonder.app.data.local.entity.DayState

interface ToneRouter {
    fun suggestDayState(text: String): DayState?
    fun suggestWeatherScore(text: String): Float?
}

class LightweightLexiconToneRouter : ToneRouter {

    private val heavyWords = mapOf(
        "tired" to 2, "exhausted" to 3, "sad" to 3, "anxious" to 3, "anxiety" to 3,
        "stressed" to 3, "stress" to 2, "failed" to 3, "failure" to 2, "lonely" to 3,
        "rough" to 2, "heavy" to 2, "crying" to 3, "cried" to 2, "overwhelmed" to 3,
        "drained" to 3, "awful" to 3, "terrible" to 3, "hopeless" to 3, "angry" to 2,
        "upset" to 2, "hurt" to 2, "broke" to 1, "panic" to 3, "burnt" to 1,
        "hate" to 2, "miserable" to 3, "numb" to 2, "lost" to 1, "alone" to 1
    )

    private val brightWords = mapOf(
        "amazing" to 3, "proud" to 3, "best" to 2, "crushed" to 2, "won" to 2,
        "joy" to 3, "excited" to 3, "awesome" to 3, "celebrated" to 3, "great" to 2,
        "happy" to 3, "delighted" to 3, "grateful" to 2, "loved" to 2, "fun" to 2,
        "brilliant" to 2, "perfect" to 2, "smiled" to 2, "laughed" to 1, "glowing" to 2
    )

    private val warmWords = mapOf(
        "chai" to 2, "peace" to 2, "peaceful" to 3, "calm" to 3, "cozy" to 3,
        "friends" to 2, "friend" to 2, "family" to 2, "walk" to 1, "nice" to 2,
        "good" to 1, "warm" to 2, "coffee" to 2, "laughed" to 1, "laugh" to 1,
        "home" to 1, "cooked" to 1, "sunset" to 2, "quiet" to 1, "sweet" to 2
    )

    private val negations = setOf("not", "no", "never", "isn't", "wasn't", "didn't", "don't")

    override fun suggestDayState(text: String): DayState? {
        val tokens = text.lowercase()
            .split(Regex("[^a-z']+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        var heavy = 0
        var bright = 0
        var warm = 0

        tokens.forEachIndexed { index, token ->
            val negated = index > 0 && tokens[index - 1] in negations
            val sign = if (negated) -1 else 1
            heavy += sign * (heavyWords[token] ?: 0)
            bright += sign * (brightWords[token] ?: 0)
            warm += sign * (warmWords[token] ?: 0)
        }

        heavy = maxOf(heavy, 0)
        bright = maxOf(bright, 0)
        warm = maxOf(warm, 0)

        val top = maxOf(heavy, bright, warm)
        if (top == 0) return null
        return when (top) {
            heavy -> DayState.HEAVY
            bright -> DayState.BRIGHT
            else -> DayState.WARM
        }
    }

    override fun suggestWeatherScore(text: String): Float? = when (suggestDayState(text)) {
        DayState.HEAVY -> -0.75f
        DayState.BRIGHT -> 0.85f
        DayState.WARM -> 0.50f
        DayState.ORDINARY -> 0.0f
        null -> null
    }
}
