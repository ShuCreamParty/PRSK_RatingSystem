package com.example.sekairatingsystem.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.sekairatingsystem.Constants
import com.example.sekairatingsystem.R
import com.example.sekairatingsystem.data.entity.ScoreRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagementScreen(
    viewModel: MainViewModel,
    initialTabStatus: String,
    onOpenDrawer: () -> Unit,
    onEditRecord: (Long) -> Unit,
) {
    var selectedTab by rememberSaveable(initialTabStatus) {
        mutableStateOf(FileManagementTab.fromStatus(initialTabStatus))
    }
    val recordsFlow = remember(selectedTab) {
        if (selectedTab == FileManagementTab.CALCULATED) {
            viewModel.getCalculatedRecords()
        } else {
            viewModel.getScoreRecordsByStatus(selectedTab.status)
        }
    }
    val records by recordsFlow.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.menu_file_management)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.cd_open_menu),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TabRow(selectedTabIndex = FileManagementTab.entries.indexOf(selectedTab)) {
                FileManagementTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(text = tab.label.substringBefore("(").take(4)) },
                    )
                }
            }

            if (records.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = selectedTab.emptyMessage,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    items(
                        items = records,
                        key = { record -> record.id },
                    ) { record ->
                        FileRecordCard(
                            record = record,
                            onClick = { onEditRecord(record.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRecordCard(
    record: ScoreRecord,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = record.imageUri.toDisplayFileName(),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "曲名: ${record.songName ?: "未設定"}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = "難易度: ${record.difficulty ?: "-"} / Lv: ${record.level?.toString() ?: "-"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "PERFECT ${record.perfectCount?.toString() ?: "-"} / GREAT ${record.greatCount?.toString() ?: "-"} / GOOD ${record.goodCount?.toString() ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = "BAD ${record.badCount?.toString() ?: "-"} / MISS ${record.missCount?.toString() ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private enum class FileManagementTab(
    val status: String,
    val label: String,
    val emptyMessage: String,
) {
    FAILED(
        status = Constants.STATUS_FAILED,
        label = "読取失敗(FAILED)",
        emptyMessage = "読取失敗の画像はありません",
    ),
    CALCULATED(
        status = Constants.STATUS_CALCULATED,
        label = "計算済(CALCULATED)",
        emptyMessage = "計算済の画像はありません",
    ),
    TRASH(
        status = Constants.STATUS_TRASH,
        label = "削除予定(TRASH)",
        emptyMessage = "削除予定の画像はありません",
    ),
    ;

    companion object {
        fun fromStatus(status: String): FileManagementTab {
            return entries.firstOrNull { tab -> tab.status.equals(status, ignoreCase = true) }
                ?: FAILED
        }
    }
}

private fun String.toDisplayFileName(): String {
    val parsedUri = Uri.parse(this)
    return parsedUri.lastPathSegment
        ?.substringAfterLast('/')
        ?.substringAfterLast(':')
        ?.takeIf(String::isNotBlank)
        ?: this
}