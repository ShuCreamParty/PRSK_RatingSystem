package com.example.sekairatingsystem.domain

import com.example.sekairatingsystem.Constants
import com.example.sekairatingsystem.data.entity.ScoreRecord
import com.example.sekairatingsystem.data.repository.AppRepository
import java.util.PriorityQueue

object RatingCalculator {
    fun calculateSingleRate(record: ScoreRecord): Float {
        val level = record.level?.toFloat() ?: return 0f
        val perfectCount = record.perfectCount ?: return 0f
        val greatCount = record.greatCount ?: return 0f
        val goodCount = record.goodCount ?: return 0f
        val badCount = record.badCount ?: return 0f
        val missCount = record.missCount ?: return 0f

        val totalNotes = perfectCount + greatCount + goodCount + badCount + missCount
        if (totalNotes <= 0) {
            return 0f
        }

        val accuracy = (perfectCount * 3 + greatCount * 2 + goodCount).toFloat() / (totalNotes * 3f)
        return when {
            accuracy >= 1.0f -> level + 2.0f
            accuracy >= 0.99f -> level + 1.0f + (accuracy - 0.99f) * 100f
            accuracy >= 0.95f -> level + (accuracy - 0.95f) * 25f
            else -> (level - (0.95f - accuracy) * 50f).coerceAtLeast(0f)
        }
    }

    suspend fun updateFramesInBatch(
        repository: AppRepository,
        ratedRecords: List<ScoreRecord>,
    ) {
        if (ratedRecords.isEmpty()) {
            return
        }

        val allRecords = repository.getAllScoreRecordsSnapshot()
        val state = FrameUpdateState(
            recordsById = allRecords.associateBy { record -> record.id }.toMutableMap(),
            bestRecords = allRecords.filter { record -> record.isBestFrame }.toMutableList(),
            reservedRecords = allRecords.filter { record -> record.isReservedFrame }.toMutableList(),
        )
        val updatesById = LinkedHashMap<Long, ScoreRecord>()

        trimBestFrameOverflow(state, updatesById)

        for (ratedRecord in ratedRecords) {
            val mergedRecord = mergeWithPersistedRecord(
                persistedRecord = state.recordsById[ratedRecord.id],
                ratedRecord = ratedRecord,
            )
            applyRecordUpdate(state, updatesById, mergedRecord)
            updateBestFrameWithState(state, updatesById, mergedRecord)

            val updatedAfterBest = state.recordsById[mergedRecord.id] ?: mergedRecord
            updateReservedFrameWithState(state, updatesById, updatedAfterBest)
        }

        if (updatesById.isNotEmpty()) {
            repository.updateScoreRecords(updatesById.values.toList())
        }
    }

    suspend fun updateBestFrame(repository: AppRepository, newRecord: ScoreRecord) {
        val songName = newRecord.songName?.trim()
        val difficulty = newRecord.difficulty?.trim()
        if (songName.isNullOrBlank() || difficulty.isNullOrBlank()) {
            return
        }

        val bestRecords = repository.getBestFrameRecordsSnapshot().toMutableList()
        trimBestFrameOverflow(repository, bestRecords)

        val existingBestForSameChart = bestRecords.firstOrNull { record ->
            record.songName == songName && record.difficulty == difficulty
        }

        if (existingBestForSameChart != null) {
            if (newRecord.rateValue() > existingBestForSameChart.rateValue()) {
                repository.updateScoreRecord(existingBestForSameChart.withFrameMembership(isBestFrame = false))
                repository.updateScoreRecord(newRecord.withFrameMembership(isBestFrame = true))
            } else {
                repository.updateScoreRecord(newRecord.withFrameMembership(isBestFrame = false))
            }
            return
        }

        if (bestRecords.size < Constants.BEST_FRAME_SIZE) {
            repository.updateScoreRecord(newRecord.withFrameMembership(isBestFrame = true))
            return
        }

        val lowestBestRecord = PriorityQueue(
            compareBy<ScoreRecord> { record -> record.rateValue() }
                .thenBy { record -> record.id },
        ).apply {
            addAll(bestRecords)
        }.peek() ?: return

        if (newRecord.rateValue() > lowestBestRecord.rateValue()) {
            repository.updateScoreRecord(lowestBestRecord.withFrameMembership(isBestFrame = false))
            repository.updateScoreRecord(newRecord.withFrameMembership(isBestFrame = true))
        } else {
            repository.updateScoreRecord(newRecord.withFrameMembership(isBestFrame = false))
        }
    }

    suspend fun updateReservedFrame(repository: AppRepository, newRecord: ScoreRecord) {
        val songName = newRecord.songName?.trim()
        val difficulty = newRecord.difficulty?.trim()
        if (songName.isNullOrBlank() || difficulty.isNullOrBlank()) {
            return
        }

        val persistedRecord = repository.getScoreRecordByIdSnapshot(newRecord.id) ?: newRecord
        val workingRecord = persistedRecord.copy(singleRate = newRecord.singleRate)

        val reservedRecords = repository.getReservedRecordsOldestFirst()

        if (reservedRecords.size < Constants.RESERVE_FRAME_SIZE) {
            repository.updateScoreRecord(workingRecord.withFrameMembership(isReservedFrame = true))
            return
        }

        val currentRate = workingRecord.rateValue()
        val specialConditionA = (workingRecord.goodCount ?: 0) + (workingRecord.badCount ?: 0) + (workingRecord.missCount ?: 0) <= 7
        val previousHighestRate = if (workingRecord.id > 0L) {
            repository.getHighestSingleRateExcludingRecord(songName, difficulty, newRecord.id)
        } else {
            repository.getHighestSingleRate(songName, difficulty)
        }
        val specialConditionB = previousHighestRate == null || currentRate >= previousHighestRate

        val duplicateReservedRecord = if (specialConditionA || specialConditionB) {
            findDuplicateReservedRecordByLowestRate(reservedRecords)
        } else {
            findDuplicateReservedRecordByOldestDate(reservedRecords)
        }
        if (duplicateReservedRecord != null) {
            repository.updateScoreRecord(duplicateReservedRecord.withFrameMembership(isReservedFrame = false))
            repository.updateScoreRecord(workingRecord.withFrameMembership(isReservedFrame = true))
            return
        }

        if (specialConditionA || specialConditionB) {
            handleSpecialReservedUpdate(repository, workingRecord, reservedRecords)
            return
        }

        val oldestReservedRecord = reservedRecords.firstOrNull()
        if (oldestReservedRecord != null) {
            repository.updateScoreRecord(oldestReservedRecord.withFrameMembership(isReservedFrame = false))
            repository.updateScoreRecord(workingRecord.withFrameMembership(isReservedFrame = true))
            return
        }

        repository.updateScoreRecord(workingRecord.withFrameMembership(isReservedFrame = true))
    }

    private fun mergeWithPersistedRecord(
        persistedRecord: ScoreRecord?,
        ratedRecord: ScoreRecord,
    ): ScoreRecord {
        val baseRecord = persistedRecord ?: ratedRecord
        return baseRecord.copy(
            imageUri = ratedRecord.imageUri,
            date = ratedRecord.date,
            status = ratedRecord.status,
            songName = ratedRecord.songName,
            difficulty = ratedRecord.difficulty,
            level = ratedRecord.level,
            perfectCount = ratedRecord.perfectCount,
            greatCount = ratedRecord.greatCount,
            goodCount = ratedRecord.goodCount,
            badCount = ratedRecord.badCount,
            missCount = ratedRecord.missCount,
            isAllPerfect = ratedRecord.isAllPerfect,
            singleRate = ratedRecord.singleRate,
            isBestFrame = false,
            isReservedFrame = false,
        )
    }

    private fun updateBestFrameWithState(
        state: FrameUpdateState,
        updatesById: MutableMap<Long, ScoreRecord>,
        newRecord: ScoreRecord,
    ) {
        val songName = newRecord.songName?.trim()
        val difficulty = newRecord.difficulty?.trim()
        if (songName.isNullOrBlank() || difficulty.isNullOrBlank()) {
            return
        }

        val existingBestForSameChart = state.bestRecords.firstOrNull { record ->
            record.songName?.trim() == songName && record.difficulty?.trim() == difficulty
        }

        if (existingBestForSameChart != null) {
            if (newRecord.rateValue() > existingBestForSameChart.rateValue()) {
                applyRecordUpdate(
                    state,
                    updatesById,
                    existingBestForSameChart.withFrameMembership(isBestFrame = false),
                )
                applyRecordUpdate(
                    state,
                    updatesById,
                    newRecord.withFrameMembership(isBestFrame = true),
                )
            } else {
                applyRecordUpdate(
                    state,
                    updatesById,
                    newRecord.withFrameMembership(isBestFrame = false),
                )
            }
            return
        }

        if (state.bestRecords.size < Constants.BEST_FRAME_SIZE) {
            applyRecordUpdate(
                state,
                updatesById,
                newRecord.withFrameMembership(isBestFrame = true),
            )
            return
        }

        val lowestBestRecord = state.bestRecords.minWithOrNull(
            compareBy<ScoreRecord> { record -> record.rateValue() }
                .thenBy { record -> record.id },
        ) ?: return

        if (newRecord.rateValue() > lowestBestRecord.rateValue()) {
            applyRecordUpdate(
                state,
                updatesById,
                lowestBestRecord.withFrameMembership(isBestFrame = false),
            )
            applyRecordUpdate(
                state,
                updatesById,
                newRecord.withFrameMembership(isBestFrame = true),
            )
        } else {
            applyRecordUpdate(
                state,
                updatesById,
                newRecord.withFrameMembership(isBestFrame = false),
            )
        }
    }

    private fun updateReservedFrameWithState(
        state: FrameUpdateState,
        updatesById: MutableMap<Long, ScoreRecord>,
        newRecord: ScoreRecord,
    ) {
        val songName = newRecord.songName?.trim()
        val difficulty = newRecord.difficulty?.trim()
        if (songName.isNullOrBlank() || difficulty.isNullOrBlank()) {
            return
        }

        val workingRecord = (state.recordsById[newRecord.id] ?: newRecord).copy(singleRate = newRecord.singleRate)
        val reservedRecords = state.reservedRecords.sortedWith(oldestReservedComparator)

        if (reservedRecords.size < Constants.RESERVE_FRAME_SIZE) {
            applyRecordUpdate(
                state,
                updatesById,
                workingRecord.withFrameMembership(isReservedFrame = true),
            )
            return
        }

        val currentRate = workingRecord.rateValue()
        val specialConditionA = (workingRecord.goodCount ?: 0) + (workingRecord.badCount ?: 0) + (workingRecord.missCount ?: 0) <= 7
        val previousHighestRate = highestSingleRateExcluding(
            records = state.recordsById.values,
            songName = songName,
            difficulty = difficulty,
            excludedRecordId = workingRecord.id,
        )
        val specialConditionB = previousHighestRate == null || currentRate >= previousHighestRate

        val duplicateReservedRecord = if (specialConditionA || specialConditionB) {
            findDuplicateReservedRecordByLowestRate(reservedRecords)
        } else {
            findDuplicateReservedRecordByOldestDate(reservedRecords)
        }
        if (duplicateReservedRecord != null) {
            applyRecordUpdate(
                state,
                updatesById,
                duplicateReservedRecord.withFrameMembership(isReservedFrame = false),
            )
            applyRecordUpdate(
                state,
                updatesById,
                workingRecord.withFrameMembership(isReservedFrame = true),
            )
            return
        }

        if (specialConditionA || specialConditionB) {
            handleSpecialReservedUpdateWithState(state, updatesById, workingRecord, reservedRecords)
            return
        }

        val oldestReservedRecord = reservedRecords.firstOrNull()
        if (oldestReservedRecord != null) {
            applyRecordUpdate(
                state,
                updatesById,
                oldestReservedRecord.withFrameMembership(isReservedFrame = false),
            )
            applyRecordUpdate(
                state,
                updatesById,
                workingRecord.withFrameMembership(isReservedFrame = true),
            )
            return
        }

        applyRecordUpdate(
            state,
            updatesById,
            workingRecord.withFrameMembership(isReservedFrame = true),
        )
    }

    private fun handleSpecialReservedUpdateWithState(
        state: FrameUpdateState,
        updatesById: MutableMap<Long, ScoreRecord>,
        newRecord: ScoreRecord,
        reservedRecords: List<ScoreRecord>,
    ) {
        val lowestReservedRecord = reservedRecords.minWithOrNull(
            compareBy<ScoreRecord> { record -> record.rateValue() }
                .thenBy { record -> record.date ?: Long.MAX_VALUE }
                .thenBy { record -> record.id },
        )

        if (lowestReservedRecord == null) {
            applyRecordUpdate(
                state,
                updatesById,
                newRecord.withFrameMembership(isReservedFrame = true),
            )
            return
        }

        if (newRecord.rateValue() <= lowestReservedRecord.rateValue()) {
            applyRecordUpdate(
                state,
                updatesById,
                newRecord.withFrameMembership(isReservedFrame = false),
            )
            return
        }

        if (reservedRecords.size >= Constants.RESERVE_FRAME_SIZE) {
            applyRecordUpdate(
                state,
                updatesById,
                lowestReservedRecord.withFrameMembership(isReservedFrame = false),
            )
        }

        applyRecordUpdate(
            state,
            updatesById,
            newRecord.withFrameMembership(isReservedFrame = true),
        )
    }

    private fun highestSingleRateExcluding(
        records: Collection<ScoreRecord>,
        songName: String,
        difficulty: String,
        excludedRecordId: Long,
    ): Float? {
        return records.asSequence()
            .filter { record -> record.id != excludedRecordId }
            .filter { record -> record.status != Constants.STATUS_FAILED }
            .filter { record -> record.songName?.trim() == songName && record.difficulty?.trim() == difficulty }
            .mapNotNull { record -> record.singleRate }
            .maxOrNull()
    }

    private fun findDuplicateReservedRecordByLowestRate(reservedRecords: List<ScoreRecord>): ScoreRecord? {
        val duplicateChartKeys = reservedRecords.duplicateChartKeys()
        if (duplicateChartKeys.isEmpty()) {
            return null
        }

        return reservedRecords
            .asSequence()
            .filter { record -> record.chartKey() in duplicateChartKeys }
            .minWithOrNull(
                compareBy<ScoreRecord> { record -> record.rateValue() }
                    .thenBy { record -> record.date ?: Long.MAX_VALUE }
                    .thenBy { record -> record.id },
            )
    }

    private fun findDuplicateReservedRecordByOldestDate(reservedRecords: List<ScoreRecord>): ScoreRecord? {
        val duplicateChartKeys = reservedRecords.duplicateChartKeys()
        if (duplicateChartKeys.isEmpty()) {
            return null
        }

        return reservedRecords
            .asSequence()
            .filter { record -> record.chartKey() in duplicateChartKeys }
            .minWithOrNull(oldestReservedComparator)
    }

    private fun List<ScoreRecord>.duplicateChartKeys(): Set<String> {
        return asSequence()
            .mapNotNull { record -> record.chartKey() }
            .groupingBy { chartKey -> chartKey }
            .eachCount()
            .filterValues { count -> count >= 2 }
            .keys
    }

    private fun applyRecordUpdate(
        state: FrameUpdateState,
        updatesById: MutableMap<Long, ScoreRecord>,
        updatedRecord: ScoreRecord,
    ) {
        state.recordsById[updatedRecord.id] = updatedRecord
        updatesById[updatedRecord.id] = updatedRecord

        state.bestRecords.removeAll { record -> record.id == updatedRecord.id }
        if (updatedRecord.isBestFrame) {
            state.bestRecords.add(updatedRecord)
        }

        state.reservedRecords.removeAll { record -> record.id == updatedRecord.id }
        if (updatedRecord.isReservedFrame) {
            state.reservedRecords.add(updatedRecord)
        }
    }

    private suspend fun trimBestFrameOverflow(
        repository: AppRepository,
        bestRecords: MutableList<ScoreRecord>,
    ) {
        val overflowRecords = bestRecords.bestFrameOverflowRecords()
        if (overflowRecords.isEmpty()) {
            return
        }

        overflowRecords.forEach { overflowRecord ->
            repository.updateScoreRecord(overflowRecord.withFrameMembership(isBestFrame = false))
            bestRecords.removeAll { record -> record.id == overflowRecord.id }
        }
    }

    private fun trimBestFrameOverflow(
        state: FrameUpdateState,
        updatesById: MutableMap<Long, ScoreRecord>,
    ) {
        state.bestRecords.bestFrameOverflowRecords().forEach { overflowRecord ->
            applyRecordUpdate(
                state,
                updatesById,
                overflowRecord.withFrameMembership(isBestFrame = false),
            )
        }
    }

    private fun List<ScoreRecord>.bestFrameOverflowRecords(): List<ScoreRecord> {
        if (size <= Constants.BEST_FRAME_SIZE) {
            return emptyList()
        }

        return sortedWith(
            compareBy<ScoreRecord> { record -> record.rateValue() }
                .thenBy { record -> record.id },
        ).take(size - Constants.BEST_FRAME_SIZE)
    }

    private suspend fun handleSpecialReservedUpdate(
        repository: AppRepository,
        newRecord: ScoreRecord,
        reservedRecords: List<ScoreRecord>,
    ) {
        val lowestReservedRecord = reservedRecords.minWithOrNull(
            compareBy<ScoreRecord> { record -> record.rateValue() }
                .thenBy { record -> record.date ?: Long.MAX_VALUE }
                .thenBy { record -> record.id },
        )

        if (lowestReservedRecord == null) {
            repository.updateScoreRecord(newRecord.withFrameMembership(isReservedFrame = true))
            return
        }

        if (newRecord.rateValue() <= lowestReservedRecord.rateValue()) {
            repository.updateScoreRecord(newRecord.withFrameMembership(isReservedFrame = false))
            return
        }

        if (reservedRecords.size >= Constants.RESERVE_FRAME_SIZE) {
            repository.updateScoreRecord(lowestReservedRecord.withFrameMembership(isReservedFrame = false))
        }

        repository.updateScoreRecord(newRecord.withFrameMembership(isReservedFrame = true))
    }

    private fun ScoreRecord.withFrameMembership(
        isBestFrame: Boolean = this.isBestFrame,
        isReservedFrame: Boolean = this.isReservedFrame,
    ): ScoreRecord {
        return copy(
            status = if (isBestFrame || isReservedFrame) Constants.STATUS_CALCULATED else Constants.STATUS_TRASH,
            isBestFrame = isBestFrame,
            isReservedFrame = isReservedFrame,
        )
    }

    private fun ScoreRecord.rateValue(): Float = singleRate ?: 0f

    private fun ScoreRecord.chartKey(): String? {
        val songName = songName?.trim()
        val difficulty = difficulty?.trim()
        if (songName.isNullOrBlank() || difficulty.isNullOrBlank()) {
            return null
        }
        return "$songName|$difficulty"
    }

    private data class FrameUpdateState(
        val recordsById: MutableMap<Long, ScoreRecord>,
        val bestRecords: MutableList<ScoreRecord>,
        val reservedRecords: MutableList<ScoreRecord>,
    )

    private val oldestReservedComparator = compareBy<ScoreRecord>(
        { record -> record.date == null },
        { record -> record.date ?: Long.MAX_VALUE },
        { record -> record.id },
    )
}