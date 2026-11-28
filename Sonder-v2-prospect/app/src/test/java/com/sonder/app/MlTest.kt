package com.sonder.app

import com.sonder.app.data.local.entity.DayState
import com.sonder.app.ml.EmbeddingEngine
import com.sonder.app.ml.LightweightLexiconToneRouter
import com.sonder.app.ml.VectorMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToneRouterTest {

    private val router = LightweightLexiconToneRouter()

    @Test
    fun routesHeavyVentsToHeavy() {
        assertEquals(
            DayState.HEAVY,
            router.suggestDayState("I failed my presentation and feel useless and exhausted")
        )
    }

    @Test
    fun routesProudDaysToBright() {
        assertEquals(
            DayState.BRIGHT,
            router.suggestDayState("So proud, crushed the demo and felt amazing")
        )
    }

    @Test
    fun routesCozyDaysToWarm() {
        assertEquals(
            DayState.WARM,
            router.suggestDayState("Quiet chai on the terrace, peaceful evening")
        )
    }

    @Test
    fun returnsNullWhenThereIsNoSignal() {
        assertEquals(null, router.suggestDayState(""))
        assertEquals(null, router.suggestDayState("the report is on the table"))
    }
}

class EmbeddingEngineTest {

    @Test
    fun embeddingsAreNormalisedAndFixedSize() {
        val vector = EmbeddingEngine.embed("late night chai with Rohan after the build")
        assertEquals(EmbeddingEngine.DIMENSIONS, vector.size)
        var norm = 0f
        vector.forEach { norm += it * it }
        assertEquals(1f, kotlin.math.sqrt(norm), 0.001f)
    }

    @Test
    fun similarMemoriesScoreHigherThanUnrelatedOnes() {
        val query = EmbeddingEngine.embed("crushed my presentation at work today")
        val related = EmbeddingEngine.embed("presentation at work went really well")
        val unrelated = EmbeddingEngine.embed("rainy walk by the beach with chai")
        val relatedScore = VectorMath.cosineSimilarity(query, related)
        val unrelatedScore = VectorMath.cosineSimilarity(query, unrelated)
        assertTrue(relatedScore > unrelatedScore)
    }

    @Test
    fun emptyTextProducesZeroVector() {
        val vector = EmbeddingEngine.embed("   ")
        assertTrue(vector.all { it == 0f })
    }
}
