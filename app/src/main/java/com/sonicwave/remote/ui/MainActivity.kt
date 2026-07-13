package com.sonicwave.remote.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sonicwave.remote.ui.screens.discovery.DiscoveryScreen
import com.sonicwave.remote.ui.screens.remote.RemoteMainScreen
import com.sonicwave.remote.ui.theme.SonicWaveRemoteTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SonicWaveRemoteTheme {
                SonicWaveRemoteNavHost()
            }
        }
    }
}

@Composable
private fun SonicWaveRemoteNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "discovery"
    ) {
        composable("discovery") {
            DiscoveryScreen(
                onDeviceFound = { host, port ->
                    navController.navigate("remote/$host/$port") {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = "remote/{host}/{port}",
            arguments = listOf(
                navArgument("host") { type = NavType.StringType },
                navArgument("port") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val host = backStackEntry.arguments?.getString("host") ?: return@composable
            val port = backStackEntry.arguments?.getInt("port") ?: 8082
            RemoteMainScreen(host = host, port = port)
        }
    }
}
