package com.dayora.app.ml

import com.dayora.app.data.local.entity.DayState

/**
 * Phase 0 Interface for Silent Tone Auto-Routing.
 * Swap [LightweightLexiconToneRouter] with your quantized LiteRT (.tflite) classifier in Phase 3
 * without touching any UI code.
 */
interface ToneRouter {
    fun suggestDayState(text: String): DayState?
}

class LightweightLexiconToneRouter : ToneRouter {
    private val heavyKeywords = setOf(
        "tired", "exhausted", "sad", "anxious", "stressed", "failed", "lonely", "rough", "heavy", "crying", "overwhelmed"
    )
    private val brightKeywords = setOf(
        "amazing", "proud", "best", "crushed", "won", "joy", "excited", "awesome", "celebrated", "great"
    )
    private val warmKeywords = setOf(
        "chai", "peace", "calm", "cozy", "friends", "family", "walk", "nice", "good", "warm", "coffee", "laugh"
    )

    override fun suggestDayState(text: String): DayState? {
        val tokens = text.lowercase().split(Regex("[^a-z]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        return when {
            tokens.any { it in heavyKeywords } -> DayState.HEAVY
            tokens.any { it in brightKeywords } -> DayState.BRIGHT
            tokens.any { it in warmKeywords } -> DayState.WARM
            else -> null
        }
    }
}
