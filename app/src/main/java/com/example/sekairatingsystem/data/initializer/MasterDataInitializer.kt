package com.example.sekairatingsystem.data.initializer

import android.content.Context
import com.example.sekairatingsystem.data.entity.SongMaster
import com.example.sekairatingsystem.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MasterDataInitializer(
    context: Context,
    private val repository: AppRepository,
) {
    private val appContext = context.applicationContext
    private val sharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun initializeIfNeeded() {
        withContext(Dispatchers.IO) {
            if (sharedPreferences.getBoolean(KEY_MASTER_DATA_INITIALIZED, false)) {
                return@withContext
            }

            repository.insertSongMasters(createInitialSongMasters())

            check(sharedPreferences.edit().putBoolean(KEY_MASTER_DATA_INITIALIZED, true).commit()) {
                "Failed to persist master data initialization flag."
            }
        }
    }

    private fun createInitialSongMasters(): List<SongMaster> {
        return listOf(
            SongMaster("Tell Your World", easyLevel = 5, normalLevel = 10, hardLevel = 15, expertLevel = 23, masterLevel = 27, appendLevel = 30),
            SongMaster("初音ミクの消失", easyLevel = 9, normalLevel = 14, hardLevel = 20, expertLevel = 31, masterLevel = 34, appendLevel = 37),
            SongMaster("千本桜", easyLevel = 7, normalLevel = 12, hardLevel = 18, expertLevel = 26, masterLevel = 29, appendLevel = 32),
            SongMaster("アイドル", easyLevel = 8, normalLevel = 13, hardLevel = 19, expertLevel = 28, masterLevel = 31, appendLevel = 34),
            SongMaster("マーシャル・マキシマイザー", easyLevel = 8, normalLevel = 13, hardLevel = 19, expertLevel = 28, masterLevel = 32, appendLevel = 35),
            SongMaster("KING", easyLevel = 7, normalLevel = 12, hardLevel = 18, expertLevel = 27, masterLevel = 30, appendLevel = 33),
            SongMaster("ロキ", easyLevel = 6, normalLevel = 11, hardLevel = 17, expertLevel = 25, masterLevel = 29, appendLevel = 31),
            SongMaster("群青讃歌", easyLevel = 5, normalLevel = 10, hardLevel = 16, expertLevel = 24, masterLevel = 27, appendLevel = 30),
        )
    }

    companion object {
        private const val PREFS_NAME = "sekai_rating_preferences"
        private const val KEY_MASTER_DATA_INITIALIZED = "master_data_initialized"
    }
}