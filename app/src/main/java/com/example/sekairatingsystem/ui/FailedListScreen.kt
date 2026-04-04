package com.example.sekairatingsystem.ui

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sekairatingsystem.data.entity.ScoreRecord
import com.example.sekairatingsystem.ui.theme.LocalOshiColor
import com.example.sekairatingsystem.ui.theme.LocalOshiOnColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FailedListScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onEditRecord: (Long) -> Unit,
) {
    val failedRecords by viewModel.failedRecords.collectAsState()
    val themeColor = LocalOshiColor.current
    val themeOnColor = LocalOshiOnColor.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "読み取り失敗一覧") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        if (failedRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "読み取り失敗の画像はありません",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = failedRecords,
                key = { record -> record.id },
            ) { record ->
                FailedRecordCard(
                    record = record,
                    onClick = { onEditRecord(record.id) },
                )
            }
        }
    }
}

@Composable
private fun FailedRecordCard(
    record: ScoreRecord,
    onClick: () -> Unit,
) {
    val cardBackground = LocalOshiColor.current
    val cardContentColor = LocalOshiOnColor.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = cardBackground,
            contentColor = cardContentColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = record.imageUri.toDisplayFileName(),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "曲名候補: ${record.songName ?: "未抽出"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "難易度: ${record.difficulty ?: "未抽出"} / Lv: ${record.level?.toString() ?: "-"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "PERFECT ${record.perfectCount?.toString() ?: "-"} / GREAT ${record.greatCount?.toString() ?: "-"} / GOOD ${record.goodCount?.toString() ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "BAD ${record.badCount?.toString() ?: "-"} / MISS ${record.missCount?.toString() ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
            )
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