package com.example.sekairatingsystem.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sekairatingsystem.R
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
    onOpenFailedTab: () -> Unit,
) {
    val context = LocalContext.current
    val userName by viewModel.userName.collectAsState()
    val isInitializing by viewModel.isInitializing.collectAsState()
    val selectedFolderUri by viewModel.selectedFolderUri.collectAsState()
    val isScanningFolder by viewModel.isScanningFolder.collectAsState()
    val isAnalyzingOcr by viewModel.isAnalyzingOcr.collectAsState()
    val isCalculatingRates by viewModel.isCalculatingRates.collectAsState()
    val totalRate by viewModel.totalRate.collectAsState()
    val bestRateSum by viewModel.bestRateSum.collectAsState()
    val recentRateSum by viewModel.recentRateSum.collectAsState()
    val totalPlayCount by viewModel.totalPlayCount.collectAsState()
    val oshiName by viewModel.oshiName.collectAsState()
    val unknownSongNotice by viewModel.unknownSongNotice.collectAsState()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { selectedUri ->
        if (selectedUri != null) {
            viewModel.onFolderSelected(selectedUri, context)
        }
    }

    val ratingStyle = getArcaeaRatingStyle(totalRate)

    unknownSongNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUnknownSongNotice() },
            title = { Text(text = stringResource(R.string.main_unknown_song_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(
                            R.string.main_unknown_song_dialog_message,
                            notice.count,
                        ),
                    )
                    if (notice.songNames.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.main_unknown_song_dialog_names,
                                notice.songNames.joinToString(separator = "\n"),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissUnknownSongNotice()
                        onOpenFailedTab()
                    },
                ) {
                    Text(text = stringResource(R.string.main_unknown_song_dialog_open_failed))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUnknownSongNotice() }) {
                    Text(text = stringResource(R.string.main_unknown_song_dialog_close))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Profile & Rating Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(18.dp, RoundedCornerShape(24.dp), spotColor = ratingStyle.glowColor)
                    .clip(RoundedCornerShape(24.dp))
                    .background(ratingStyle.background)
                    .border(2.dp, ratingStyle.frameColor, RoundedCornerShape(24.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ratingStyle.gloss),
                )

                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (userName.isBlank()) stringResource(R.string.main_header_rating_default) else stringResource(R.string.main_header_rating, userName),
                        style = MaterialTheme.typography.titleMedium,
                        color = ratingStyle.labelColor,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    Text(
                        text = stringResource(R.string.title_total_rate),
                        style = MaterialTheme.typography.titleMedium,
                        color = ratingStyle.labelColor.copy(alpha = 0.9f),
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = String.format(Locale.JAPAN, "%.2f", totalRate),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 64.sp,
                        ),
                        color = ratingStyle.valueColor,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.title_best30),
                                color = ratingStyle.labelColor.copy(alpha = 0.85f),
                            )
                            Text(
                                text = String.format(Locale.JAPAN, "%.2f", bestRateSum),
                                color = ratingStyle.valueColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.title_recent10),
                                color = ratingStyle.labelColor.copy(alpha = 0.85f),
                            )
                            Text(
                                text = String.format(Locale.JAPAN, "%.2f", recentRateSum),
                                color = ratingStyle.valueColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.main_total_play_count),
                                color = ratingStyle.labelColor.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                            )
                            Text(
                                text = stringResource(R.string.main_total_play_count_value, totalPlayCount),
                                color = ratingStyle.valueColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.main_oshi_label),
                                color = ratingStyle.labelColor.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                            )
                            Text(
                                text = oshiName,
                                color = ratingStyle.valueColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val isBusy = isInitializing || isScanningFolder || isAnalyzingOcr || isCalculatingRates

            if (selectedFolderUri == null) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.msg_require_folder),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Button(
                            onClick = { folderPickerLauncher.launch(null) },
                            enabled = !isInitializing && !isScanningFolder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text(text = stringResource(R.string.btn_select_folder))
                        }
                        if (isScanningFolder) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                val pulseAnimation = rememberInfiniteTransition()
                val pulseScale by pulseAnimation.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 650),
                        repeatMode = RepeatMode.Reverse,
                    ),
                )

                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent),
                ) {
                    Button(
                        onClick = { viewModel.processAllUnreadData(context) },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .scale(if (isBusy) pulseScale else 1f),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF353B46),
                            contentColor = Color(0xFFF4F6F8),
                            disabledContainerColor = Color(0xFF8A8F98),
                            disabledContentColor = Color(0xFFE7E7E7),
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                color = Color(0xFFF4F6F8),
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 3.dp,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.btn_processing),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.btn_start_calc),
                                modifier = Modifier.size(30.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.btn_start_calc),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private data class ArcaeaRatingStyle(
    val background: Brush,
    val gloss: Brush,
    val frameColor: Color,
    val glowColor: Color,
    val valueColor: Color,
    val labelColor: Color,
)

private fun getArcaeaRatingStyle(rate: Float): ArcaeaRatingStyle {
    return when {
        rate <= 0f -> flatStyleOf(
            background = Color(0xFF8C8C8C),
            frame = Color(0xFFB7B7B7),
            glow = Color(0xFF777777),
            value = Color(0xFFF3F3F3),
            label = Color(0xFFE4E4E4),
        )
        rate >= 1300f -> {
            ArcaeaRatingStyle(
                background = Brush.linearGradient(
                    listOf(
                        Color(0xFFFF5DAA),
                        Color(0xFFFF8C42),
                        Color(0xFFFFE45E),
                        Color(0xFF47D16C),
                        Color(0xFF3CA8FF),
                        Color(0xFFA56BFF),
                    ),
                ),
                gloss = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.24f), Color.Transparent),
                ),
                frameColor = Color(0xFFF8FAFF),
                glowColor = Color(0xFFFF7CCB),
                valueColor = Color(0xFFFFFFFF),
                labelColor = Color(0xFFF7F9FF),
            )
        }

        rate >= 1200f -> {
            ArcaeaRatingStyle(
                background = Brush.linearGradient(
                    listOf(Color(0xFF6E4D00), Color(0xFFB8890A), Color(0xFFF3D46E), Color(0xFF8C5E00)),
                ),
                gloss = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.36f), Color.Transparent),
                ),
                frameColor = Color(0xFFFCEAA2),
                glowColor = Color(0xFFFFC947),
                valueColor = Color(0xFF1B1300),
                labelColor = Color(0xFF2D2200),
            )
        }

        rate >= 1100f -> {
            ArcaeaRatingStyle(
                background = Brush.linearGradient(
                    listOf(Color(0xFF5A616A), Color(0xFFBFC6D1), Color(0xFFE9EDF4), Color(0xFF656B73)),
                ),
                gloss = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                ),
                frameColor = Color(0xFFE9EEF8),
                glowColor = Color(0xFFC6CEDA),
                valueColor = Color(0xFF1D2025),
                labelColor = Color(0xFF2E3339),
            )
        }

        rate >= 1000f -> {
            ArcaeaRatingStyle(
                background = Brush.linearGradient(
                    listOf(Color(0xFF4D2D17), Color(0xFF9E6338), Color(0xFFE5B07E), Color(0xFF6E3F1F)),
                ),
                gloss = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.3f), Color.Transparent),
                ),
                frameColor = Color(0xFFE0AC7A),
                glowColor = Color(0xFFC58249),
                valueColor = Color(0xFFFFF2E8),
                labelColor = Color(0xFFFDE2CF),
            )
        }

        rate >= 800f -> flatStyleOf(
            background = Color(0xFFC83145),
            frame = Color(0xFFFF7B8B),
            glow = Color(0xFFEA4A61),
            value = Color(0xFFFFF5F6),
            label = Color(0xFFFFE6E9),
        )

        rate >= 600f -> flatStyleOf(
            background = Color(0xFFD87721),
            frame = Color(0xFFFFB877),
            glow = Color(0xFFE38A32),
            value = Color(0xFFFFF6ED),
            label = Color(0xFFFFE6CC),
        )

        rate >= 400f -> flatStyleOf(
            background = Color(0xFF7B43D6),
            frame = Color(0xFFC9A7FF),
            glow = Color(0xFF9967F1),
            value = Color(0xFFF7F2FF),
            label = Color(0xFFE9DDFF),
        )

        rate >= 250f -> flatStyleOf(
            background = Color(0xFF3AAA64),
            frame = Color(0xFFA9F1C2),
            glow = Color(0xFF56D988),
            value = Color(0xFFF4FFF8),
            label = Color(0xFFDDFBE9),
        )

        rate >= 150f -> flatStyleOf(
            background = Color(0xFFDDC433),
            frame = Color(0xFFFFF08A),
            glow = Color(0xFFE7CF47),
            value = Color(0xFF231E00),
            label = Color(0xFF403700),
        )

        rate >= 50f -> flatStyleOf(
            background = Color(0xFF41B5E8),
            frame = Color(0xFF98E4FF),
            glow = Color(0xFF58C8F4),
            value = Color(0xFFF2FBFF),
            label = Color(0xFFD8F4FF),
        )

        else -> flatStyleOf(
            background = Color(0xFF2B69C7),
            frame = Color(0xFF91B8FF),
            glow = Color(0xFF4D8CF3),
            value = Color(0xFFF2F7FF),
            label = Color(0xFFDDEAFF),
        )
    }
}

private fun flatStyleOf(
    background: Color,
    frame: Color,
    glow: Color,
    value: Color,
    label: Color,
): ArcaeaRatingStyle {
    return ArcaeaRatingStyle(
        background = SolidColor(background),
        gloss = SolidColor(Color.Transparent),
        frameColor = frame,
        glowColor = glow,
        valueColor = value,
        labelColor = label,
    )
}
