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

enum class RateMode {
    ALL,
    MASTER_ONLY,
    APPEND_ONLY,
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppRepository(AppDatabase.getDatabase(application).appDao())
    private val masterDataInitializer = MasterDataInitializer(application.applicationContext, repository)
    private val preferences = application.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _userName = MutableStateFlow(preferences.getString(KEY_USER_NAME, DEFAULT_USER_NAME) ?: DEFAULT_USER_NAME)
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _themeMode = MutableStateFlow(
        ThemeMode.fromPreference(preferences.getString(KEY_THEME_MODE, DEFAULT_THEME_MODE)),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _rateMode = MutableStateFlow(RateMode.ALL)
    val rateMode: StateFlow<RateMode> = _rateMode.asStateFlow()

    private val _oshiName = MutableStateFlow(preferences.getString(KEY_OSHI_NAME, DEFAULT_OSHI_NAME) ?: DEFAULT_OSHI_NAME)
    val oshiName: StateFlow<String> = _oshiName.asStateFlow()

    val calculatedCount: StateFlow<Int> = repository.getCalculatedRecords().map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalPlayCount: StateFlow<Int> = repository.getTotalScoreRecordsCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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

    private val rateModeRecords: StateFlow<List<ScoreRecord>> = combine(
        repository.getCalculatedRecords(),
        rateMode,
    ) { records, mode ->
        records.filter { record -> record.matchesRateMode(mode) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val bestRateRecordsByMode: StateFlow<List<ScoreRecord>> = rateModeRecords
        .map(::extractBestFrameRecords)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val usedListBestRecordsByMode: StateFlow<List<ScoreRecord>> = bestRateRecordsByMode

    private val recentRateRecordsByMode: StateFlow<List<ScoreRecord>> = rateModeRecords
        .map(::extractRecentFrameRecords)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val usedListRecentRecordsByMode: StateFlow<List<ScoreRecord>> = recentRateRecordsByMode

    val bestRateAverage: StateFlow<Float> = bestRateRecordsByMode
        .map { records -> fixedFrameAverage(records, Constants.BEST_FRAME_SIZE) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0f,
        )

    val recentRateAverage: StateFlow<Float> = recentRateRecordsByMode
        .map { records -> fixedFrameAverage(records, Constants.RECENT_FRAME_SIZE) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0f,
        )

    val totalRate: StateFlow<Float> = combine(
        bestRateAverage,
        recentRateAverage,
    ) { bestAverage, recentAverage ->
        (bestAverage + recentAverage) / 2f
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

    private val _unknownSongNotice = MutableStateFlow<UnknownSongNotice?>(null)
    val unknownSongNotice: StateFlow<UnknownSongNotice?> = _unknownSongNotice.asStateFlow()

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

    private val _levelMismatchPrompt = MutableStateFlow<LevelMismatchPrompt?>(null)
    val levelMismatchPrompt: StateFlow<LevelMismatchPrompt?> = _levelMismatchPrompt.asStateFlow()

    private val _isDeletingTrash = MutableStateFlow(false)
    val isDeletingTrash: StateFlow<Boolean> = _isDeletingTrash.asStateFlow()

    private val _trashDeletionMessage = MutableStateFlow<String?>(null)
    val trashDeletionMessage: StateFlow<String?> = _trashDeletionMessage.asStateFlow()

    private val _isResettingDatabase = MutableStateFlow(false)
    val isResettingDatabase: StateFlow<Boolean> = _isResettingDatabase.asStateFlow()

    private val _databaseResetMessage = MutableStateFlow<String?>(null)
    val databaseResetMessage: StateFlow<String?> = _databaseResetMessage.asStateFlow()

    private var pendingEditedSave: PendingEditedSave? = null

    fun toggleRateMode() {
        _rateMode.value = when (_rateMode.value) {
            RateMode.ALL -> RateMode.MASTER_ONLY
            RateMode.MASTER_ONLY -> RateMode.APPEND_ONLY
            RateMode.APPEND_ONLY -> RateMode.ALL
        }
    }

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

    private fun ensurePersistedUriPermission(context: Context, uri: Uri) {
        val resolver = context.contentResolver
        val hasPermission = resolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isReadPermission
        }
        if (!hasPermission) {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    private suspend fun scanFolderForNewImages(
        context: Context,
        uri: Uri,
    ): FolderImportSummary {
        ensurePersistedUriPermission(context, uri)

        return withContext(Dispatchers.IO) {
            val imageFiles = ImageFileScanner.getImageFiles(context, uri)

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
        }
    }

    private fun formatFolderScanMessage(summary: FolderImportSummary): String {
        return when {
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
    }

    fun onFolderSelected(uri: Uri, context: Context) {
        if (_isScanningFolder.value || _isAnalyzingOcr.value || _isCalculatingRates.value) {
            return
        }

        viewModelScope.launch {
            _isScanningFolder.value = true

            runCatching {
                scanFolderForNewImages(context, uri)
            }.onSuccess { summary ->
                _selectedFolderUri.value = summary.selectedUri
                _importedImageCount.value = summary.insertedCount
                preferences.edit().putString(KEY_SELECTED_FOLDER_URI, summary.selectedUri.toString()).apply()
                _folderScanMessage.value = formatFolderScanMessage(summary)
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
            val selectedUri = _selectedFolderUri.value
            if (selectedUri != null) {
                _isScanningFolder.value = true
                val scanResult = runCatching { scanFolderForNewImages(context, selectedUri) }
                _isScanningFolder.value = false

                scanResult.onSuccess { summary ->
                    _importedImageCount.value = summary.insertedCount
                    _folderScanMessage.value = formatFolderScanMessage(summary)
                }.onFailure { throwable ->
                    _folderScanMessage.value = throwable.message ?: "フォルダの読み込みに失敗しました"
                    return@launch
                }
            }

            val chartReferences = withContext(Dispatchers.IO) {
                buildChartReferenceMap(
                    repository.getScoreRecordsByStatusSnapshot(Constants.STATUS_CALCULATED),
                )
            }

            // --- Phase 1: OCR ---
            _isAnalyzingOcr.value = true
            _ocrProcessedCount.value = 0
            _ocrFailedCount.value = 0
            _ocrStatusMessage.value = "未読み取り画像のOCR解析を開始します..."
            _unknownSongNotice.value = null

            var ocrSucceeded = false
            runCatching {
                val unreadRecords = repository.getScoreRecordsByStatusSnapshot(Constants.STATUS_UNREAD)
                    .filter(::needsOcrAnalysis)
                if (unreadRecords.isEmpty()) {
                    return@runCatching OcrSummary(
                        processedCount = 0,
                        failedCount = 0,
                        unknownCount = 0,
                        unknownSongNames = emptyList(),
                    )
                }

                val songMasters = repository.getAllSongMastersSnapshot()
                val analyzedResults = analyzeUnreadRecordsInParallel(
                    context = context,
                    unreadRecords = unreadRecords,
                    songMasters = songMasters,
                )

                val adjustedResults = analyzedResults.map { analyzed ->
                    if (!analyzed.isSuccess) {
                        analyzed
                    } else {
                        val checkedRecord = applyChartConsistencyCheck(analyzed.record, chartReferences)
                        analyzed.copy(
                            record = checkedRecord,
                            isSuccess = checkedRecord.status != Constants.STATUS_TRASH,
                        )
                    }
                }

                if (adjustedResults.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        repository.updateScoreRecords(adjustedResults.map { analyzed -> analyzed.record })
                    }
                }

                val processedCount = adjustedResults.size
                val failedCount = adjustedResults.count { analyzed -> !analyzed.isSuccess }
                val unknownCount = adjustedResults.count { analyzed -> analyzed.unknownSongName != null }
                val unknownSongNames = adjustedResults
                    .mapNotNull { analyzed -> analyzed.unknownSongName?.trim()?.takeIf(String::isNotBlank) }
                    .filterNot { songName -> songName.equals(UNKNOWN_SONG_PLACEHOLDER, ignoreCase = true) }
                    .distinct()
                    .take(UNKNOWN_SONG_NOTICE_NAME_LIMIT)

                _ocrProcessedCount.value = processedCount
                _ocrFailedCount.value = failedCount

                OcrSummary(
                    processedCount = processedCount,
                    failedCount = failedCount,
                    unknownCount = unknownCount,
                    unknownSongNames = unknownSongNames,
                )
            }.onSuccess { summary ->
                ocrSucceeded = true
                if (summary.unknownCount > 0) {
                    _unknownSongNotice.value = UnknownSongNotice(
                        count = summary.unknownCount,
                        songNames = summary.unknownSongNames,
                    )
                }

                _ocrStatusMessage.value = when {
                    summary.processedCount == 0 -> {
                        "OCR対象の未読み取り画像がありませんでした"
                    }
                    summary.unknownCount > 0 -> {
                        "OCR解析が完了しました。成功: ${summary.processedCount - summary.failedCount} 件 / 失敗: ${summary.failedCount} 件 / 未登録候補: ${summary.unknownCount} 件"
                    }
                    else -> {
                        "OCR解析が完了しました。成功: ${summary.processedCount - summary.failedCount} 件 / 失敗: ${summary.failedCount} 件"
                    }
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
                    val checkedRecords = calculableRecords.map { record ->
                        applyChartConsistencyCheck(record, chartReferences)
                    }
                    val trashedRecords = checkedRecords.filter { record -> record.status == Constants.STATUS_TRASH }
                    if (trashedRecords.isNotEmpty()) {
                        repository.updateScoreRecords(trashedRecords)
                    }

                    val validRecords = checkedRecords.filter { record -> record.status != Constants.STATUS_TRASH }
                    val skippedCount = unreadRecords.size - validRecords.size

                    if (validRecords.isEmpty()) {
                        return@withContext RateCalculationSummary(0, skippedCount)
                    }

                    val ratedRecords = validRecords.map { record ->
                        record.copy(singleRate = RatingCalculator.calculateSingleRate(record))
                    }

                    RatingCalculator.updateFramesInBatch(repository, ratedRecords)

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

    fun dismissUnknownSongNotice() {
        _unknownSongNotice.value = null
    }

    fun chooseLevelMismatchReferenceLevel() {
        resolveLevelMismatch(useReferenceLevel = true)
    }

    fun chooseLevelMismatchEditedLevel() {
        resolveLevelMismatch(useReferenceLevel = false)
    }

    fun cancelLevelMismatchPrompt() {
        pendingEditedSave = null
        _levelMismatchPrompt.value = null
    }

    fun saveProfileSettings(
        userName: String,
        oshiName: String,
        themeMode: ThemeMode,
    ) {
        val normalizedUserName = userName.trim()

        preferences.edit()
            .putString(KEY_USER_NAME, normalizedUserName)
            .putString(KEY_OSHI_NAME, oshiName)
            .putString(KEY_THEME_MODE, themeMode.name)
            .apply()

        _userName.value = normalizedUserName
        _oshiName.value = oshiName
        _themeMode.value = themeMode
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

                val mismatchPrompt = findLevelMismatchPrompt(normalizedRecord)
                if (mismatchPrompt != null) {
                    pendingEditedSave = PendingEditedSave(
                        record = normalizedRecord,
                        onSaved = onSaved,
                        mismatchPrompt = mismatchPrompt,
                    )
                    _levelMismatchPrompt.value = mismatchPrompt
                    return@runCatching SaveEditedRecordOutcome.WAITING_LEVEL_SELECTION
                }

                persistEditedRecordAndRecalculate(
                    normalizedRecord = normalizedRecord,
                    alignChartLevelToEditedValue = false,
                )

                SaveEditedRecordOutcome.SAVED
            }.onSuccess { outcome ->
                when (outcome) {
                    SaveEditedRecordOutcome.SAVED -> {
                        pendingEditedSave = null
                        _levelMismatchPrompt.value = null
                        _manualEditMessage.value = null
                        onSaved()
                    }

                    SaveEditedRecordOutcome.WAITING_LEVEL_SELECTION -> {
                        // no-op: waits for dialog selection from the UI
                    }
                }
            }.onFailure { throwable ->
                _manualEditMessage.value = throwable.message ?: "保存に失敗しました"
            }

            _isSavingEditedRecord.value = false
        }
    }

    private fun resolveLevelMismatch(useReferenceLevel: Boolean) {
        val pendingSave = pendingEditedSave ?: return
        if (_isSavingEditedRecord.value || _isDeletingEditedImage.value) {
            return
        }

        viewModelScope.launch {
            _isSavingEditedRecord.value = true
            _manualEditMessage.value = null

            runCatching {
                val selectedLevel = if (useReferenceLevel) {
                    pendingSave.mismatchPrompt.referenceLevel
                } else {
                    pendingSave.mismatchPrompt.inputLevel
                }

                val resolvedRecord = pendingSave.record.copy(level = selectedLevel)
                persistEditedRecordAndRecalculate(
                    normalizedRecord = resolvedRecord,
                    alignChartLevelToEditedValue = !useReferenceLevel,
                )
            }.onSuccess {
                pendingEditedSave = null
                _levelMismatchPrompt.value = null
                _manualEditMessage.value = null
                pendingSave.onSaved()
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

                val protectedRecordIds = getProtectedRecordIdsAcrossRateModes()

                if (isRecordProtectedForImageDeletion(targetRecord, protectedRecordIds)) {
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
                    val protectedRecordIds = getProtectedRecordIdsAcrossRateModes()

                    for (record in trashRecords) {
                        if (isRecordProtectedForImageDeletion(record, protectedRecordIds)) {
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

    private fun ScoreRecord.totalNotes(): Int? {
        val perfect = perfectCount ?: return null
        val great = greatCount ?: return null
        val good = goodCount ?: return null
        val bad = badCount ?: return null
        val miss = missCount ?: return null
        return perfect + great + good + bad + miss
    }

    private fun buildChartReferenceMap(
        records: List<ScoreRecord>,
        excludedRecordId: Long? = null,
    ): Map<String, ChartReference> {
        val sortedRecords = records
            .asSequence()
            .filter { record -> excludedRecordId == null || record.id != excludedRecordId }
            .filter { record ->
                record.status == Constants.STATUS_CALCULATED || record.isBestFrame || record.isReservedFrame
            }
            .sortedWith(
                compareByDescending<ScoreRecord> { record -> record.date ?: 0L }
                    .thenByDescending { record -> record.id },
            )

        val references = LinkedHashMap<String, ChartReference>()
        for (record in sortedRecords) {
            val chartKey = record.chartKey() ?: continue
            if (references.containsKey(chartKey)) {
                continue
            }
            val level = record.level ?: continue
            val totalNotes = record.totalNotes() ?: continue
            references[chartKey] = ChartReference(level = level, totalNotes = totalNotes)
        }

        return references
    }

    private fun applyChartConsistencyCheck(
        record: ScoreRecord,
        references: Map<String, ChartReference>,
    ): ScoreRecord {
        val chartKey = record.chartKey() ?: return record
        val reference = references[chartKey] ?: return record
        val totalNotes = record.totalNotes() ?: return record
        val level = record.level ?: return record

        return if (reference.totalNotes != totalNotes || reference.level != level) {
            record.copy(
                status = Constants.STATUS_TRASH,
                isBestFrame = false,
                isReservedFrame = false,
                singleRate = null,
            )
        } else {
            record
        }
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

    private suspend fun persistEditedRecordAndRecalculate(
        normalizedRecord: ScoreRecord,
        alignChartLevelToEditedValue: Boolean,
    ) {
        if (alignChartLevelToEditedValue) {
            applyEditedLevelToExistingChartRecords(
                songName = normalizedRecord.songName,
                difficulty = normalizedRecord.difficulty,
                level = normalizedRecord.level,
            )
        }

        upsertSongMasterFromEditedRecord(
            songName = normalizedRecord.songName,
            difficulty = normalizedRecord.difficulty,
            level = normalizedRecord.level,
        )

        if (!isReadyForRateCalculation(normalizedRecord)) {
            throw IllegalArgumentException("レート再計算に必要な項目が不足しています")
        }

        val chartReferences = buildChartReferenceMap(
            repository.getScoreRecordsByStatusSnapshot(Constants.STATUS_CALCULATED),
            excludedRecordId = normalizedRecord.id,
        )
        val checkedRecord = applyChartConsistencyCheck(normalizedRecord, chartReferences)
        if (checkedRecord.status == Constants.STATUS_TRASH) {
            repository.updateScoreRecord(
                checkedRecord.copy(
                    singleRate = null,
                    isBestFrame = false,
                    isReservedFrame = false,
                ),
            )
            throw IllegalStateException("譜面の総ノーツ数またはレベルが過去データと一致しないため、TRASHに移動しました")
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
    }

    private suspend fun findLevelMismatchPrompt(record: ScoreRecord): LevelMismatchPrompt? {
        val songName = record.songName ?: return null
        val difficulty = record.difficulty ?: return null
        val inputLevel = record.level ?: return null

        val masterLevel = repository.getSongMasterByName(songName)?.getLevelForDifficulty(difficulty)
        if (masterLevel != null && masterLevel != inputLevel) {
            return LevelMismatchPrompt(
                songName = songName,
                difficulty = difficulty,
                inputLevel = inputLevel,
                referenceLevel = masterLevel,
                referenceSourceLabel = LEVEL_REFERENCE_MASTER,
            )
        }

        val historyLevel = repository.getLatestLevelForChartExcludingRecord(
            songName = songName,
            difficulty = difficulty,
            excludedRecordId = record.id,
        )
        if (historyLevel != null && historyLevel != inputLevel) {
            return LevelMismatchPrompt(
                songName = songName,
                difficulty = difficulty,
                inputLevel = inputLevel,
                referenceLevel = historyLevel,
                referenceSourceLabel = LEVEL_REFERENCE_HISTORY,
            )
        }

        return null
    }

    private suspend fun applyEditedLevelToExistingChartRecords(
        songName: String?,
        difficulty: String?,
        level: Int?,
    ) {
        if (songName.isNullOrBlank() || difficulty.isNullOrBlank() || level == null) {
            return
        }

        repository.updateLevelsForChart(
            songName = songName,
            difficulty = difficulty,
            targetLevel = level,
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
            // Level can be null only for known songs that still need manual level correction.
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
        val parsedSongName = resolvedSongMaster?.songName ?: resolvedMatch.matchedText.takeIf(String::isNotBlank)
        val isUnknownSong = resolvedSongMaster == null

        val isSuccess = !isUnknownSong && isSuccessfulOcrResult(
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
            unknownSongName = if (isUnknownSong) parsedSongName else null,
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
        val bestByChart = reservedRecords
            .mapNotNull { record -> record.chartKey()?.let { chartKey -> chartKey to record } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (_, chartRecords) ->
                chartRecords.maxWithOrNull(
                    compareBy<ScoreRecord> { record -> record.singleRate ?: 0f }
                        .thenBy { record -> record.date ?: Long.MIN_VALUE }
                        .thenBy { record -> record.id },
                )
            }

        return bestByChart
            .sortedWith(
                compareByDescending<ScoreRecord> { record -> record.singleRate ?: 0f }
                    .thenByDescending { record -> record.level ?: 0 }
                    .thenByDescending { record -> record.date ?: Long.MIN_VALUE }
                    .thenByDescending { record -> record.id },
            )
            .take(Constants.RECENT_FRAME_SIZE)
    }

    private fun extractBestFrameRecords(records: List<ScoreRecord>): List<ScoreRecord> {
        val bestByChart = records
            .mapNotNull { record -> record.chartKey()?.let { chartKey -> chartKey to record } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (_, chartRecords) ->
                chartRecords.maxWithOrNull(
                    compareBy<ScoreRecord> { record -> record.singleRate ?: 0f }
                        .thenBy { record -> record.level ?: 0 }
                        .thenBy { record -> record.id },
                )
            }

        return bestByChart
            .sortedWith(
                compareByDescending<ScoreRecord> { record -> record.singleRate ?: 0f }
                    .thenByDescending { record -> record.level ?: 0 }
                    .thenBy { record -> record.id },
            )
            .take(Constants.BEST_FRAME_SIZE)
    }

    private fun fixedFrameAverage(records: List<ScoreRecord>, frameSize: Int): Float {
        if (frameSize <= 0 || records.isEmpty()) {
            return 0f
        }
        return sumRateValues(records) / frameSize
    }

    private fun ScoreRecord.matchesRateMode(mode: RateMode): Boolean {
        val normalizedDifficulty = difficulty?.trim()?.uppercase(Locale.ROOT)
        val levelValue = level ?: 0
        val isAppendChart = normalizedDifficulty == "APPEND" ||
            (normalizedDifficulty == "MASTER" && levelValue >= 37)

        return when (mode) {
            RateMode.ALL -> true
            RateMode.MASTER_ONLY -> !isAppendChart
            RateMode.APPEND_ONLY -> isAppendChart
        }
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

    private data class ChartReference(
        val level: Int,
        val totalNotes: Int,
    )

    private data class OcrSummary(
        val processedCount: Int,
        val failedCount: Int,
        val unknownCount: Int,
        val unknownSongNames: List<String>,
    )

    private data class OcrAnalyzedRecord(
        val record: ScoreRecord,
        val isSuccess: Boolean,
        val unknownSongName: String?,
    )

    data class UnknownSongNotice(
        val count: Int,
        val songNames: List<String>,
    )

    data class LevelMismatchPrompt(
        val songName: String,
        val difficulty: String,
        val inputLevel: Int,
        val referenceLevel: Int,
        val referenceSourceLabel: String,
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

    private data class PendingEditedSave(
        val record: ScoreRecord,
        val onSaved: () -> Unit,
        val mismatchPrompt: LevelMismatchPrompt,
    )

    private enum class SaveEditedRecordOutcome {
        SAVED,
        WAITING_LEVEL_SELECTION,
    }

    private suspend fun getProtectedRecordIdsAcrossRateModes(): Set<Long> {
        val activeRecords = repository.getAllScoreRecordsSnapshot().filter { record ->
            record.status == Constants.STATUS_CALCULATED || record.isBestFrame || record.isReservedFrame
        }

        if (activeRecords.isEmpty()) {
            return emptySet()
        }

        val protectedRecordIds = mutableSetOf<Long>()
        for (mode in RateMode.entries) {
            val modeRecords = activeRecords.filter { record -> record.matchesRateMode(mode) }
            extractBestFrameRecords(modeRecords).forEach { record -> protectedRecordIds += record.id }
            extractRecentFrameRecords(modeRecords).forEach { record -> protectedRecordIds += record.id }
        }

        return protectedRecordIds
    }

    private fun isRecordProtectedForImageDeletion(
        record: ScoreRecord,
        protectedRecordIds: Set<Long>,
    ): Boolean {
        return record.id in protectedRecordIds
    }

    private companion object {
        const val OCR_WORKER_COUNT = 2
        const val UNKNOWN_SONG_NOTICE_NAME_LIMIT = 5
        const val PREFERENCES_NAME = "sekai_rating_settings"
        const val KEY_USER_NAME = "user_name"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_OSHI_NAME = "oshi_name"
        const val KEY_SELECTED_FOLDER_URI = "selected_folder_uri"

        const val UNKNOWN_SONG_PLACEHOLDER = "Unknown Song"
        const val LEVEL_REFERENCE_MASTER = "マスターデータ"
        const val LEVEL_REFERENCE_HISTORY = "過去データ"

        const val DEFAULT_USER_NAME = "セカイの住人"
        const val DEFAULT_OSHI_NAME = "ミク"
        const val DEFAULT_THEME_MODE = "SYSTEM"
    }
}


