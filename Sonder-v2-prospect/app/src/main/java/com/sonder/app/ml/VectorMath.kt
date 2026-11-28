package com.sonder.app.ml

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object VectorMath {

    fun floatArrayToBytes(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (v in vector) buffer.putFloat(v)
        return buffer.array()
    }

    fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val result = FloatArray(bytes.size / 4)
        for (i in result.indices) result[i] = buffer.getFloat()
        return result
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vector dimensions must match (\${a.size} vs \${b.size})" }
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0f) 0f else dot / denom
    }

    fun findBestSemanticMatch(
        queryVector: FloatArray,
        candidates: List<Pair<Long, FloatArray>>
    ): Long? {
        return candidates
            .maxByOrNull { (_, vec) -> cosineSimilarity(queryVector, vec) }
            ?.first
    }
}
