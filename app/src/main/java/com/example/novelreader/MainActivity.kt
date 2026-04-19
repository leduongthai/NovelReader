package com.example.novelreader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.novelreader.presentation.ui.navigation.AppNavHost
import com.example.novelreader.presentation.ui.navigation.MainBottomBar
import com.example.novelreader.presentation.ui.navigation.Screen
import com.example.novelreader.presentation.ui.theme.AINovelReaderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Đọc deep link nếu app mở từ link
        val deepLinkBookId = intent?.data?.let { uri ->
            if (uri.host == "novelreader.app" && uri.pathSegments.firstOrNull() == "story") {
                uri.pathSegments.getOrNull(1)  // lấy bookId từ /story/{bookId}
            } else null
        }

        setContent {
            AINovelReaderTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val hideBottomNav = currentRoute?.startsWith("reader/") == true

                // Navigate đến reader nếu mở từ deep link
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

    // Xử lý khi app đang chạy mà nhận được deep link mới
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}