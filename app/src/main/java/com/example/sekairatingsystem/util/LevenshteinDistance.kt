package com.example.sekairatingsystem.util

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

object LevenshteinDistance {
    fun distance(left: String, right: String): Int {
        val normalizedLeft = normalize(left)
        val normalizedRight = normalize(right)

        return distanceNormalized(normalizedLeft, normalizedRight)
    }

    fun similarity(left: String, right: String): Double {
        val normalizedLeft = normalize(left)
        val normalizedRight = normalize(right)

        return similarityNormalized(normalizedLeft, normalizedRight)
    }

    fun similarityNormalized(normalizedLeft: String, normalizedRight: String): Double {
        if (normalizedLeft.isEmpty() && normalizedRight.isEmpty()) {
            return 1.0
        }

        val longestLength = max(normalizedLeft.length, normalizedRight.length)
        if (longestLength == 0) {
            return 1.0
        }

        return 1.0 - (distanceNormalized(normalizedLeft, normalizedRight).toDouble() / longestLength.toDouble())
    }

    private fun distanceNormalized(normalizedLeft: String, normalizedRight: String): Int {

        if (normalizedLeft.isEmpty()) {
            return normalizedRight.length
        }
        if (normalizedRight.isEmpty()) {
            return normalizedLeft.length
        }

        val previous = IntArray(normalizedRight.length + 1) { it }
        val current = IntArray(normalizedRight.length + 1)

        for (leftIndex in normalizedLeft.indices) {
            current[0] = leftIndex + 1

            for (rightIndex in normalizedRight.indices) {
                val substitutionCost = if (normalizedLeft[leftIndex] == normalizedRight[rightIndex]) {
                    0
                } else {
                    1
                }

                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + substitutionCost,
                )
            }

            current.copyInto(previous)
        }

        return previous[normalizedRight.length]
    }

    fun normalize(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
            .uppercase(Locale.ROOT)
            .replace(WHITESPACE_REGEX, "")
    }

    private val WHITESPACE_REGEX = Regex("\\s+")
}