package com.example.sekairatingsystem.ocr

import com.example.sekairatingsystem.data.entity.SongMaster
import com.example.sekairatingsystem.util.LevenshteinDistance

object SongNameResolver {
    fun resolve(
        ocrResult: OcrResult,
        songMasters: List<SongMaster>,
        minSimilarity: Double = DEFAULT_SIMILARITY_THRESHOLD,
    ): ResolvedSongMatch {
        val candidates = buildCandidates(ocrResult)
        var bestMatch: ResolvedSongMatch? = null

        if (songMasters.isNotEmpty()) {
            for (candidate in candidates) {
                for (songMaster in songMasters) {
                    val similarity = LevenshteinDistance.similarity(candidate, sanitizeCandidate(songMaster.songName))
                    if (bestMatch == null || similarity > bestMatch?.similarity!!) {
                        bestMatch = ResolvedSongMatch(
                            songMaster = songMaster,
                            matchedText = candidate,
                            similarity = similarity,
                        )
                    }
                }
            }
        }

        // マスターデータに該当がある場合はそれを返す
        bestMatch?.takeIf { it.similarity >= minSimilarity }?.let {
            return it
        }

        // 該当がない（類似度が低い、またはマスターが空）場合、
        // エラーにせず、生テキストの候補を「新規楽曲」として取り扱うためのフォールバックを返す
        val fallbackName = candidates.firstOrNull() ?: ocrResult.rawSongText ?: "Unknown Song"
        return ResolvedSongMatch(
            songMaster = null, // 新規楽曲候補のため null
            matchedText = fallbackName,
            similarity = 0.0,
        )
    }

    private fun buildCandidates(ocrResult: OcrResult): List<String> {
        return buildList {
            ocrResult.rawSongText?.let(::add)
            addAll(ocrResult.songTextCandidates)
            addAll(ocrResult.recognizedLines.take(3))
            addAll(ocrResult.recognizedLines.takeLast(3))
        }
            .map(::sanitizeCandidate)
            .filter(::isMeaningfulCandidate)
            .distinct()
    }

    private fun sanitizeCandidate(text: String): String {
        var sanitized = text
        NOISE_PATTERNS.forEach { pattern ->
            sanitized = sanitized.replace(pattern, " ")
        }

        return sanitized
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
            .trim('!', '！', ':', '：', '-', ' ')
    }

    private fun isMeaningfulCandidate(text: String): Boolean {
        return text.isNotBlank() && SONG_TEXT_REGEX.containsMatchIn(text)
    }

    private const val DEFAULT_SIMILARITY_THRESHOLD = 0.45
    private val SONG_TEXT_REGEX = Regex("[A-Za-zぁ-んァ-ヶ一-龠ー]")
    private val MULTI_SPACE_REGEX = Regex("\\s+")
    private val NOISE_PATTERNS = listOf(
        Regex("NEW\\s*RECORD!?", setOf(RegexOption.IGNORE_CASE)),
        Regex("FULL\\s*COMBO!?", setOf(RegexOption.IGNORE_CASE)),
        Regex("ALL\\s*PERFECT!?", setOf(RegexOption.IGNORE_CASE)),
        Regex("LIVE\\s*CLEAR!?", setOf(RegexOption.IGNORE_CASE)),
        Regex("\\bCLEAR\\b!?", setOf(RegexOption.IGNORE_CASE)),
        Regex("ハイスコア"),
        Regex("スコア"),
    )
}

data class ResolvedSongMatch(
    val songMaster: SongMaster?,
    val matchedText: String,
    val similarity: Double,
)
