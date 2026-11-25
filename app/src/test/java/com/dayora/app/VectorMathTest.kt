package com.dayora.app

import com.dayora.app.ml.VectorMath
import org.junit.Assert.assertEquals
import org.junit.Test

class VectorMathTest {

    @Test
    fun floatByteRoundTripIsExact() {
        val original = floatArrayOf(0.25f, -0.5f, 1.0f, 0.0f)
        val restored = VectorMath.bytesToFloatArray(VectorMath.floatArrayToBytes(original))
        assertEquals(original.size, restored.size)
        for (i in original.indices) {
            assertEquals(original[i], restored[i], 0.0001f)
        }
    }

    @Test
    fun findsBestSemanticRescueMatch() {
        val query = floatArrayOf(1f, 0f, 0f)
        val candidates = listOf(
            10L to floatArrayOf(0f, 1f, 0f),
            42L to floatArrayOf(0.95f, 0.1f, 0f),
            99L to floatArrayOf(-1f, 0f, 0f)
        )
        assertEquals(42L, VectorMath.findBestSemanticMatch(query, candidates))
    }
}
