package com.example.sekairatingsystem.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.sekairatingsystem.R
import com.example.sekairatingsystem.data.entity.ScoreRecord
import com.example.sekairatingsystem.ui.theme.LocalOshiColor
import com.example.sekairatingsystem.ui.theme.LocalOshiOnColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScoreScreen(
    recordId: Long,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val recordFlow = remember(recordId) {
        viewModel.getScoreRecord(recordId)
    }
    val record by recordFlow.collectAsState(initial = null)
    val isSaving by viewModel.isSavingEditedRecord.collectAsState()
    val isDeletingImage by viewModel.isDeletingEditedImage.collectAsState()
    val manualEditMessage by viewModel.manualEditMessage.collectAsState()
    val levelMismatchPrompt by viewModel.levelMismatchPrompt.collectAsState()
    val recalcToastMessage = stringResource(R.string.edit_score_recalculate_done)
    val isOperationInProgress = isSaving || isDeletingImage
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showCannotDeleteDialog by remember { mutableStateOf(false) }
    val themeColor = LocalOshiColor.current
    val themeOnColor = LocalOshiOnColor.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "スコア手動編集") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "前の画面に戻る",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeColor,
                    titleContentColor = themeOnColor,
                    navigationIconContentColor = themeOnColor,
                    actionIconContentColor = themeOnColor,
                ),
            )
        },
    ) { innerPadding ->
        val currentRecord = record
        if (currentRecord == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            }
            return@Scaffold
        }

        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text(text = stringResource(R.string.edit_score_delete_confirm_title)) },
                text = { Text(text = stringResource(R.string.edit_score_delete_confirm_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            viewModel.deleteEditedRecordImage(
                                recordId = currentRecord.id,
                                context = context,
                                onDeleted = onNavigateBack,
                            )
                        },
                    ) {
                        Text(text = stringResource(R.string.edit_score_delete_confirm_yes))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text(text = stringResource(R.string.edit_score_delete_confirm_no))
                    }
                },
            )
        }

        if (showCannotDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showCannotDeleteDialog = false },
                title = { Text(text = stringResource(R.string.edit_score_delete_blocked_title)) },
                text = { Text(text = stringResource(R.string.edit_score_delete_blocked_message)) },
                confirmButton = {
                    TextButton(onClick = { showCannotDeleteDialog = false }) {
                        Text(text = stringResource(R.string.edit_score_delete_blocked_ok))
                    }
                },
            )
        }

        levelMismatchPrompt?.let { prompt ->
            AlertDialog(
                onDismissRequest = {
                    if (!isOperationInProgress) {
                        viewModel.cancelLevelMismatchPrompt()
                    }
                },
                title = { Text(text = stringResource(R.string.edit_score_level_mismatch_title)) },
                text = {
                    Text(
                        text = stringResource(
                            R.string.edit_score_level_mismatch_message,
                            prompt.songName,
                            prompt.difficulty,
                            prompt.inputLevel,
                            prompt.referenceLevel,
                            prompt.referenceSourceLabel,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !isOperationInProgress,
                        onClick = { viewModel.chooseLevelMismatchReferenceLevel() },
                    ) {
                        Text(
                            text = stringResource(
                                R.string.edit_score_level_mismatch_use_reference,
                                prompt.referenceLevel,
                            ),
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isOperationInProgress,
                        onClick = { viewModel.chooseLevelMismatchEditedLevel() },
                    ) {
                        Text(
                            text = stringResource(
                                R.string.edit_score_level_mismatch_use_input,
                                prompt.inputLevel,
                            ),
                        )
                    }
                },
            )
        }

        var songName by remember(currentRecord) { mutableStateOf(currentRecord.songName.orEmpty()) }
        var difficulty by remember(currentRecord) { mutableStateOf(currentRecord.difficulty.orEmpty()) }
        var level by remember(currentRecord) { mutableStateOf(currentRecord.level?.toString().orEmpty()) }
        var perfectCount by remember(currentRecord) { mutableStateOf(currentRecord.perfectCount?.toString().orEmpty()) }
        var greatCount by remember(currentRecord) { mutableStateOf(currentRecord.greatCount?.toString().orEmpty()) }
        var goodCount by remember(currentRecord) { mutableStateOf(currentRecord.goodCount?.toString().orEmpty()) }
        var badCount by remember(currentRecord) { mutableStateOf(currentRecord.badCount?.toString().orEmpty()) }
        var missCount by remember(currentRecord) { mutableStateOf(currentRecord.missCount?.toString().orEmpty()) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (currentRecord.imageUri.isNotBlank()) {
                AsyncImage(
                    model = Uri.parse(currentRecord.imageUri),
                    contentDescription = stringResource(R.string.edit_score_image_preview),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentScale = ContentScale.Crop,
                )
            }

            Text(
                text = if (currentRecord.imageUri.isBlank()) {
                    stringResource(R.string.edit_score_image_deleted)
                } else {
                    stringResource(
                        R.string.edit_score_image_name,
                        currentRecord.imageUri.substringAfterLast('/'),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedTextField(
                value = formatScoreRecordDate(currentRecord.date),
                onValueChange = {},
                label = { Text(text = stringResource(R.string.edit_score_image_date)) },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (manualEditMessage != null) {
                Text(
                    text = manualEditMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            OutlinedTextField(
                value = songName,
                onValueChange = { songName = it },
                label = { Text(text = stringResource(R.string.edit_score_song_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = difficulty,
                onValueChange = { difficulty = it },
                label = { Text(text = stringResource(R.string.edit_score_difficulty)) },
                supportingText = { Text(text = stringResource(R.string.edit_score_difficulty_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            NumericField(
                value = level,
                onValueChange = { level = it },
                label = stringResource(R.string.edit_score_level),
            )
            NumericField(value = perfectCount, onValueChange = { perfectCount = it }, label = "PERFECT")
            NumericField(value = greatCount, onValueChange = { greatCount = it }, label = "GREAT")
            NumericField(value = goodCount, onValueChange = { goodCount = it }, label = "GOOD")
            NumericField(value = badCount, onValueChange = { badCount = it }, label = "BAD")
            NumericField(value = missCount, onValueChange = { missCount = it }, label = "MISS")

            Button(
                onClick = {
                    val normalizedPerfect = perfectCount.toNullableInt()
                    val normalizedGreat = greatCount.toNullableInt()
                    val normalizedGood = goodCount.toNullableInt()
                    val normalizedBad = badCount.toNullableInt()
                    val normalizedMiss = missCount.toNullableInt()
                    val hasAllCounts = listOf(
                        normalizedPerfect,
                        normalizedGreat,
                        normalizedGood,
                        normalizedBad,
                        normalizedMiss,
                    ).all { value -> value != null }
                    val resolvedIsAllPerfect = if (hasAllCounts) {
                        normalizedGreat == 0 && normalizedGood == 0 && normalizedBad == 0 && normalizedMiss == 0
                    } else {
                        currentRecord.isAllPerfect
                    }
                    viewModel.saveEditedScoreRecord(
                        record = currentRecord.copy(
                            songName = songName.toNullableText(),
                            difficulty = difficulty.toNullableText(),
                            level = level.toNullableInt(),
                            perfectCount = normalizedPerfect,
                            greatCount = normalizedGreat,
                            goodCount = normalizedGood,
                            badCount = normalizedBad,
                            missCount = normalizedMiss,
                            isAllPerfect = resolvedIsAllPerfect,
                        ),
                        onSaved = {
                            Toast.makeText(context, recalcToastMessage, Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        },
                    )
                },
                enabled = !isOperationInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (isSaving) {
                        stringResource(R.string.edit_score_save_recalculate_loading)
                    } else {
                        stringResource(R.string.edit_score_save_recalculate)
                    },
                )
            }

            Button(
                onClick = {
                    if (currentRecord.isBestFrame || currentRecord.isReservedFrame) {
                        showCannotDeleteDialog = true
                    } else {
                        showDeleteConfirmDialog = true
                    }
                },
                enabled = !isOperationInProgress,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(
                    text = if (isDeletingImage) {
                        stringResource(R.string.edit_score_delete_image_loading)
                    } else {
                        stringResource(R.string.edit_score_delete_image)
                    },
                )
            }
        }
    }
}

private fun formatScoreRecordDate(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) {
        return "不明"
    }

    return DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
        .format(
            Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime(),
        )
}

@Composable
private fun NumericField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            onValueChange(newValue.filter { character -> character.isDigit() })
        },
        label = { Text(text = label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun String.toNullableText(): String? = trim().takeIf(String::isNotBlank)

private fun String.toNullableInt(): Int? = trim().takeIf(String::isNotBlank)?.toIntOrNull()