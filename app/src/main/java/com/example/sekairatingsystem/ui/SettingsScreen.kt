package com.example.sekairatingsystem.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sekairatingsystem.Constants
import com.example.sekairatingsystem.R
import com.example.sekairatingsystem.ui.theme.LocalIsDarkTheme
import com.example.sekairatingsystem.ui.theme.LocalOshiColor
import com.example.sekairatingsystem.ui.theme.LocalOshiOnColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current

    val userName by viewModel.userName.collectAsState()
    val oshiName by viewModel.oshiName.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val selectedFolderUri by viewModel.selectedFolderUri.collectAsState()
    val isScanningFolder by viewModel.isScanningFolder.collectAsState()
    val isDeletingTrash by viewModel.isDeletingTrash.collectAsState()
    val trashDeletionMessage by viewModel.trashDeletionMessage.collectAsState()
    val isResettingDatabase by viewModel.isResettingDatabase.collectAsState()
    val databaseResetMessage by viewModel.databaseResetMessage.collectAsState()
    val trashRecords by viewModel.getScoreRecordsByStatus(Constants.STATUS_TRASH)
        .collectAsState(initial = emptyList())

    var editableUserName by rememberSaveable(userName) { mutableStateOf(userName) }
    var selectedOshiName by rememberSaveable(oshiName) { mutableStateOf(oshiName) }
    var selectedThemeModeName by rememberSaveable(themeMode.name) { mutableStateOf(themeMode.name) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteBlockedDialog by remember { mutableStateOf(false) }
    var showDatabaseResetConfirmDialog by remember { mutableStateOf(false) }

    val selectedThemeMode = ThemeMode.fromPreference(selectedThemeModeName)
    val themeOptions = listOf(
        ThemeOption(ThemeMode.SYSTEM, stringResource(R.string.settings_theme_mode_system)),
        ThemeOption(ThemeMode.LIGHT, stringResource(R.string.settings_theme_mode_light)),
        ThemeOption(ThemeMode.DARK, stringResource(R.string.settings_theme_mode_dark)),
        ThemeOption(ThemeMode.VIRTUAL_SINGER, "VIRTUAL SINGER"),
        ThemeOption(ThemeMode.LEO_NEED, "Leo/need"),
        ThemeOption(ThemeMode.MORE_MORE_JUMP, "MORE MORE JUMP!"),
        ThemeOption(ThemeMode.VIVID_BAD_SQUAD, "Vivid BAD SQUAD"),
        ThemeOption(ThemeMode.WONDERLANDS_SHOWTIME, "ワンダーランズ×ショウタイム"),
        ThemeOption(ThemeMode.NIGHTCORD, "25時、ナイトコードで。"),
    )
    val themeColor = LocalOshiColor.current
    val themeOnColor = LocalOshiOnColor.current
    val isDarkTheme = LocalIsDarkTheme.current
    val cardBackground = themeColor
    val cardContentColor = themeOnColor
    val profileSavedToast = stringResource(R.string.settings_profile_saved_toast)
    val hasPendingChanges = editableUserName.trim() != userName ||
        selectedOshiName != oshiName ||
        selectedThemeMode != themeMode

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeColor,
                    titleContentColor = themeOnColor,
                    navigationIconContentColor = themeOnColor,
                    actionIconContentColor = themeOnColor,
                ),
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
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = cardBackground,
                        contentColor = cardContentColor,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_profile_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        OutlinedTextField(
                            value = editableUserName,
                            onValueChange = { editableUserName = it },
                            singleLine = true,
                            label = { Text(text = stringResource(R.string.settings_user_name_label)) },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        HorizontalDivider()

                        Text(
                            text = stringResource(R.string.settings_theme_mode_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        themeOptions.forEach { option ->
                            val isSelected = selectedThemeMode == option.mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedThemeModeName = option.mode.name },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedThemeModeName = option.mode.name },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = themeOnColor,
                                        unselectedColor = themeOnColor.copy(alpha = 0.6f),
                                    ),
                                )
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }

                        HorizontalDivider()

                        Text(
                            text = stringResource(R.string.settings_oshi_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.settings_current_oshi, selectedOshiName),
                            style = MaterialTheme.typography.bodySmall,
                            color = themeOnColor.copy(alpha = 0.75f),
                        )

                        OSHI_UNITS.forEach { (unitName, characters) ->
                            val unitColor = when (unitName) {
                                "VIRTUAL SINGER" -> if (isDarkTheme) Color(0xFFF2F9FF) else Color(0xFF000000)
                                "Leo/need" -> Color(0xFF4455DD)
                                "MORE MORE JUMP!" -> Color(0xFF88DD44)
                                "Vivid BAD SQUAD" -> Color(0xFFEE1166)
                                "ワンダーランズ×ショウタイム" -> Color(0xFFFF9900)
                                "25時、ナイトコードで。" -> Color(0xFF884499)
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Text(
                                text = unitName,
                                style = MaterialTheme.typography.titleSmall,
                                color = unitColor,
                                modifier = Modifier.padding(top = 8.dp),
                            )

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(
                                    items = characters,
                                    key = { character -> character.name },
                                ) { oshi ->
                                    val isSelected = selectedOshiName == oshi.name
                                    val oshiColor = colorResource(id = oshi.colorResId)
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clickable { selectedOshiName = oshi.name }
                                            .padding(4.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(oshiColor)
                                                .border(
                                                    width = if (isSelected) 4.dp else 1.dp,
                                                    color = if (isSelected) themeOnColor else MaterialTheme.colorScheme.outline,
                                                    shape = CircleShape,
                                                ),
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = oshi.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.saveProfileSettings(
                                    userName = editableUserName,
                                    oshiName = selectedOshiName,
                                    themeMode = selectedThemeMode,
                                )
                                Toast.makeText(
                                    context,
                                    profileSavedToast,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            enabled = hasPendingChanges,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.settings_profile_save))
                        }
                    }
                }
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = cardBackground,
                        contentColor = cardContentColor,
                    ),
                ) {
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
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = cardBackground,
                        contentColor = cardContentColor,
                    ),
                ) {
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
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = cardBackground,
                        contentColor = cardContentColor,
                    ),
                ) {
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
        }
    }
}

private data class ThemeOption(
    val mode: ThemeMode,
    val label: String,
)
