package com.example.sekairatingsystem

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.sekairatingsystem.ui.MainViewModel
import com.example.sekairatingsystem.ui.ThemeMode
import com.example.sekairatingsystem.ui.navigation.AppNavigation
import com.example.sekairatingsystem.ui.theme.SekaiRatingSystemTheme

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel.initializeMasterData()
        enableEdgeToEdge()
        setContent {
            val themeColorLong by mainViewModel.themeColor.collectAsState()
            val themeMode by mainViewModel.themeMode.collectAsState()
            val useDarkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            SekaiRatingSystemTheme(darkTheme = useDarkTheme, seedColor = themeColorLong) {
                AppNavigation(viewModel = mainViewModel)
            }
        }
    }
}