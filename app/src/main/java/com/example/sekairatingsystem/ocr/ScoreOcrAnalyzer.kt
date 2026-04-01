package com.example.sekairatingsystem.ocr

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import java.io.Closeable
import java.text.Normalizer
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class ScoreOcrAnalyzer(
    context: Context,
) : Closeable {
    private val appContext = context.applicationContext
    private val recognizer: TextRecognizer =
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

    suspend fun analyze(uri: Uri): OcrResult {
        val image = withContext(Dispatchers.IO) {
            InputImage.fromFilePath(appContext, uri)
        }
        val recognizedText = recognizer.process(image).await()
        return buildOcrResult(recognizedText)
    }

    override fun close() {
        recognizer.close()
    }

    private fun buildOcrResult(recognizedText: Text): OcrResult {
        val lines = recognizedText.textBlocks
            .flatMap { block -> block.lines }
            .map { line -> removeGlobalNoise(line.text.trim()) }
            .filter { line -> line.isNotBlank() }

        val rawText = removeGlobalNoise(recognizedText.text.trim())
        val normalizedLines = lines.map(::normalizeForSearch)
        val normalizedWholeText = normalizeForSearch(rawText)
        val combinedDifficultyLevel = parseDifficultyAndLevel(normalizedLines, normalizedWholeText)
        val difficulty = combinedDifficultyLevel.first ?: parseDifficulty(normalizedLines, normalizedWholeText)
        val level = combinedDifficultyLevel.second ?: parseLevel(lines, normalizedLines, normalizedWholeText, difficulty)
        val songCandidates = extractSongCandidates(lines)
        val isAllPerfect = normalizedWholeText.contains("ALLPERFECT")
        val isFullCombo = normalizedWholeText.contains("FULLCOMBO")
        val parsedGreatCount = parseMetric(lines, normalizedLines, "GREAT")
        val parsedGoodCount = parseMetric(lines, normalizedLines, "GOOD")
        val parsedBadCount = parseMetric(lines, normalizedLines, "BAD")
        val parsedMissCount = parseMetric(lines, normalizedLines, "MISS")

        return OcrResult(
            rawText = rawText,
            recognizedLines = lines,
            rawSongText = songCandidates.firstOrNull(),
            songTextCandidates = songCandidates,
            difficulty = difficulty,
            level = level,
            perfectCount = parseMetric(lines, normalizedLines, "PERFECT"),
            greatCount = if (isAllPerfect) 0 else parsedGreatCount,
            goodCount = if (isAllPerfect || isFullCombo) 0 else parsedGoodCount,
            badCount = if (isAllPerfect || isFullCombo) 0 else parsedBadCount,
            missCount = if (isAllPerfect || isFullCombo) 0 else parsedMissCount,
            isAllPerfect = isAllPerfect,
        )
    }

    private fun parseDifficultyAndLevel(
        normalizedLines: List<String>,
        normalizedWholeText: String,
    ): Pair<String?, Int?> {
        normalizedLines.forEach { normalizedLine ->
            DIFFICULTY_AND_LEVEL_REGEX.find(normalizedLine)?.let { match ->
                return match.groupValues[1] to match.groupValues[2].toIntOrNull()
            }
        }

        DIFFICULTY_AND_LEVEL_REGEX.find(normalizedWholeText)?.let { match ->
            return match.groupValues[1] to match.groupValues[2].toIntOrNull()
        }

        return null to null
    }

    private fun parseDifficulty(
        normalizedLines: List<String>,
        normalizedWholeText: String,
    ): String? {
        return DIFFICULTIES.firstOrNull { difficulty ->
            normalizedWholeText.contains(difficulty) || normalizedLines.any { line -> line.contains(difficulty) }
        }
    }

    private fun parseLevel(
        rawLines: List<String>,
        normalizedLines: List<String>,
        normalizedWholeText: String,
        difficulty: String?,
    ): Int? {
        LEVEL_REGEXES.forEach { regex ->
            regex.find(normalizedWholeText)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { level ->
                return level
            }
        }

        if (difficulty != null) {
            normalizedLines.forEachIndexed { index, normalizedLine ->
                if (!normalizedLine.contains(difficulty)) {
                    return@forEachIndexed
                }

                LEVEL_NUMBER_REGEX.find(normalizedLine)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { level ->
                    return level
                }

                rawLines.getOrNull(index + 1)?.let { nextLine ->
                    FIRST_NUMBER_REGEX.find(normalizeForSearch(nextLine))?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { level ->
                        return level
                    }
                }
            }
        }

        return null
    }

    private fun parseMetric(
        rawLines: List<String>,
        normalizedLines: List<String>,
        vararg labels: String,
    ): Int? {
        normalizedLines.forEachIndexed { index, normalizedLine ->
            if (labels.none(normalizedLine::contains)) {
                return@forEachIndexed
            }

            labels.forEach { label ->
                metricRegex(label).find(normalizedLine)
                    ?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { value ->
                        return value
                    }
            }

            rawLines.getOrNull(index + 1)?.let { nextLine ->
                FIRST_NUMBER_REGEX.find(normalizeForSearch(nextLine))
                    ?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { value ->
                        return value
                    }
            }
        }

        return null
    }

    private fun extractSongCandidates(lines: List<String>): List<String> {
        return lines
            .map(::stripNoiseText)
            .filter(::isLikelySongCandidate)
            .distinct()
            .sortedByDescending(::scoreSongCandidate)
    }

    private fun removeGlobalNoise(text: String): String {
        return text
            .replace(Regex("\\b(?:LATE|FAST|FLICK|FEVER)\\b\\s*\\d*", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isLikelySongCandidate(line: String): Boolean {
        if (line.length !in 2..40) {
            return false
        }

        val normalizedLine = normalizeForSearch(line)
        if (normalizedLine.isBlank()) {
            return false
        }

        if (DIFFICULTIES.any(normalizedLine::contains)) {
            return false
        }

        if (EXCLUDED_LINE_KEYWORDS.any(normalizedLine::contains)) {
            return false
        }

        val digitCount = normalizedLine.count(Char::isDigit)
        if (digitCount > normalizedLine.length / 2) {
            return false
        }

        return SONG_TEXT_REGEX.containsMatchIn(line)
    }

    private fun scoreSongCandidate(line: String): Int {
        val lengthScore = line.length
        val digitPenalty = normalizeForSearch(line).count(Char::isDigit) * 2
        return lengthScore - digitPenalty
    }

    private fun stripNoiseText(text: String): String {
        var sanitized = text
        NOISE_PATTERNS.forEach { pattern ->
            sanitized = sanitized.replace(pattern, " ")
        }

        return sanitized
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
            .trim('!', '！', ':', '：', '-', ' ')
    }

    private fun normalizeForSearch(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
            .uppercase(Locale.ROOT)
            .replace(WHITESPACE_REGEX, "")
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }
        addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }

    companion object {
        private val DIFFICULTIES = listOf("APPEND", "MASTER", "EXPERT", "HARD", "NORMAL", "EASY")
        private val DIFFICULTY_AND_LEVEL_REGEX = Regex("(EASY|NORMAL|HARD|EXPERT|MASTER|APPEND)\\D{0,6}(?:楽曲)?LV\\.?0*(\\d{1,2})")
        private val LEVEL_REGEXES = listOf(
            Regex("(?:楽曲)?LV\\.?0*(\\d{1,2})"),
            Regex("(?:LV|LEVEL)\\D{0,4}0*(\\d{1,2})"),
            Regex("(?:MASTER|EXPERT|HARD|NORMAL|EASY|APPEND)\\D{0,4}(\\d{1,2})"),
        )
        private val LEVEL_NUMBER_REGEX = Regex("(?:LV|LEVEL)?\\D{0,4}(\\d{1,2})")
        private val FIRST_NUMBER_REGEX = Regex("(\\d{1,7})")
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val MULTI_SPACE_REGEX = Regex("\\s+")
        private val SONG_TEXT_REGEX = Regex("[A-Za-zぁ-んァ-ヶ一-龠ー]")
        private val EXCLUDED_LINE_KEYWORDS = listOf(
            "PERFECT",
            "GREAT",
            "GOOD",
            "BAD",
            "MISS",
            "NEWRECORD",
            "FULLCOMBO",
            "SCORE",
            "RESULT",
            "LIVECLEAR",
            "ALLPERFECT",
            "AP",
            "AUTO",
            "CLEAR",
            "スコア",
            "ハイスコア",
        )

        private val NOISE_PATTERNS = listOf(
            Regex("NEW\\s*RECORD!?", setOf(RegexOption.IGNORE_CASE)),
            Regex("FULL\\s*COMBO!?", setOf(RegexOption.IGNORE_CASE)),
            Regex("ALL\\s*PERFECT!?", setOf(RegexOption.IGNORE_CASE)),
            Regex("LIVE\\s*CLEAR!?", setOf(RegexOption.IGNORE_CASE)),
            Regex("\\bCLEAR\\b!?", setOf(RegexOption.IGNORE_CASE)),
            Regex("ハイスコア"),
            Regex("スコア"),
        )

        private fun metricRegex(label: String): Regex = Regex("${label}\\D{0,4}0*(\\d{1,7})")
    }
}

data class OcrResult(
    val rawText: String,
    val recognizedLines: List<String>,
    val rawSongText: String?,
    val songTextCandidates: List<String>,
    val difficulty: String?,
    val level: Int?,
    val perfectCount: Int?,
    val greatCount: Int?,
    val goodCount: Int?,
    val badCount: Int?,
    val missCount: Int?,
    val isAllPerfect: Boolean,
)