package com.example.novelreader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.novelreader.presentation.ui.navigation.AppNavHost
import com.example.novelreader.presentation.ui.navigation.MainBottomBar
import com.example.novelreader.presentation.ui.navigation.Screen
import com.example.novelreader.presentation.ui.theme.AINovelReaderTheme
import com.example.novelreader.presentation.viewmodel.ReaderViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var dataStore: DataStore<Preferences>

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val deepLinkBookId = intent?.data?.let { uri ->
            if (uri.host == "novelreader.app" && uri.pathSegments.firstOrNull() == "story") {
                uri.pathSegments.getOrNull(1)
            } else null
        }

        setContent {
            val isDarkMode by remember(dataStore) {
                dataStore.data.map { prefs -> prefs[ReaderViewModel.IS_DARK_MODE] ?: false }
            }.collectAsState(initial = false)

            AINovelReaderTheme(darkTheme = isDarkMode) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val hideBottomNav = currentRoute?.startsWith("reader/") == true

                if (deepLinkBookId != null) {
                    androidx.compose.runtime.LaunchedEffect(deepLinkBookId) {
                        navController.navigate(Screen.Reader.createRoute(deepLinkBookId, 0))
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (!hideBottomNav) {
                            MainBottomBar(navController = navController)
                        }
                    }
                ) { innerPadding ->
                    AppNavHost(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
