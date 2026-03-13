package com.example.sekairatingsystem.data.repository

import com.example.sekairatingsystem.data.dao.AppDao
import com.example.sekairatingsystem.data.entity.ScoreRecord
import com.example.sekairatingsystem.data.entity.SongMaster
import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val appDao: AppDao,
) {
    fun getAllSongMasters(): Flow<List<SongMaster>> = appDao.getAllSongMasters()

    suspend fun getAllSongMastersSnapshot(): List<SongMaster> = appDao.getAllSongMastersSnapshot()

    suspend fun getSongMasterByName(songName: String): SongMaster? = appDao.getSongMasterByName(songName)

    suspend fun insertSongMaster(songMaster: SongMaster) {
        appDao.insertSongMaster(songMaster)
    }

    suspend fun insertSongMasters(songMasters: List<SongMaster>) {
        appDao.insertSongMasters(songMasters)
    }

    suspend fun updateSongMaster(songMaster: SongMaster) {
        appDao.updateSongMaster(songMaster)
    }

    suspend fun deleteSongMaster(songMaster: SongMaster) {
        appDao.deleteSongMaster(songMaster)
    }

    suspend fun insertScoreRecord(scoreRecord: ScoreRecord): Long = appDao.insertScoreRecord(scoreRecord)

    suspend fun insertScoreRecords(scoreRecords: List<ScoreRecord>) {
        appDao.insertScoreRecords(scoreRecords)
    }

    suspend fun updateScoreRecord(scoreRecord: ScoreRecord) {
        appDao.updateScoreRecord(scoreRecord)
    }

    suspend fun updateScoreRecords(scoreRecords: List<ScoreRecord>) {
        appDao.updateScoreRecords(scoreRecords)
    }

    suspend fun deleteScoreRecord(scoreRecord: ScoreRecord) {
        appDao.deleteScoreRecord(scoreRecord)
    }

    suspend fun deleteAllScoreRecords(): Int = appDao.deleteAllScoreRecords()

    fun getScoreRecordsByStatus(status: String): Flow<List<ScoreRecord>> =
        appDao.getScoreRecordsByStatus(status)

    suspend fun getScoreRecordsByStatusSnapshot(status: String): List<ScoreRecord> =
        appDao.getScoreRecordsByStatusSnapshot(status)

    fun getCalculatedRecords(): Flow<List<ScoreRecord>> = appDao.getCalculatedRecords()

    fun getBestFrameRecords(): Flow<List<ScoreRecord>> = appDao.getBestFrameRecords()

    suspend fun getBestFrameRecordsSnapshot(): List<ScoreRecord> = appDao.getBestFrameRecordsSnapshot()

    fun getReservedFrameRecords(): Flow<List<ScoreRecord>> = appDao.getReservedFrameRecords()

    fun getTotalScoreRecordsCount(): Flow<Int> = appDao.getTotalScoreRecordsCount()

    fun getScoreRecordById(recordId: Long): Flow<ScoreRecord?> = appDao.getScoreRecordById(recordId)

    suspend fun getScoreRecordByIdSnapshot(recordId: Long): ScoreRecord? = appDao.getScoreRecordByIdSnapshot(recordId)

    suspend fun getAllScoreRecordImageUris(): List<String> = appDao.getAllScoreRecordImageUris()

    suspend fun getAllScoreRecordsSnapshot(): List<ScoreRecord> = appDao.getAllScoreRecordsSnapshot()

    suspend fun getHighestSingleRate(songName: String, difficulty: String): Float? =
        appDao.getHighestSingleRate(songName, difficulty)

    suspend fun getHighestSingleRateExcludingRecord(
        songName: String,
        difficulty: String,
        excludedRecordId: Long,
    ): Float? = appDao.getHighestSingleRateExcludingRecord(songName, difficulty, excludedRecordId)

    suspend fun getReservedRecordsOldestFirst(): List<ScoreRecord> = appDao.getReservedRecordsOldestFirst()

    suspend fun getOldestDuplicateReservedRecord(): ScoreRecord? = appDao.getOldestDuplicateReservedRecord()

    suspend fun getBestSingleRate(songName: String, difficulty: String): Float? =
        appDao.getBestSingleRate(songName, difficulty)
}