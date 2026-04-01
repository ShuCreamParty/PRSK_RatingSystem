package com.example.sekairatingsystem.domain

import com.example.sekairatingsystem.Constants
import com.example.sekairatingsystem.data.entity.ScoreRecord
import com.example.sekairatingsystem.data.repository.AppRepository
import java.util.PriorityQueue

object RatingCalculator {
    fun calculateSingleRate(record: ScoreRecord): Float {
        val level = record.level?.toFloat() ?: return 0f
        if (record.isAllPerfect == true) {
            return level
        }

        val perfectCount = record.perfectCount ?: return 0f
        val greatCount = record.greatCount ?: return 0f
        val goodCount = record.goodCount ?: return 0f
        val badCount = record.badCount ?: return 0f
        val missCount = record.missCount ?: return 0f

        val totalNotes = perfectCount + greatCount + goodCount + badCount + missCount
        if (totalNotes <= 0) {
            return 0f
        }

        val weightedScore = (perfectCount * 3) + (greatCount * 2) + goodCount
        return level * (weightedScore.toFloat() / (totalNotes.toFloat() * 3f))
    }

    suspend fun updateBestFrame(repository: AppRepository, newRecord: ScoreRecord) {
        val songName = newRecord.songName?.trim()
        val difficulty = newRecord.difficulty?.trim()
        if (songName.isNullOrBlank() || difficulty.isNullOrBlank()) {
            return
        }

        val bestRecords = repository.getBestFrameRecordsSnapshot()
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
        val currentRate = workingRecord.rateValue()
        val specialConditionA = (workingRecord.goodCount ?: 0) + (workingRecord.badCount ?: 0) + (workingRecord.missCount ?: 0) <= 7
        val previousHighestRate = if (workingRecord.id > 0L) {
            repository.getHighestSingleRateExcludingRecord(songName, difficulty, newRecord.id)
        } else {
            repository.getHighestSingleRate(songName, difficulty)
        }
        val specialConditionB = previousHighestRate == null || currentRate > previousHighestRate

        if (specialConditionA || specialConditionB) {
            handleSpecialReservedUpdate(repository, workingRecord, reservedRecords)
            return
        }

        if (reservedRecords.size < Constants.RESERVE_FRAME_SIZE) {
            repository.updateScoreRecord(workingRecord.withFrameMembership(isReservedFrame = true))
            return
        }

        val duplicateReservedRecord = repository.getOldestDuplicateReservedRecord()
        if (duplicateReservedRecord != null) {
            repository.updateScoreRecord(duplicateReservedRecord.withFrameMembership(isReservedFrame = false))
            repository.updateScoreRecord(workingRecord.withFrameMembership(isReservedFrame = true))
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
}