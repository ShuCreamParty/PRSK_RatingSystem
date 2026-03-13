package com.example.sekairatingsystem.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sekairatingsystem.Constants
import com.example.sekairatingsystem.data.AppDatabase
import com.example.sekairatingsystem.data.entity.ScoreRecord
import com.example.sekairatingsystem.data.entity.SongMaster
import com.example.sekairatingsystem.data.initializer.MasterDataInitializer
import com.example.sekairatingsystem.data.repository.AppRepository
import com.example.sekairatingsystem.domain.RatingCalculator
import com.example.sekairatingsystem.ocr.ScoreOcrAnalyzer
import com.example.sekairatingsystem.ocr.SongNameResolver
import com.example.sekairatingsystem.util.ImageFileScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppRepository(AppDatabase.getDatabase(application).appDao())
    private val masterDataInitializer = MasterDataInitializer(application.applicationContext, repository)
    private val preferences = application.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _userName = MutableStateFlow(preferences.getString(KEY_USER_NAME, DEFAULT_USER_NAME) ?: DEFAULT_USER_NAME)
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _themeColor = MutableStateFlow(preferences.getLong(KEY_THEME_COLOR, DEFAULT_THEME_COLOR))
    val themeColor: StateFlow<Long> = _themeColor.asStateFlow()

    private val _oshiName = MutableStateFlow(preferences.getString(KEY_OSHI_NAME, DEFAULT_OSHI_NAME) ?: DEFAULT_OSHI_NAME)
    val oshiName: StateFlow<String> = _oshiName.asStateFlow()

    val calculatedCount: StateFlow<Int> = repository.getCalculatedRecords().map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalPlayCount: StateFlow<Int> = repository.getTotalScoreRecordsCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _userNameMessage = MutableStateFlow<String?>(null)
    val userNameMessage: StateFlow<String?> = _userNameMessage.asStateFlow()

    val failedRecords: StateFlow<List<ScoreRecord>> = repository.getScoreRecordsByStatus(Constants.STATUS_FAILED)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val bestFrameRecords: StateFlow<List<ScoreRecord>> = repository.getBestFrameRecords()
        .map { records ->
            records.sortedWith(
                compareByDescending<ScoreRecord> { record -> record.singleRate ?: 0f }
                    .thenByDescending { record -> record.level ?: 0 }
                    .thenBy { record -> record.id },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val recentFrameRecords: StateFlow<List<ScoreRecord>> = repository.getReservedFrameRecords()
        .map(::extractRecentFrameRecords)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val bestRateSum: StateFlow<Float> = bestFrameRecords
        .map(::sumRateValues)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0f,
        )

    val recentRateSum: StateFlow<Float> = recentFrameRecords
        .map(::sumRateValues)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0f,
        )

    val totalRate: StateFlow<Float> = combine(bestRateSum, recentRateSum) { best, recent ->
        best + recent
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0f,
    )

    private val _isInitializing = MutableStateFlow(false)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    private val _isMasterDataReady = MutableStateFlow(false)
    val isMasterDataReady: StateFlow<Boolean> = _isMasterDataReady.asStateFlow()

    private val _initializationErrorMessage = MutableStateFlow<String?>(null)
    val initializationErrorMessage: StateFlow<String?> = _initializationErrorMessage.asStateFlow()

    private val _selectedFolderUri = MutableStateFlow(
        preferences.getString(KEY_SELECTED_FOLDER_URI, null)?.let(Uri::parse),
    )
    val selectedFolderUri: StateFlow<Uri?> = _selectedFolderUri.asStateFlow()

    private val _isScanningFolder = MutableStateFlow(false)
    val isScanningFolder: StateFlow<Boolean> = _isScanningFolder.asStateFlow()

    private val _importedImageCount = MutableStateFlow(0)
    val importedImageCount: StateFlow<Int> = _importedImageCount.asStateFlow()

    private val _folderScanMessage = MutableStateFlow<String?>(null)
    val folderScanMessage: StateFlow<String?> = _folderScanMessage.asStateFlow()

    private val _isAnalyzingOcr = MutableStateFlow(false)
    val isAnalyzingOcr: StateFlow<Boolean> = _isAnalyzingOcr.asStateFlow()

    private val _ocrProcessedCount = MutableStateFlow(0)
    val ocrProcessedCount: StateFlow<Int> = _ocrProcessedCount.asStateFlow()

    private val _ocrFailedCount = MutableStateFlow(0)
    val ocrFailedCount: StateFlow<Int> = _ocrFailedCount.asStateFlow()

    private val _ocrStatusMessage = MutableStateFlow<String?>(null)
    val ocrStatusMessage: StateFlow<String?> = _ocrStatusMessage.asStateFlow()

    private val _isCalculatingRates = MutableStateFlow(false)
    val isCalculatingRates: StateFlow<Boolean> = _isCalculatingRates.asStateFlow()

    private val _rateCalculationMessage = MutableStateFlow<String?>(null)
    val rateCalculationMessage: StateFlow<String?> = _rateCalculationMessage.asStateFlow()

    private val _isSavingEditedRecord = MutableStateFlow(false)
    val isSavingEditedRecord: StateFlow<Boolean> = _isSavingEditedRecord.asStateFlow()

    private val _isDeletingEditedImage = MutableStateFlow(false)
    val isDeletingEditedImage: StateFlow<Boolean> = _isDeletingEditedImage.asStateFlow()

    private val _manualEditMessage = MutableStateFlow<String?>(null)
    val manualEditMessage: StateFlow<String?> = _manualEditMessage.asStateFlow()

    private val _isDeletingTrash = MutableStateFlow(false)
    val isDeletingTrash: StateFlow<Boolean> = _isDeletingTrash.asStateFlow()

    private val _trashDeletionMessage = MutableStateFlow<String?>(null)
    val trashDeletionMessage: StateFlow<String?> = _trashDeletionMessage.asStateFlow()

    private val _isResettingDatabase = MutableStateFlow(false)
    val isResettingDatabase: StateFlow<Boolean> = _isResettingDatabase.asStateFlow()

    private val _databaseResetMessage = MutableStateFlow<String?>(null)
    val databaseResetMessage: StateFlow<String?> = _databaseResetMessage.asStateFlow()

    fun initializeMasterData() {
        if (_isInitializing.value || _isMasterDataReady.value) {
            return
        }

        viewModelScope.launch {
            _isInitializing.value = true

            runCatching {
                masterDataInitializer.initializeIfNeeded()
            }.onSuccess {
                _isMasterDataReady.value = true
                _initializationErrorMessage.value = null
            }.onFailure { throwable ->
                _initializationErrorMessage.value = throwable.message ?: "Unknown initialization error"
            }

            _isInitializing.value = false
        }
    }

    fun onFolderSelected(uri: Uri, context: Context) {
        if (_isScanningFolder.value || _isAnalyzingOcr.value || _isCalculatingRates.value) {
            return
        }

        viewModelScope.launch {
            _isScanningFolder.value = true

            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )

                val imageFiles = withContext(Dispatchers.IO) {
                    ImageFileScanner.getImageFiles(context, uri)
                }

                val existingRecords = repository.getAllScoreRecordsSnapshot()
                val existingByUri = existingRecords
                    .filter { record -> record.imageUri.isNotBlank() }
                    .associateBy { record -> record.imageUri }
                val recordsByTimestamp = existingRecords
                    .filter { record -> record.imageUri.isNotBlank() && record.date != null }
                    .groupBy { record -> record.date!! }
                    .mapValues { (_, records) ->
                        records.sortedByDescending { record -> record.id }.toMutableList()
                    }
                    .toMutableMap()

                val newRecords = mutableListOf<ScoreRecord>()
                val relinkedRecords = mutableListOf<ScoreRecord>()

                imageFiles.forEach { imageFile ->
                    val imageUri = imageFile.uri.toString()
                    if (existingByUri.containsKey(imageUri)) {
                        return@forEach
                    }

                    val renamedCandidate = imageFile.lastModified?.let { timestamp ->
                        recordsByTimestamp[timestamp]
                            ?.firstOrNull()
                            ?.also { candidate -> recordsByTimestamp[timestamp]?.remove(candidate) }
                    }

                    if (renamedCandidate != null) {
                        relinkedRecords += renamedCandidate.copy(
                            imageUri = imageUri,
                            date = imageFile.lastModified,
                        )
                    } else {
                        newRecords += ScoreRecord(
                            imageUri = imageUri,
                            date = imageFile.lastModified,
                            status = Constants.STATUS_UNREAD,
                            isBestFrame = false,
                            isReservedFrame = false,
                        )
                    }
                }

                relinkedRecords.forEach { record ->
                    repository.updateScoreRecord(record)
                }

                if (newRecords.isNotEmpty()) {
                    repository.insertScoreRecords(newRecords)
                }

                FolderImportSummary(
                    selectedUri = uri,
                    insertedCount = newRecords.size,
                    relinkedCount = relinkedRecords.size,
                )
            }.onSuccess { summary ->
                _selectedFolderUri.value = summary.selectedUri
                _importedImageCount.value = summary.insertedCount
                preferences.edit().putString(KEY_SELECTED_FOLDER_URI, summary.selectedUri.toString()).apply()
                _folderScanMessage.value = when {
                    summary.insertedCount > 0 && summary.relinkedCount > 0 -> {
                        "新規 ${summary.insertedCount} 件を登録し、リネーム済み画像 ${summary.relinkedCount} 件を再リンクしました"
                    }
                    summary.insertedCount > 0 -> {
                        "新規の未読み取り画像を ${summary.insertedCount} 件取り込みました"
                    }
                    summary.relinkedCount > 0 -> {
                        "リネーム済み画像 ${summary.relinkedCount} 件を既存レコードへ再リンクしました"
                    }
                    else -> {
                        "新しく取り込む画像はありませんでした"
                    }
                }
            }.onFailure { throwable ->
                _folderScanMessage.value = throwable.message ?: "フォルダの読み込みに失敗しました"
            }

            _isScanningFolder.value = false
        }
    }

    fun processAllUnreadData(context: Context) {
        if (_isInitializing.value || _isScanningFolder.value || _isAnalyzingOcr.value || _isCalculatingRates.value) {
            return
        }

        viewModelScope.launch {
            // --- Phase 1: OCR ---
            _isAnalyzingOcr.value = true
            _ocrProcessedCount.value = 0
            _ocrFailedCount.value = 0
            _ocrStatusMessage.value = "未読み取り画像のOCR解析を開始します..."

            var ocrSucceeded = false
            runCatching {
                val unreadRecords = repository.getScoreRecordsByStatusSnapshot(Constants.STATUS_UNREAD)
                    .filter(::needsOcrAnalysis)
                if (unreadRecords.isEmpty()) {
                    return@runCatching OcrSummary(0, 0)
                }

                val songMasters = repository.getAllSongMastersSnapshot()
                val analyzedResults = analyzeUnreadRecordsInParallel(
                    context = context,
                    unreadRecords = unreadRecords,
                    songMasters = songMasters,
                )

                if (analyzedResults.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        repository.updateScoreRecords(analyzedResults.map { analyzed -> analyzed.record })
                    }
                }

                val processedCount = analyzedResults.size
                val failedCount = analyzedResults.count { analyzed -> !analyzed.isSuccess }

                _ocrProcessedCount.value = processedCount
                _ocrFailedCount.value = failedCount

                OcrSummary(processedCount, failedCount)
            }.onSuccess { summary ->
                ocrSucceeded = true
                _ocrStatusMessage.value = if (summary.processedCount == 0) {
                    "OCR対象の未読み取り画像がありませんでした"
                } else {
                    "OCR解析が完了しました。成功: ${summary.processedCount - summary.failedCount} 件 / 失敗: ${summary.failedCount} 件"
                }
            }.onFailure { throwable ->
                _ocrStatusMessage.value = "OCR解析に失敗しました: ${throwable.localizedMessage}"
            }

            _isAnalyzingOcr.value = false

            if (!ocrSucceeded) return@launch

            // --- Phase 2: Rate Calculation ---
            _isCalculatingRates.value = true
            _rateCalculationMessage.value = "レート計算を開始します..."

            runCatching {
                withContext(Dispatchers.IO) {
                    val unreadRecords = repository.getScoreRecordsByStatusSnapshot(Constants.STATUS_UNREAD)
                    val calculableRecords = unreadRecords.filter(::isReadyForRateCalculation)
                    val skippedCount = unreadRecords.size - calculableRecords.size

                    if (calculableRecords.isEmpty()) {
                        return@withContext RateCalculationSummary(0, skippedCount)
                    }

                    val ratedRecords = calculableRecords.map { record ->
                        record.copy(singleRate = RatingCalculator.calculateSingleRate(record))
                    }

                    for (ratedRecord in ratedRecords) {
                        RatingCalculator.updateBestFrame(repository, ratedRecord)
                        RatingCalculator.updateReservedFrame(repository, ratedRecord)
                    }

                    RateCalculationSummary(ratedRecords.size, skippedCount)
                }
            }.onSuccess { summary ->
                _rateCalculationMessage.value = when {
                    summary.processedCount == 0 && summary.skippedCount > 0 -> {
                        "計算可能なデータがありません。入力不足 ${summary.skippedCount} 件"
                    }
                    summary.processedCount == 0 -> {
                        "新しく計算されたデータはありません。"
                    }
                    summary.skippedCount > 0 -> {
                        "レート計算完了: ${summary.processedCount} 件 / 入力不足 ${summary.skippedCount} 件"
                    }
                    else -> {
                        "レート計算完了: ${summary.processedCount} 件"
                    }
                }
            }.onFailure { throwable ->
                _rateCalculationMessage.value = "レート計算に失敗しました: ${throwable.localizedMessage}"
            }

            _isCalculatingRates.value = false
        }
    }

    fun getScoreRecord(recordId: Long): Flow<ScoreRecord?> = repository.getScoreRecordById(recordId)

    fun getScoreRecordsByStatus(status: String): Flow<List<ScoreRecord>> = repository.getScoreRecordsByStatus(status)

    fun getCalculatedRecords(): Flow<List<ScoreRecord>> = repository.getCalculatedRecords()

    fun saveUserName(userName: String) {
        val normalizedUserName = userName.trim()

        preferences.edit()
            .putString(KEY_USER_NAME, normalizedUserName)
            .apply()

        _userName.value = normalizedUserName
        _userNameMessage.value = if (normalizedUserName.isBlank()) {
            "ユーザー名をクリアしました"
        } else {
            "ユーザー名を保存しました"
        }
    }

    fun saveEditedScoreRecord(record: ScoreRecord, onSaved: () -> Unit) {
        if (_isSavingEditedRecord.value || _isDeletingEditedImage.value) {
            return
        }

        viewModelScope.launch {
            _isSavingEditedRecord.value = true
            _manualEditMessage.value = null

            runCatching {
                val normalizedRecord = normalizeEditedRecord(record)
                if (normalizedRecord.id <= 0L) {
                    throw IllegalArgumentException("保存対象のレコードIDが不正です")
                }

                upsertSongMasterFromEditedRecord(
                    songName = normalizedRecord.songName,
                    difficulty = normalizedRecord.difficulty,
                    level = normalizedRecord.level,
                )

                if (!isReadyForRateCalculation(normalizedRecord)) {
                    throw IllegalArgumentException("レート再計算に必要な項目が不足しています")
                }

                val recalculationBaseRecord = normalizedRecord.copy(
                    status = Constants.STATUS_UNREAD,
                    isBestFrame = false,
                    isReservedFrame = false,
                    singleRate = null,
                )
                repository.updateScoreRecord(recalculationBaseRecord)

                val ratedRecord = recalculationBaseRecord.copy(
                    status = Constants.STATUS_CALCULATED,
                    singleRate = RatingCalculator.calculateSingleRate(recalculationBaseRecord),
                )
                RatingCalculator.updateBestFrame(repository, ratedRecord)
                val updatedAfterBest = repository.getScoreRecordByIdSnapshot(ratedRecord.id) ?: ratedRecord
                RatingCalculator.updateReservedFrame(repository, updatedAfterBest.copy(singleRate = ratedRecord.singleRate))
            }.onSuccess {
                _manualEditMessage.value = null
                onSaved()
            }.onFailure { throwable ->
                _manualEditMessage.value = throwable.message ?: "保存に失敗しました"
            }

            _isSavingEditedRecord.value = false
        }
    }

    fun deleteEditedRecordImage(recordId: Long, context: Context, onDeleted: () -> Unit) {
        if (_isDeletingEditedImage.value || _isSavingEditedRecord.value) {
            return
        }

        viewModelScope.launch {
            _isDeletingEditedImage.value = true
            _manualEditMessage.value = null

            runCatching {
                val targetRecord = repository.getScoreRecordByIdSnapshot(recordId)
                    ?: throw IllegalArgumentException("削除対象のレコードが見つかりません")

                if (isRecordProtectedForImageDeletion(targetRecord)) {
                    throw IllegalStateException("この画像は現在のレート計算に使用中のため削除できません")
                }

                val imageUriText = targetRecord.imageUri.trim()
                if (imageUriText.isNotEmpty()) {
                    val deleteSucceeded = runCatching {
                        DocumentFile.fromSingleUri(
                            context.applicationContext,
                            Uri.parse(imageUriText),
                        )?.delete() == true
                    }.getOrDefault(false)

                    if (!deleteSucceeded) {
                        throw IllegalStateException("画像の削除に失敗しました。権限またはファイル状態を確認してください")
                    }
                }

                repository.updateScoreRecord(
                    targetRecord.copy(
                        imageUri = "",
                        status = Constants.STATUS_MEDIA_DELETED,
                    ),
                )
            }.onSuccess {
                _manualEditMessage.value = null
                onDeleted()
            }.onFailure { throwable ->
                _manualEditMessage.value = throwable.message ?: "画像削除に失敗しました"
            }

            _isDeletingEditedImage.value = false
        }
    }

    fun deleteTrashFiles(context: Context) {
        if (_isDeletingTrash.value) {
            return
        }

        viewModelScope.launch {
            _isDeletingTrash.value = true
            _trashDeletionMessage.value = null

            runCatching {
                withContext(Dispatchers.IO) {
                    val trashRecords = repository.getScoreRecordsByStatusSnapshot(Constants.STATUS_TRASH)
                        .filter { record -> record.imageUri.isNotBlank() }
                    if (trashRecords.isEmpty()) {
                        return@withContext TrashDeletionSummary(
                            targetCount = 0,
                            deletedCount = 0,
                            failedCount = 0,
                            blockedCount = 0,
                        )
                    }

                    var deletedCount = 0
                    var failedCount = 0
                    var blockedCount = 0

                    for (record in trashRecords) {
                        if (isRecordProtectedForImageDeletion(record)) {
                            blockedCount += 1
                            continue
                        }

                        val deleteSucceeded = runCatching {
                            DocumentFile.fromSingleUri(
                                context.applicationContext,
                                Uri.parse(record.imageUri),
                            )?.delete() == true
                        }.getOrDefault(false)

                        if (deleteSucceeded) {
                            repository.updateScoreRecord(
                                record.copy(
                                    imageUri = "",
                                    status = Constants.STATUS_MEDIA_DELETED,
                                ),
                            )
                            deletedCount += 1
                        } else {
                            failedCount += 1
                        }
                    }

                    TrashDeletionSummary(
                        targetCount = trashRecords.size,
                        deletedCount = deletedCount,
                        failedCount = failedCount,
                        blockedCount = blockedCount,
                    )
                }
            }.onSuccess { summary ->
                _trashDeletionMessage.value = when {
                    summary.targetCount == 0 -> {
                        "削除対象の画像はありませんでした"
                    }
                    summary.deletedCount == 0 && summary.blockedCount > 0 -> {
                        "レート計算に使用中の画像が ${summary.blockedCount} 件あるため削除できません"
                    }
                    summary.failedCount > 0 -> {
                        "画像削除完了: ${summary.deletedCount} 件 / 失敗 ${summary.failedCount} 件 / 使用中で削除不可 ${summary.blockedCount} 件"
                    }
                    else -> {
                        "画像削除完了: ${summary.deletedCount} 件 / 使用中で削除不可 ${summary.blockedCount} 件"
                    }
                }
            }.onFailure { throwable ->
                _trashDeletionMessage.value = throwable.message ?: "画像削除に失敗しました"
            }

            _isDeletingTrash.value = false
        }
    }

    fun resetAllDatabaseData() {
        if (
            _isResettingDatabase.value ||
            _isInitializing.value ||
            _isScanningFolder.value ||
            _isAnalyzingOcr.value ||
            _isCalculatingRates.value ||
            _isSavingEditedRecord.value ||
            _isDeletingEditedImage.value ||
            _isDeletingTrash.value
        ) {
            return
        }

        viewModelScope.launch {
            _isResettingDatabase.value = true
            _databaseResetMessage.value = null

            runCatching {
                withContext(Dispatchers.IO) {
                    repository.deleteAllScoreRecords()
                }
            }.onSuccess { deletedCount ->
                _importedImageCount.value = 0
                _ocrProcessedCount.value = 0
                _ocrFailedCount.value = 0
                _folderScanMessage.value = null
                _ocrStatusMessage.value = null
                _rateCalculationMessage.value = null
                _manualEditMessage.value = null
                _trashDeletionMessage.value = null
                _databaseResetMessage.value = "DBリセット完了: ${deletedCount} 件のスコアデータを削除しました"
            }.onFailure { throwable ->
                _databaseResetMessage.value = throwable.message ?: "DBリセットに失敗しました"
            }

            _isResettingDatabase.value = false
        }
    }

    private fun needsOcrAnalysis(record: ScoreRecord): Boolean {
        return record.songName.isNullOrBlank() ||
            record.difficulty.isNullOrBlank() ||
            record.level == null ||
            record.perfectCount == null ||
            record.greatCount == null ||
            record.goodCount == null ||
            record.badCount == null ||
            record.missCount == null
    }

    private fun isReadyForRateCalculation(record: ScoreRecord): Boolean {
        return !record.songName.isNullOrBlank() &&
            !record.difficulty.isNullOrBlank() &&
            record.level != null &&
            record.perfectCount != null &&
            record.greatCount != null &&
            record.goodCount != null &&
            record.badCount != null &&
            record.missCount != null
    }

    private fun normalizeEditedRecord(record: ScoreRecord): ScoreRecord {
        return record.copy(
            songName = record.songName?.trim()?.takeIf(String::isNotBlank),
            difficulty = record.difficulty
                ?.trim()
                ?.uppercase(Locale.ROOT)
                ?.takeIf(String::isNotBlank),
        )
    }

    private suspend fun upsertSongMasterFromEditedRecord(
        songName: String?,
        difficulty: String?,
        level: Int?,
    ) {
        if (songName.isNullOrBlank()) {
            return
        }

        val existingMaster = repository.getSongMasterByName(songName)
        if (existingMaster == null) {
            repository.insertSongMaster(
                SongMaster(
                    songName = songName,
                    easyLevel = if (difficulty == "EASY") level else null,
                    normalLevel = if (difficulty == "NORMAL") level else null,
                    hardLevel = if (difficulty == "HARD") level else null,
                    expertLevel = if (difficulty == "EXPERT") level else null,
                    masterLevel = if (difficulty == "MASTER") level else null,
                    appendLevel = if (difficulty == "APPEND") level else null,
                ),
            )
            return
        }

        if (level == null) {
            return
        }

        val updatedMaster = when (difficulty) {
            "EASY" -> if (existingMaster.easyLevel != level) existingMaster.copy(easyLevel = level) else existingMaster
            "NORMAL" -> if (existingMaster.normalLevel != level) existingMaster.copy(normalLevel = level) else existingMaster
            "HARD" -> if (existingMaster.hardLevel != level) existingMaster.copy(hardLevel = level) else existingMaster
            "EXPERT" -> if (existingMaster.expertLevel != level) existingMaster.copy(expertLevel = level) else existingMaster
            "MASTER" -> if (existingMaster.masterLevel != level) existingMaster.copy(masterLevel = level) else existingMaster
            "APPEND" -> if (existingMaster.appendLevel != level) existingMaster.copy(appendLevel = level) else existingMaster
            else -> existingMaster
        }

        if (updatedMaster != existingMaster) {
            repository.updateSongMaster(updatedMaster)
        }
    }

    private fun isSuccessfulOcrResult(
        songName: String?,
        difficulty: String?,
        level: Int?,
        perfectCount: Int?,
        greatCount: Int?,
        goodCount: Int?,
        badCount: Int?,
        missCount: Int?,
    ): Boolean {
        return !songName.isNullOrBlank() &&
            !difficulty.isNullOrBlank() &&
            // We allow level to be null here so that unknown songs wait in UNREAD for user edits
            perfectCount != null &&
            greatCount != null &&
            goodCount != null &&
            badCount != null &&
            missCount != null
    }

    private suspend fun analyzeUnreadRecordsInParallel(
        context: Context,
        unreadRecords: List<ScoreRecord>,
        songMasters: List<SongMaster>,
    ): List<OcrAnalyzedRecord> = coroutineScope {
        if (unreadRecords.isEmpty()) {
            return@coroutineScope emptyList()
        }

        val workerCount = minOf(OCR_WORKER_COUNT, unreadRecords.size)
        val chunkSize = (unreadRecords.size + workerCount - 1) / workerCount

        unreadRecords
            .chunked(chunkSize)
            .map { chunk ->
                async(Dispatchers.Default) {
                    val ocrAnalyzer = ScoreOcrAnalyzer(context.applicationContext)
                    try {
                        chunk.map { record ->
                            analyzeSingleUnreadRecord(
                                record = record,
                                ocrAnalyzer = ocrAnalyzer,
                                songMasters = songMasters,
                            )
                        }
                    } finally {
                        ocrAnalyzer.close()
                    }
                }
            }
            .awaitAll()
            .flatten()
    }

    private suspend fun analyzeSingleUnreadRecord(
        record: ScoreRecord,
        ocrAnalyzer: ScoreOcrAnalyzer,
        songMasters: List<SongMaster>,
    ): OcrAnalyzedRecord {
        val ocrResult = ocrAnalyzer.analyze(Uri.parse(record.imageUri))
        val resolvedMatch = SongNameResolver.resolve(ocrResult, songMasters)
        val resolvedSongMaster = resolvedMatch.songMaster
        val resolvedLevel = ocrResult.level ?: resolvedSongMaster?.getLevelForDifficulty(ocrResult.difficulty)
        val parsedSongName = resolvedSongMaster?.songName ?: resolvedMatch.matchedText

        val isSuccess = isSuccessfulOcrResult(
            songName = parsedSongName,
            difficulty = ocrResult.difficulty,
            level = resolvedLevel,
            perfectCount = ocrResult.perfectCount,
            greatCount = ocrResult.greatCount,
            goodCount = ocrResult.goodCount,
            badCount = ocrResult.badCount,
            missCount = ocrResult.missCount,
        )

        val updatedRecord = record.copy(
            status = if (isSuccess) Constants.STATUS_UNREAD else Constants.STATUS_FAILED,
            isBestFrame = false,
            isReservedFrame = false,
            songName = parsedSongName,
            difficulty = ocrResult.difficulty,
            level = resolvedLevel,
            perfectCount = ocrResult.perfectCount,
            greatCount = ocrResult.greatCount,
            goodCount = ocrResult.goodCount,
            badCount = ocrResult.badCount,
            missCount = ocrResult.missCount,
            isAllPerfect = ocrResult.isAllPerfect,
        )

        return OcrAnalyzedRecord(
            record = updatedRecord,
            isSuccess = isSuccess,
        )
    }

    private fun SongMaster.getLevelForDifficulty(difficulty: String?): Int? {
        return when (difficulty) {
            "EASY" -> easyLevel
            "NORMAL" -> normalLevel
            "HARD" -> hardLevel
            "EXPERT" -> expertLevel
            "MASTER" -> masterLevel
            "APPEND" -> appendLevel
            else -> null
        }
    }

    private fun extractRecentFrameRecords(reservedRecords: List<ScoreRecord>): List<ScoreRecord> {
        val pickedCharts = linkedSetOf<String>()

        return reservedRecords
            .sortedWith(
                compareByDescending<ScoreRecord> { record -> record.singleRate ?: 0f }
                    .thenByDescending { record -> record.date ?: 0L }
                    .thenByDescending { record -> record.id },
            )
            .filter { record ->
                val chartKey = record.chartKey() ?: return@filter false
                pickedCharts.add(chartKey)
            }
            .take(Constants.RECENT_FRAME_SIZE)
    }

    private fun sumRateValues(records: List<ScoreRecord>): Float {
        return records.sumOf { record -> (record.singleRate ?: 0f).toDouble() }.toFloat()
    }

    private fun ScoreRecord.chartKey(): String? {
        val songName = songName?.trim()
        val difficulty = difficulty?.trim()
        if (songName.isNullOrBlank() || difficulty.isNullOrBlank()) {
            return null
        }

        return "$songName|$difficulty"
    }

    private data class OcrSummary(
        val processedCount: Int,
        val failedCount: Int,
    )

    private data class OcrAnalyzedRecord(
        val record: ScoreRecord,
        val isSuccess: Boolean,
    )

    private data class FolderImportSummary(
        val selectedUri: Uri,
        val insertedCount: Int,
        val relinkedCount: Int,
    )

    private data class RateCalculationSummary(
        val processedCount: Int,
        val skippedCount: Int,
    )

    private data class TrashDeletionSummary(
        val targetCount: Int,
        val deletedCount: Int,
        val failedCount: Int,
        val blockedCount: Int,
    )

    private fun isRecordProtectedForImageDeletion(record: ScoreRecord): Boolean {
        return record.isBestFrame || record.isReservedFrame
    }

    fun saveThemeColorAndOshi(color: Long, name: String) {
        preferences.edit()
            .putLong(KEY_THEME_COLOR, color)
            .putString(KEY_OSHI_NAME, name)
            .apply()
        _themeColor.value = color
        _oshiName.value = name
    }

    private companion object {
        const val OCR_WORKER_COUNT = 2
        const val PREFERENCES_NAME = "sekai_rating_settings"
        const val KEY_USER_NAME = "user_name"
        const val KEY_THEME_COLOR = "theme_color"
        const val KEY_OSHI_NAME = "oshi_name"
        const val KEY_SELECTED_FOLDER_URI = "selected_folder_uri"

        const val DEFAULT_USER_NAME = "セカイの住人"
        const val DEFAULT_OSHI_NAME = "ミク"
        const val DEFAULT_THEME_COLOR = 0xFF33CCBB
    }
}


