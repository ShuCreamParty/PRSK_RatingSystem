package com.example.sekairatingsystem.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "song_masters")
data class SongMaster(
    @PrimaryKey
    val songName: String,
    val easyLevel: Int? = null,
    val normalLevel: Int? = null,
    val hardLevel: Int? = null,
    val expertLevel: Int? = null,
    val masterLevel: Int? = null,
    val appendLevel: Int? = null,
)