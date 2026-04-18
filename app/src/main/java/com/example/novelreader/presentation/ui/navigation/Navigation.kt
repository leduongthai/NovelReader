package com.example.novelreader.presentation.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.novelreader.presentation.ui.bookshelf.BookshelfScreen
import com.example.novelreader.presentation.ui.community.CommunityScreen
import com.example.novelreader.presentation.ui.discover.DiscoverScreen
import com.example.novelreader.presentation.ui.discover.NovelDetailScreen
import com.example.novelreader.presentation.ui.profile.ProfileScreen
import com.example.novelreader.presentation.ui.reader.ReaderScreen

// ============================================================
// NAVIGATION ROUTES
// ============================================================

sealed class Screen(val route: String) {
    object Bookshelf   : Screen("bookshelf")
    object Discover    : Screen("discover")
    object Community   : Screen("community")
    object Profile     : Screen("profile")

    object NovelDetail : Screen("novel_detail/{url}") {
        fun createRoute(url: String) = "novel_detail/$url"
    }
    object Reader : Screen("reader/{bookId}/{chapterIndex}") {
        fun createRoute(bookId: String, chapterIndex: Int = 0) = "reader/$bookId/$chapterIndex"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Bookshelf,  "Kệ sách",   Icons.Default.MenuBook),
    BottomNavItem(Screen.Discover,   "Khám phá",  Icons.Default.Explore),
    BottomNavItem(Screen.Community,  "Cộng đồng", Icons.Default.Forum),
    BottomNavItem(Screen.Profile,    "Cá nhân",   Icons.Default.Person)
)

// ============================================================
// MAIN APP NAVIGATION HOST
// ============================================================

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Bookshelf.route,
        modifier = modifier
    ) {
        composable(Screen.Bookshelf.route) {
            BookshelfScreen(
                onOpenReader = { bookId, chapterIndex ->
                    navController.navigate(Screen.Reader.createRoute(bookId, chapterIndex))
                }
            )
        }

        composable(Screen.Discover.route) {
            DiscoverScreen(
                onOpenDetail = { url ->
                    val encoded = java.net.URLEncoder.encode(url, "UTF-8")
                    navController.navigate(Screen.NovelDetail.createRoute(encoded))
                }
            )
        }

        composable(Screen.NovelDetail.route) { backStack ->
            val encodedUrl = backStack.arguments?.getString("url") ?: ""
            val url = java.net.URLDecoder.decode(encodedUrl, "UTF-8")
            NovelDetailScreen(
                detailUrl = url,
                onBack = { navController.popBackStack() },
                onOpenReader = { bookId ->
                    navController.navigate(Screen.Reader.createRoute(bookId))
                }
            )
        }

        composable(Screen.Community.route) {
            CommunityScreen()
        }

        composable(Screen.Profile.route) {
            ProfileScreen()
        }

        composable(Screen.Reader.route) { backStack ->
            val bookId = backStack.arguments?.getString("bookId") ?: return@composable
            val chapterIndex = backStack.arguments?.getString("chapterIndex")?.toIntOrNull() ?: 0
            ReaderScreen(
                bookId = bookId,
                initialChapterIndex = chapterIndex,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

// ============================================================
// BOTTOM NAVIGATION BAR
// ============================================================

@Composable
fun MainBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.screen.route,
                onClick = {
                    navController.navigate(item.screen.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}
