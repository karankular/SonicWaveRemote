package com.sonicwave.remote.ui.screens.remote

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sonicwave.remote.data.model.RemotePlaybackState
import com.sonicwave.remote.data.model.RemoteSong

private val Background = Color(0xFF121212)
private val Surface1 = Color(0xFF1E1E2E)
private val Surface2 = Color(0xFF2A2A3E)
private val Primary = Color(0xFFBB86FC)
private val OnPrimary = Color(0xFF000000)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF888888)
private val TextDim = Color(0xFF555555)

/**
 * The Now Playing tab — receives the shared [viewModel] from [RemoteMainScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingTab(viewModel: RemoteViewModel) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var showQueue by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top bar
            TopBar(connectionState = connectionState)

            // Main content — weight-based so it always fits on ONE screen (no scroll).
            // The album art flexes to fill whatever vertical space is left after the
            // fixed controls, so the Queue button below is ALWAYS visible.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Album art — square, sized to the smaller of available width/height
                // (capped) so it shrinks on short screens instead of pushing content off.
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val side = minOf(maxWidth, maxHeight, 360.dp)
                    AlbumArtSection(artUrl = playbackState?.artUrl, size = side)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Song info
                SongInfoSection(state = playbackState)

                Spacer(modifier = Modifier.height(16.dp))

                // Progress bar
                val duration = playbackState?.duration ?: 0L
                val effectivePosition = if (isSeeking) {
                    (seekPosition * duration).toLong()
                } else {
                    currentPosition
                }
                val progress = if (duration > 0) effectivePosition.toFloat() / duration else 0f

                ProgressSection(
                    progress = progress,
                    currentPosition = effectivePosition,
                    duration = duration,
                    onSeekStart = { isSeeking = true },
                    onSeekChange = { seekPosition = it },
                    onSeekEnd = { fraction ->
                        // Send seek command immediately
                        viewModel.seek((fraction * duration).toLong())
                        // Keep isSeeking=true until the SSE round-trip confirms the
                        // new position (forcePush fires ~200ms after seek on server).
                        // Without this delay the slider snaps back before the update arrives.
                        scope.launch {
                            delay(450)
                            isSeeking = false
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Main controls
                ControlsRow(
                    state = playbackState,
                    onPlayPause = { viewModel.playPause() },
                    onNext = { viewModel.next() },
                    onPrevious = { viewModel.previous() },
                    onShuffle = { viewModel.toggleShuffle() },
                    onRepeat = { viewModel.cycleRepeat() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Favorite + volume row
                FavoriteVolumeRow(
                    isFavorite = playbackState?.isFavorite ?: false,
                    volumeFraction = playbackState?.volumeFraction ?: 0f,
                    enabled = playbackState != null,
                    onToggleFavorite = { viewModel.toggleFavorite() },
                    onSetVolume = { viewModel.setVolume(it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Queue button — always on screen now (no scrolling needed)
                OutlinedButton(
                    onClick = { showQueue = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Surface2),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Icon(
                        Icons.Filled.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val queueSize = playbackState?.queueSize ?: 0
                    Text(
                        text = if (queueSize > 0) "Queue · $queueSize songs" else "Queue",
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Reconnecting overlay
        AnimatedVisibility(
            visible = connectionState == RemoteViewModel.ConnectionState.Reconnecting,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Reconnecting...", color = TextPrimary, fontSize = 16.sp)
                }
            }
        }
    }

    // Queue bottom sheet
    if (showQueue) {
        QueueBottomSheet(
            queue = queue,
            currentIndex = playbackState?.queueIndex ?: 0,
            onDismiss = { showQueue = false },
            onSongClick = { songId -> viewModel.playSong(songId) }
        )
    }
}

@Composable
private fun TopBar(connectionState: RemoteViewModel.ConnectionState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "SonicWave Remote",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val dotColor = when (connectionState) {
                RemoteViewModel.ConnectionState.Connected -> Color(0xFF4CAF50)
                RemoteViewModel.ConnectionState.Reconnecting -> Color(0xFFFFB300)
                else -> Color(0xFF666666)
            }
            val statusText = when (connectionState) {
                RemoteViewModel.ConnectionState.Connected -> "Connected"
                RemoteViewModel.ConnectionState.Reconnecting -> "Reconnecting"
                RemoteViewModel.ConnectionState.Connecting -> "Connecting"
                RemoteViewModel.ConnectionState.Disconnected -> "Disconnected"
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(text = statusText, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AlbumArtSection(artUrl: String?, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1),
        contentAlignment = Alignment.Center
    ) {
        if (artUrl != null) {
            AsyncImage(
                model = artUrl,
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = TextDim,
                modifier = Modifier.size(80.dp)
            )
        }
    }
}

@Composable
private fun SongInfoSection(state: RemotePlaybackState?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = state?.title ?: "Not playing",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        val artistAlbum = if (state != null) {
            buildString {
                append(state.artist)
                if (state.album.isNotBlank()) append(" · ${state.album}")
            }
        } else ""
        Text(
            text = artistAlbum,
            color = TextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // The source format, when the phone reports one. Shown smaller than the artist line
        // because it is reference information rather than identity, and omitted entirely when
        // unknown -- an older phone build sends nothing, and a blank line reads as a fault.
        state?.formatSummary?.let { format ->
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (state.isLossless) {
                    Text(
                        text = "Lossless",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(TextSecondary.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = format,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProgressSection(
    progress: Float,
    currentPosition: Long,
    duration: Long,
    onSeekStart: () -> Unit,
    onSeekChange: (Float) -> Unit,
    onSeekEnd: (Float) -> Unit
) {
    var sliderValue by remember(progress) { mutableStateOf(progress) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = sliderValue,
            onValueChange = {
                onSeekStart()
                sliderValue = it
                onSeekChange(it)
            },
            onValueChangeFinished = {
                onSeekEnd(sliderValue)
            },
            colors = SliderDefaults.colors(
                thumbColor = Primary,
                activeTrackColor = Primary,
                inactiveTrackColor = Surface2
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatMs(currentPosition),
                color = TextSecondary,
                fontSize = 12.sp
            )
            Text(
                text = formatMs(duration),
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ControlsRow(
    state: RemotePlaybackState?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit
) {
    val shuffleActive = state?.shuffleEnabled ?: false
    val repeatMode = state?.repeatMode ?: "off"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle
        IconButton(onClick = onShuffle) {
            Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = "Shuffle",
                tint = if (shuffleActive) Primary else TextSecondary,
                modifier = Modifier.size(26.dp)
            )
        }

        // Previous
        IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Previous",
                tint = TextPrimary,
                modifier = Modifier.size(36.dp)
            )
        }

        // Play / Pause (large filled circle)
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(68.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Primary,
                contentColor = OnPrimary
            )
        ) {
            val isPlaying = state?.isPlaying ?: false
            Crossfade(targetState = isPlaying, label = "play_pause") { playing ->
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Next
        IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Next",
                tint = TextPrimary,
                modifier = Modifier.size(36.dp)
            )
        }

        // Repeat
        val repeatIcon: ImageVector = when (repeatMode) {
            "one" -> Icons.Filled.RepeatOne
            else -> Icons.Filled.Repeat
        }
        IconButton(onClick = onRepeat) {
            Icon(
                imageVector = repeatIcon,
                contentDescription = "Repeat",
                tint = if (repeatMode != "off") Primary else TextSecondary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun FavoriteVolumeRow(
    isFavorite: Boolean,
    volumeFraction: Float,
    enabled: Boolean,
    onToggleFavorite: () -> Unit,
    onSetVolume: (Int) -> Unit
) {
    // Local drag state so we only send the volume command on release (not every frame).
    var dragging by remember { mutableStateOf(false) }
    var localValue by remember { mutableStateOf(0f) }
    val value = if (dragging) localValue else volumeFraction.coerceIn(0f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onToggleFavorite, enabled = enabled) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) Color(0xFFE91E63) else TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
        Icon(Icons.Filled.VolumeDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Slider(
            value = value,
            onValueChange = { dragging = true; localValue = it },
            onValueChangeFinished = {
                onSetVolume((localValue * 100).toInt())
                dragging = false
            },
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Primary,
                activeTrackColor = Primary,
                inactiveTrackColor = Surface2
            ),
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueBottomSheet(
    queue: List<RemoteSong>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onSongClick: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    // Scroll to currently playing song when sheet opens
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentIndex < queue.size) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface1
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Queue · ${queue.size} songs",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider(color = Surface2)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
                itemsIndexed(queue) { index, song ->
                    val isCurrentSong = index == currentIndex
                    QueueSongRow(
                        song = song,
                        isCurrentSong = isCurrentSong,
                        onClick = {
                            onSongClick(song.id)
                            onDismiss()
                        }
                    )
                }
                item { Spacer(modifier = Modifier.navigationBarsPadding()) }
            }
        }
    }
}

@Composable
private fun QueueSongRow(
    song: RemoteSong,
    isCurrentSong: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrentSong) Color(0x22BB86FC) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.artUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Surface2)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isCurrentSong) Primary else TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (isCurrentSong) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isCurrentSong) {
            Icon(
                imageVector = Icons.Filled.VolumeUp,
                contentDescription = "Now playing",
                tint = Primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        "%d:%02d:%02d".format(hours, remainingMinutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
