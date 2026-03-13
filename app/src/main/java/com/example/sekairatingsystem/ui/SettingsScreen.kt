package com.example.sekairatingsystem.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sekairatingsystem.Constants
import com.example.sekairatingsystem.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current

    val userName by viewModel.userName.collectAsState()
    val userNameMessage by viewModel.userNameMessage.collectAsState()
    val oshiName by viewModel.oshiName.collectAsState()
    val selectedFolderUri by viewModel.selectedFolderUri.collectAsState()
    val isScanningFolder by viewModel.isScanningFolder.collectAsState()
    val isDeletingTrash by viewModel.isDeletingTrash.collectAsState()
    val trashDeletionMessage by viewModel.trashDeletionMessage.collectAsState()
    val isResettingDatabase by viewModel.isResettingDatabase.collectAsState()
    val databaseResetMessage by viewModel.databaseResetMessage.collectAsState()
    val trashRecords by viewModel.getScoreRecordsByStatus(Constants.STATUS_TRASH)
        .collectAsState(initial = emptyList())

    var editableUserName by rememberSaveable(userName) { mutableStateOf(userName) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteBlockedDialog by remember { mutableStateOf(false) }
    var showDatabaseResetConfirmDialog by remember { mutableStateOf(false) }

    val deletableTrashCount = remember(trashRecords) {
        trashRecords.count { record ->
            record.imageUri.isNotBlank() && !record.isBestFrame && !record.isReservedFrame
        }
    }
    val protectedTrashCount = remember(trashRecords) {
        trashRecords.count { record ->
            record.imageUri.isNotBlank() && (record.isBestFrame || record.isReservedFrame)
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let { selectedUri ->
            viewModel.onFolderSelected(selectedUri, context)
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(text = stringResource(R.string.settings_delete_confirm_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.settings_delete_confirm_message,
                        deletableTrashCount,
                        protectedTrashCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteTrashFiles(context)
                    },
                ) {
                    Text(text = stringResource(R.string.settings_delete_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(text = stringResource(R.string.settings_delete_confirm_no))
                }
            },
        )
    }

    if (showDeleteBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteBlockedDialog = false },
            title = { Text(text = stringResource(R.string.settings_delete_blocked_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.settings_delete_blocked_message,
                        protectedTrashCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteBlockedDialog = false }) {
                    Text(text = stringResource(R.string.settings_delete_blocked_ok))
                }
            },
        )
    }

    if (showDatabaseResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDatabaseResetConfirmDialog = false },
            title = { Text(text = stringResource(R.string.settings_db_reset_confirm_title)) },
            text = { Text(text = stringResource(R.string.settings_db_reset_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatabaseResetConfirmDialog = false
                        viewModel.resetAllDatabaseData()
                    },
                ) {
                    Text(text = stringResource(R.string.settings_db_reset_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatabaseResetConfirmDialog = false }) {
                    Text(text = stringResource(R.string.settings_db_reset_confirm_no))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_user_name_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        OutlinedTextField(
                            value = editableUserName,
                            onValueChange = { editableUserName = it },
                            singleLine = true,
                            label = { Text(text = stringResource(R.string.settings_user_name_label)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { viewModel.saveUserName(editableUserName) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.settings_user_name_btn))
                        }
                        if (!userNameMessage.isNullOrBlank()) {
                            Text(
                                text = userNameMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_folder_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(
                                R.string.settings_current_folder,
                                selectedFolderUri?.toString() ?: stringResource(R.string.settings_folder_not_selected),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = { folderPickerLauncher.launch(null) },
                            enabled = !isScanningFolder,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.settings_folder_btn))
                        }
                        if (isScanningFolder) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                    }
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_trash_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.settings_trash_desc),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (protectedTrashCount > 0) {
                            Text(
                                text = stringResource(
                                    R.string.settings_delete_blocked_message,
                                    protectedTrashCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(
                            onClick = {
                                if (deletableTrashCount == 0 && protectedTrashCount > 0) {
                                    showDeleteBlockedDialog = true
                                } else {
                                    showDeleteConfirmDialog = true
                                }
                            },
                            enabled = !isDeletingTrash && (deletableTrashCount > 0 || protectedTrashCount > 0),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Text(
                                text = if (isDeletingTrash) {
                                    stringResource(R.string.settings_trash_deleting)
                                } else {
                                    stringResource(R.string.settings_trash_btn, deletableTrashCount)
                                },
                            )
                        }

                        if (!trashDeletionMessage.isNullOrBlank()) {
                            Text(
                                text = trashDeletionMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_db_reset_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.settings_db_reset_desc),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = { showDatabaseResetConfirmDialog = true },
                            enabled = !isResettingDatabase,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Text(
                                text = if (isResettingDatabase) {
                                    stringResource(R.string.settings_db_reset_running)
                                } else {
                                    stringResource(R.string.settings_db_reset_btn)
                                },
                            )
                        }

                        if (!databaseResetMessage.isNullOrBlank()) {
                            Text(
                                text = databaseResetMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(id = R.string.settings_oshi_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(id = R.string.settings_oshi_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_current_oshi, oshiName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                OSHI_UNITS.forEach { (unitName, characters) ->
                    Text(
                        text = unitName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = characters,
                            key = { character -> character.name },
                        ) { oshi ->
                            val isSelected = oshiName == oshi.name
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        viewModel.saveThemeColorAndOshi(oshi.color, oshi.name)
                                    }
                                    .padding(4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(oshi.color))
                                        .border(
                                            width = if (isSelected) 4.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            shape = CircleShape,
                                        ),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = oshi.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}

data class OshiChar(val name: String, val color: Long)

private val OSHI_UNITS: List<Pair<String, List<OshiChar>>> = listOf(
    "VIRTUAL SINGER" to listOf(
        OshiChar("ミク", 0xFF39C5BB),
        OshiChar("リン", 0xFFF7D300),
        OshiChar("レン", 0xFFF2C94C),
        OshiChar("ルカ", 0xFFFF8FB1),
        OshiChar("MEIKO", 0xFFD95A5A),
        OshiChar("KAITO", 0xFF4A90E2),
    ),
    "Leo/need" to listOf(
        OshiChar("一歌", 0xFF4F7DFF),
        OshiChar("咲希", 0xFFFFD24D),
        OshiChar("穂波", 0xFFFFA35B),
        OshiChar("志歩", 0xFF6CC36C),
    ),
    "MORE MORE JUMP!" to listOf(
        OshiChar("みのり", 0xFFFF8FA3),
        OshiChar("遥", 0xFF7DB8FF),
        OshiChar("愛莉", 0xFFFF9ECF),
        OshiChar("雫", 0xFF98D8D2),
    ),
    "Vivid BAD SQUAD" to listOf(
        OshiChar("こはね", 0xFFFFA347),
        OshiChar("杏", 0xFFFF5A83),
        OshiChar("彰人", 0xFFE1B12C),
        OshiChar("冬弥", 0xFF3F88C5),
    ),
    "ワンダーランズxショウタイム" to listOf(
        OshiChar("司", 0xFFFFC857),
        OshiChar("えむ", 0xFFFF8CCF),
        OshiChar("寧々", 0xFF6FD6A8),
        OshiChar("類", 0xFF8D6BE8),
    ),
    "25時、ナイトコードで。" to listOf(
        OshiChar("奏", 0xFFAAB4FF),
        OshiChar("まふゆ", 0xFF9FA8DA),
        OshiChar("絵名", 0xFFF0A6FF),
        OshiChar("瑞希", 0xFFFFA8D1),
    ),
)
