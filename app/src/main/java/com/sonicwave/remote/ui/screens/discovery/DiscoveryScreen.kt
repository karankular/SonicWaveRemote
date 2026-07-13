package com.sonicwave.remote.ui.screens.discovery

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    onDeviceFound: (host: String, port: Int) -> Unit,
    viewModel: DiscoveryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val foundDevice by viewModel.foundDevice.collectAsStateWithLifecycle()
    val connecting by viewModel.connecting.collectAsStateWithLifecycle()
    val updateRequired by viewModel.updateRequired.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Navigate when device found and user taps Connect
    LaunchedEffect(state, foundDevice) {
        // Navigation is triggered by the button, not automatically
    }

    // The version handshake found the SERVER requires a newer build of THIS app than it declares —
    // block entering the remote UI and tell the user plainly what to do instead of a cryptic failure.
    updateRequired?.let { incompatible ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateRequired() },
            title = { Text("Update required") },
            text = {
                Text(
                    "This Remote app is out of date (needs protocol v${incompatible.minRequired}, " +
                        "this build has v${incompatible.declared}). Please update it from the Play Store."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissUpdateRequired() }) { Text("OK") }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            // Logo
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1E1E2E),
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFFBB86FC),
                        modifier = Modifier.size(52.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "SonicWave Remote",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFBB86FC)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Control your music from any room",
                fontSize = 14.sp,
                color = Color(0xFF888888),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Animated content based on discovery state
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(200))
                },
                label = "discovery_state"
            ) { currentState ->
                when (currentState) {
                    DiscoveryState.Searching -> SearchingContent()

                    DiscoveryState.Found -> {
                        val device = foundDevice
                        if (device != null) {
                            FoundContent(
                                host = device.first,
                                port = device.second,
                                connecting = connecting,
                                onConnect = {
                                    viewModel.connect(device.first, device.second) {
                                        onDeviceFound(device.first, device.second)
                                    }
                                },
                                onSearchAgain = { viewModel.startSearch() }
                            )
                        } else {
                            SearchingContent()
                        }
                    }

                    DiscoveryState.ManualEntry -> ManualEntryContent(
                        ip = viewModel.manualIp,
                        onIpChange = { viewModel.manualIp = it },
                        onConnect = {
                            keyboardController?.hide()
                            viewModel.connectManual()
                        },
                        onBack = { viewModel.startSearch() }
                    )

                    DiscoveryState.Error -> ErrorContent(onRetry = { viewModel.startSearch() })
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Manual IP entry toggle (only when searching)
            if (state == DiscoveryState.Searching) {
                TextButton(onClick = { viewModel.showManualEntry() }) {
                    Text(
                        text = "Enter IP manually",
                        color = Color(0xFFBB86FC),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchingContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            color = Color(0xFFBB86FC),
            modifier = Modifier.size(48.dp),
            strokeWidth = 3.dp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Searching for SonicWave\non your network...",
            color = Color(0xFFCCCCCC),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Make sure SonicWave is open on your phone",
            color = Color(0xFF666666),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FoundContent(
    host: String,
    port: Int,
    connecting: Boolean,
    onConnect: () -> Unit,
    onSearchAgain: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E1E2E),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFFBB86FC),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "SonicWave Found",
                    color = Color(0xFFFFFFFF),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$host:$port",
                    color = Color(0xFF888888),
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onConnect,
            enabled = !connecting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC))
        ) {
            if (connecting) {
                CircularProgressIndicator(
                    color = Color(0xFF000000),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = "Connect",
                    color = Color(0xFF000000),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onSearchAgain, enabled = !connecting) {
            Text(
                text = "Search again",
                color = Color(0xFF888888),
                fontSize = 13.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualEntryContent(
    ip: String,
    onIpChange: (String) -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Enter SonicWave IP",
            color = Color(0xFFCCCCCC),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Find the IP in SonicWave Settings → Servers",
            color = Color(0xFF666666),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = ip,
            onValueChange = onIpChange,
            placeholder = { Text("192.168.1.x", color = Color(0xFF555555)) },
            label = { Text("IP Address", color = Color(0xFF888888)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onConnect() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFBB86FC),
                unfocusedBorderColor = Color(0xFF444444),
                focusedTextColor = Color(0xFFFFFFFF),
                unfocusedTextColor = Color(0xFFCCCCCC),
                cursorColor = Color(0xFFBB86FC)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onConnect,
            enabled = ip.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC))
        ) {
            Text(
                text = "Connect",
                color = Color(0xFF000000),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onBack) {
            Text("Back to search", color = Color(0xFF888888), fontSize = 13.sp)
        }
    }
}

@Composable
private fun ErrorContent(onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Could not find SonicWave",
            color = Color(0xFFCF6679),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Make sure you are on the same WiFi network",
            color = Color(0xFF888888),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC))
        ) {
            Text("Try again", color = Color(0xFF000000), fontWeight = FontWeight.Bold)
        }
    }
}
