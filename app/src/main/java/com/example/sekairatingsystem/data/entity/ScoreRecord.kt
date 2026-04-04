package com.example.sekairatingsystem.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "score_records",
    indices = [
        Index(value = ["imageUri"]),
        Index(value = ["status"]),
        Index(value = ["songName", "difficulty"]),
        Index(value = ["isBestFrame", "singleRate", "id"]),
        Index(value = ["isReservedFrame", "date", "id"]),
        Index(value = ["songName", "difficulty", "singleRate"]),
    ],
)
data class ScoreRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val imageUri: String,
    val date: Long? = null,
    val status: String,
    val isBestFrame: Boolean = false,
    val isReservedFrame: Boolean = false,
    val songName: String? = null,
    val difficulty: String? = null,
    val level: Int? = null,
    val perfectCount: Int? = null,
    val greatCount: Int? = null,
    val goodCount: Int? = null,
    val badCount: Int? = null,
    val missCount: Int? = null,
    val isAllPerfect: Boolean? = null,
    val singleRate: Float? = null,
)