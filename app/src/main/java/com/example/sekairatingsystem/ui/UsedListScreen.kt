package com.example.sekairatingsystem.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sekairatingsystem.R
import com.example.sekairatingsystem.data.entity.ScoreRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class UsedListTab(val labelResId: Int) {
    BEST(R.string.used_list_best_tab),
    RECENT(R.string.used_list_recent_tab),
}

private enum class UsedSortType(val labelResId: Int) {
    SINGLE_RATE(R.string.used_list_sort_single_rate),
    SONG_NAME(R.string.used_list_sort_song_name),
    LEVEL(R.string.used_list_sort_level),
    DATE(R.string.used_list_sort_date),
}

private enum class UsedSortOrder(val labelResId: Int) {
    DESC(R.string.used_list_sort_desc),
    ASC(R.string.used_list_sort_asc),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsedListScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
) {
    val bestFrameRecords by viewModel.bestFrameRecords.collectAsState()
    val recentFrameRecords by viewModel.recentFrameRecords.collectAsState()

    var selectedTab by rememberSaveable { mutableStateOf(UsedListTab.BEST) }
    var sortType by rememberSaveable { mutableStateOf(UsedSortType.SINGLE_RATE) }
    var sortOrder by rememberSaveable { mutableStateOf(UsedSortOrder.DESC) }
    var showSortControls by rememberSaveable { mutableStateOf(false) }

    val sourceRecords = when (selectedTab) {
        UsedListTab.BEST -> bestFrameRecords
        UsedListTab.RECENT -> recentFrameRecords
    }

    val sortedRecords = remember(sourceRecords, sortType, sortOrder) {
        sortUsedRecords(sourceRecords, sortType, sortOrder)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.used_list_title)) },
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
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TabButton(
                    text = stringResource(UsedListTab.BEST.labelResId),
                    selected = selectedTab == UsedListTab.BEST,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = UsedListTab.BEST },
                )
                TabButton(
                    text = stringResource(UsedListTab.RECENT.labelResId),
                    selected = selectedTab == UsedListTab.RECENT,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = UsedListTab.RECENT },
                )
            }

            OutlinedButton(
                onClick = { showSortControls = !showSortControls },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (showSortControls) {
                        stringResource(R.string.used_list_sort_hide)
                    } else {
                        stringResource(R.string.used_list_sort_show)
                    },
                )
            }

            if (showSortControls) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.used_list_sort_panel_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(
                                R.string.used_list_sort_current,
                                stringResource(sortType.labelResId),
                                stringResource(sortOrder.labelResId),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        HorizontalDivider()

                        Text(
                            text = stringResource(R.string.used_list_sort_type),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )

                        UsedSortType.entries.chunked(2).forEach { typeRow ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                typeRow.forEach { type ->
                                    SortChip(
                                        text = stringResource(type.labelResId),
                                        selected = sortType == type,
                                        modifier = Modifier.weight(1f),
                                        onClick = { sortType = type },
                                    )
                                }
                                if (typeRow.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        HorizontalDivider()

                        Text(
                            text = stringResource(R.string.used_list_sort_order),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            UsedSortOrder.entries.forEach { order ->
                                SortChip(
                                    text = stringResource(order.labelResId),
                                    selected = sortOrder == order,
                                    modifier = Modifier.weight(1f),
                                    onClick = { sortOrder = order },
                                )
                            }
                        }
                    }
                }
            }

            if (sortedRecords.isEmpty()) {
                Text(
                    text = stringResource(R.string.used_list_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = sortedRecords,
                        key = { record -> record.id },
                    ) { record ->
                        UsedRecordCard(record = record)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(text = text)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(text = text)
        }
    }
}

@Composable
private fun SortChip(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = text) },
        modifier = modifier,
    )
}

@Composable
private fun UsedRecordCard(record: ScoreRecord) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = record.songName ?: "未設定",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "難易度: ${record.difficulty ?: "-"} / Lv: ${record.level?.toString() ?: "-"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "単曲レート: ${record.singleRate?.let { rate -> String.format(Locale.JAPAN, "%.2f", rate) } ?: "0.00"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "更新日時: ${formatUsedRecordDate(record.date)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun sortUsedRecords(
    records: List<ScoreRecord>,
    sortType: UsedSortType,
    sortOrder: UsedSortOrder,
): List<ScoreRecord> {
    val comparator = when (sortType) {
        UsedSortType.SINGLE_RATE -> compareBy<ScoreRecord> { record -> record.singleRate ?: Float.NEGATIVE_INFINITY }
            .thenBy { record -> record.songName.orEmpty() }
            .thenBy { record -> record.difficulty.orEmpty() }

        UsedSortType.SONG_NAME -> compareBy<ScoreRecord> { record -> record.songName.orEmpty() }
            .thenBy { record -> record.difficulty.orEmpty() }

        UsedSortType.LEVEL -> compareBy<ScoreRecord> { record -> record.level ?: Int.MIN_VALUE }
            .thenBy { record -> record.songName.orEmpty() }

        UsedSortType.DATE -> compareBy<ScoreRecord> { record -> record.date ?: Long.MIN_VALUE }
            .thenBy { record -> record.songName.orEmpty() }
    }

    return if (sortOrder == UsedSortOrder.ASC) {
        records.sortedWith(comparator)
    } else {
        records.sortedWith(comparator.reversed())
    }
}

private fun formatUsedRecordDate(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) {
        return "-"
    }

    return DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
        .format(
            Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime(),
        )
}
