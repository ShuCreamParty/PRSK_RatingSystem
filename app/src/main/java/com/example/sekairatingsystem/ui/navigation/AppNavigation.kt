package com.example.sekairatingsystem.ui.navigation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.sekairatingsystem.Constants
import com.example.sekairatingsystem.R
import com.example.sekairatingsystem.ui.EditScoreScreen
import com.example.sekairatingsystem.ui.FileManagementScreen
import com.example.sekairatingsystem.ui.MainScreen
import com.example.sekairatingsystem.ui.MainViewModel
import com.example.sekairatingsystem.ui.SettingsScreen
import com.example.sekairatingsystem.ui.UsedListScreen
import com.example.sekairatingsystem.ui.theme.LocalOshiColor
import com.example.sekairatingsystem.ui.theme.LocalOshiOnColor
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val themeColor = LocalOshiColor.current
    val themeOnColor = LocalOshiOnColor.current
    val drawerContentColor = themeOnColor
    val drawerItemColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = themeColor,
        selectedTextColor = themeOnColor,
        selectedIconColor = themeOnColor,
        unselectedTextColor = themeOnColor.copy(alpha = 0.85f),
        unselectedIconColor = themeOnColor.copy(alpha = 0.7f),
    )
    val drawerBackground = themeColor

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = drawerBackground,
                drawerContentColor = drawerContentColor,
            ) {
                Text(
                    text = stringResource(R.string.menu_drawer),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = drawerContentColor,
                )
                Spacer(modifier = Modifier.height(8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.menu_home)) },
                    selected = currentRoute == "main",
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("main") {
                            popUpTo("main") { inclusive = true }
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = drawerItemColors,
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.menu_used_list)) },
                    selected = currentRoute == "usedList",
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("usedList")
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = drawerItemColors,
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.menu_file_management)) },
                    selected = currentRoute == "fileManagement",
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("fileManagement")
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = drawerItemColors,
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.menu_settings)) },
                    selected = currentRoute == "settings",
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("settings")
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = drawerItemColors,
                )
            }
        }
    ) {
        NavHost(navController = navController, startDestination = "main") {
            composable("main") {
                MainScreen(
                    viewModel = viewModel,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenFailedTab = {
                        navController.navigate("fileManagement")
                    }
                )
            }
            composable("fileManagement") {
                FileManagementScreen(
                    viewModel = viewModel,
                    initialTabStatus = Constants.STATUS_FAILED,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onEditRecord = { recordId ->
                        navController.navigate("editScore/${recordId}")
                    }
                )
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("usedList") {
                UsedListScreen(
                    viewModel = viewModel,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                )
            }
            composable(
                route = "editScore/{recordId}",
                arguments = listOf(navArgument("recordId") { type = NavType.LongType })
            ) { backStackEntry ->
                val recordId = backStackEntry.arguments?.getLong("recordId") ?: -1L
                EditScoreScreen(
                    recordId = recordId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
