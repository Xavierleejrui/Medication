package com.example.medicationtracker.ml

import kotlin.math.sqrt

class PillVerifier {

    companion object {
        const val SIMILARITY_THRESHOLD = 0.74f
    }

    fun calculateSimilarity(features1: FloatArray, features2: FloatArray): Float {
        require(features1.size == features2.size)

        var dotProduct = 0f
        var magnitude1 = 0f
        var magnitude2 = 0f

        for (i in features1.indices) {
            dotProduct += features1[i] * features2[i]
            magnitude1 += features1[i] * features1[i]
            magnitude2 += features2[i] * features2[i]
        }

        magnitude1 = sqrt(magnitude1)
        magnitude2 = sqrt(magnitude2)

        if (magnitude1 == 0f || magnitude2 == 0f) return 0f

        return dotProduct / (magnitude1 * magnitude2)
    }

    /**
     * Returns AVERAGE of top 50% similarities instead of maximum.
     * Much more robust - one lucky frame won't cause a false positive.
     */
    fun calculateAverageSimilarity(
        query: FloatArray,
        references: List<FloatArray>
    ): Float {
        if (references.isEmpty()) return 0f

        val similarities = references.map { calculateSimilarity(query, it) }

        // Sort descending and take top half to ignore bad reference photos
        val sorted = similarities.sortedDescending()
        val topHalf = sorted.take(maxOf(1, sorted.size / 2))

        return topHalf.average().toFloat()
    }

    fun verifyMatch(features1: FloatArray, features2: FloatArray): Boolean {
        return calculateSimilarity(features1, features2) >= SIMILARITY_THRESHOLD
    }

    fun findBestMatch(
        queryFeatures: FloatArray,
        referenceFeatures: List<FloatArray>
    ): Pair<Int, Float> {
        var bestIndex = -1
        var bestSimilarity = -1f

        referenceFeatures.forEachIndexed { index, refFeatures ->
            val similarity = calculateSimilarity(queryFeatures, refFeatures)
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestIndex = index
            }
        }

        return Pair(bestIndex, bestSimilarity)
    }
}