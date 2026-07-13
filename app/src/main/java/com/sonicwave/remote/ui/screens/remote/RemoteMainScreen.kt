package com.sonicwave.remote.ui.screens.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import com.sonicwave.remote.ui.screens.library.LibraryScreen
import com.sonicwave.remote.ui.screens.search.SearchScreen

private val Background = Color(0xFF121212)
private val Surface1 = Color(0xFF1E1E2E)
private val Primary = Color(0xFFBB86FC)
private val TextSecondary = Color(0xFF888888)

private enum class RemoteTab(val label: String, val icon: ImageVector) {
    NOW_PLAYING("Now Playing", Icons.Filled.MusicNote),
    LIBRARY("Library", Icons.Filled.LibraryMusic),
    SEARCH("Search", Icons.Filled.Search)
}

/**
 * Root screen after connecting to SonicWave.
 * Hosts three bottom-nav tabs: Now Playing, Library, Search.
 * All three share the same [RemoteViewModel] so the connection is maintained.
 */
@Composable
fun RemoteMainScreen(
    host: String,
    port: Int,
    viewModel: RemoteViewModel = hiltViewModel()
) {
    LaunchedEffect(host, port) {
        viewModel.connect(host, port)
    }

    var selectedTab by remember { mutableStateOf(RemoteTab.NOW_PLAYING) }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            NavigationBar(containerColor = Surface1) {
                RemoteTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Primary,
                            selectedTextColor = Primary,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Color(0x22BB86FC)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                RemoteTab.NOW_PLAYING -> NowPlayingTab(viewModel = viewModel)
                RemoteTab.LIBRARY -> LibraryScreen(viewModel = viewModel)
                RemoteTab.SEARCH -> SearchScreen(viewModel = viewModel)
            }
        }
    }
}
