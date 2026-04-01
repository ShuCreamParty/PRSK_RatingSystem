package com.example.sekairatingsystem.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.sekairatingsystem.data.entity.ScoreRecord
import com.example.sekairatingsystem.data.entity.SongMaster
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongMaster(songMaster: SongMaster)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongMasters(songMasters: List<SongMaster>)

    @Update
    suspend fun updateSongMaster(songMaster: SongMaster)

    @Delete
    suspend fun deleteSongMaster(songMaster: SongMaster)

    @Query("SELECT * FROM song_masters ORDER BY songName ASC")
    fun getAllSongMasters(): Flow<List<SongMaster>>

    @Query("SELECT * FROM song_masters ORDER BY songName ASC")
    suspend fun getAllSongMastersSnapshot(): List<SongMaster>

    @Query("SELECT * FROM song_masters WHERE songName = :songName LIMIT 1")
    suspend fun getSongMasterByName(songName: String): SongMaster?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScoreRecord(scoreRecord: ScoreRecord): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertScoreRecords(scoreRecords: List<ScoreRecord>)

    @Update
    suspend fun updateScoreRecord(scoreRecord: ScoreRecord)

    @Update
    suspend fun updateScoreRecords(scoreRecords: List<ScoreRecord>)

    @Delete
    suspend fun deleteScoreRecord(scoreRecord: ScoreRecord)

    @Query("DELETE FROM score_records")
    suspend fun deleteAllScoreRecords(): Int

    @Query("SELECT * FROM score_records WHERE status = :status ORDER BY id DESC")
    fun getScoreRecordsByStatus(status: String): Flow<List<ScoreRecord>>

    @Query("SELECT * FROM score_records WHERE status = :status ORDER BY id DESC")
    suspend fun getScoreRecordsByStatusSnapshot(status: String): List<ScoreRecord>

    @Query("SELECT * FROM score_records WHERE status = 'CALCULATED' OR isBestFrame = 1 OR isReservedFrame = 1 ORDER BY id DESC")
    fun getCalculatedRecords(): Flow<List<ScoreRecord>>

    @Query("SELECT * FROM score_records WHERE isBestFrame = 1 ORDER BY singleRate DESC, id DESC")
    fun getBestFrameRecords(): Flow<List<ScoreRecord>>

    @Query("SELECT * FROM score_records WHERE isBestFrame = 1 ORDER BY singleRate DESC, id DESC")
    suspend fun getBestFrameRecordsSnapshot(): List<ScoreRecord>

    @Query(
      """
      SELECT * FROM score_records
      WHERE isReservedFrame = 1
      ORDER BY
        CASE WHEN date IS NULL THEN 1 ELSE 0 END ASC,
        date ASC,
        id ASC
      """
    )
    fun getReservedFrameRecords(): Flow<List<ScoreRecord>>

    @Query("SELECT * FROM score_records WHERE id = :recordId LIMIT 1")
    fun getScoreRecordById(recordId: Long): Flow<ScoreRecord?>

    @Query("SELECT COUNT(*) FROM score_records")
    fun getTotalScoreRecordsCount(): Flow<Int>

    @Query("SELECT * FROM score_records WHERE id = :recordId LIMIT 1")
    suspend fun getScoreRecordByIdSnapshot(recordId: Long): ScoreRecord?

    @Query("SELECT imageUri FROM score_records")
    suspend fun getAllScoreRecordImageUris(): List<String>

    @Query("SELECT * FROM score_records ORDER BY id DESC")
    suspend fun getAllScoreRecordsSnapshot(): List<ScoreRecord>

    @Query(
      """
      SELECT MAX(singleRate)
      FROM score_records
      WHERE songName = :songName
        AND difficulty = :difficulty
        AND status != 'FAILED'
        AND singleRate IS NOT NULL
      """
    )
    suspend fun getHighestSingleRate(songName: String, difficulty: String): Float?

    @Query(
      """
      SELECT MAX(singleRate)
      FROM score_records
      WHERE songName = :songName
        AND difficulty = :difficulty
        AND status != 'FAILED'
        AND singleRate IS NOT NULL
        AND id != :excludedRecordId
      """
    )
    suspend fun getHighestSingleRateExcludingRecord(
      songName: String,
      difficulty: String,
      excludedRecordId: Long,
    ): Float?

    @Query(
      """
      SELECT * FROM score_records
      WHERE isReservedFrame = 1
      ORDER BY
        CASE WHEN date IS NULL THEN 1 ELSE 0 END ASC,
        date ASC,
        id ASC
      """
    )
    suspend fun getReservedRecordsOldestFirst(): List<ScoreRecord>

    @Query(
      """
      SELECT * FROM score_records
        WHERE isReservedFrame = 1
        AND songName IS NOT NULL
        AND difficulty IS NOT NULL
        AND (songName || '|' || difficulty) IN (
          SELECT songName || '|' || difficulty
          FROM score_records
              WHERE isReservedFrame = 1
          AND songName IS NOT NULL
          AND difficulty IS NOT NULL
          GROUP BY songName, difficulty
          HAVING COUNT(*) >= 2
        )
      ORDER BY
        CASE WHEN date IS NULL THEN 1 ELSE 0 END ASC,
        date ASC,
        id ASC
      LIMIT 1
      """
    )
    suspend fun getOldestDuplicateReservedRecord(): ScoreRecord?

    @Query(
        """
        SELECT MAX(singleRate)
        FROM score_records
        WHERE songName = :songName
          AND difficulty = :difficulty
          AND singleRate IS NOT NULL
        """
    )
    suspend fun getBestSingleRate(songName: String, difficulty: String): Float?

    @Query(
        """
        SELECT level
        FROM score_records
        WHERE songName = :songName
          AND difficulty = :difficulty
          AND level IS NOT NULL
          AND id != :excludedRecordId
        ORDER BY
          CASE WHEN date IS NULL THEN 1 ELSE 0 END ASC,
          date DESC,
          id DESC
        LIMIT 1
        """
    )
    suspend fun getLatestLevelForChartExcludingRecord(
        songName: String,
        difficulty: String,
        excludedRecordId: Long,
    ): Int?

    @Query(
        """
        UPDATE score_records
        SET level = :targetLevel
        WHERE songName = :songName
          AND difficulty = :difficulty
          AND level IS NOT NULL
          AND level != :targetLevel
        """
    )
    suspend fun updateLevelsForChart(
        songName: String,
        difficulty: String,
        targetLevel: Int,
    ): Int
}