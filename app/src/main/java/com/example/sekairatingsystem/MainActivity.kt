package com.example.sekairatingsystem

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.sekairatingsystem.ui.MainViewModel
import com.example.sekairatingsystem.ui.ThemeMode
import com.example.sekairatingsystem.ui.resolveOshiColorRes
import com.example.sekairatingsystem.ui.navigation.AppNavigation
import com.example.sekairatingsystem.ui.theme.SekaiRatingSystemTheme
import com.example.sekairatingsystem.ui.theme.UnitLeoNeed
import com.example.sekairatingsystem.ui.theme.UnitMoreMoreJump
import com.example.sekairatingsystem.ui.theme.UnitNightcord
import com.example.sekairatingsystem.ui.theme.UnitVividBadSquad
import com.example.sekairatingsystem.ui.theme.UnitVirtualSinger
import com.example.sekairatingsystem.ui.theme.UnitWonderlandsShowtime

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel.initializeMasterData()
        enableEdgeToEdge()
        setContent {
            val themeMode by mainViewModel.themeMode.collectAsState()
            val oshiName by mainViewModel.oshiName.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = themeMode.resolveDarkTheme(systemDark)
            val themeColor = resolveThemeColor(themeMode, systemDark)
            val oshiBackground = colorResource(id = resolveOshiColorRes(oshiName))

            SekaiRatingSystemTheme(
                darkTheme = useDarkTheme,
                themeColor = themeColor,
                backgroundColor = oshiBackground,
            ) {
                AppNavigation(viewModel = mainViewModel)
            }
        }
    }
}

private fun resolveThemeColor(themeMode: ThemeMode, systemDark: Boolean): Color {
    return when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDark) Color(0xFF141A2A) else Color.White
        ThemeMode.LIGHT -> Color.White
        ThemeMode.DARK -> Color(0xFF141A2A)
        ThemeMode.VIRTUAL_SINGER -> UnitVirtualSinger
        ThemeMode.LEO_NEED -> UnitLeoNeed
        ThemeMode.MORE_MORE_JUMP -> UnitMoreMoreJump
        ThemeMode.VIVID_BAD_SQUAD -> UnitVividBadSquad
        ThemeMode.WONDERLANDS_SHOWTIME -> UnitWonderlandsShowtime
        ThemeMode.NIGHTCORD -> UnitNightcord
    }
}