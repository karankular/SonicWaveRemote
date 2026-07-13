package com.sonicwave.remote.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sonicwave.remote.data.model.RemoteSong
import com.sonicwave.remote.ui.screens.remote.RemoteViewModel

private val Background = Color(0xFF121212)
private val Surface1 = Color(0xFF1E1E2E)
private val Surface2 = Color(0xFF2A2A3E)
private val Primary = Color(0xFFBB86FC)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF888888)

@Composable
fun SearchScreen(viewModel: RemoteViewModel) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
    ) {
        Text(
            text = "Search",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        // Search field
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.onSearchQueryChange(it) },
            placeholder = { Text("Songs, artists, albums…", color = TextSecondary) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        viewModel.onSearchQueryChange("")
                        focusManager.clearFocus()
                    }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = TextSecondary)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = Surface2,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = Primary,
                focusedContainerColor = Surface1,
                unfocusedContainerColor = Surface1
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        when {
            isSearching -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            }
            query.isBlank() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Search your library", color = TextSecondary, fontSize = 15.sp)
                    }
                }
            }
            results.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No results for \"$query\"", color = TextSecondary, fontSize = 15.sp)
                }
            }
            else -> {
                Text(
                    text = "${results.size} result${if (results.size != 1) "s" else ""}",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(results, key = { it.id }) { song ->
                        SearchSongRow(
                            song = song,
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.playInQueue(song.id)   // play now, keep queue going after
                            },
                            onPlayNext = { viewModel.playNext(song.id) },
                            onAddToQueue = { viewModel.addToQueue(song.id) }
                        )
                    }
                    item { Spacer(modifier = Modifier.navigationBarsPadding()) }
                }
            }
        }
    }
}

@Composable
private fun SearchSongRow(
    song: RemoteSong,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.artUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Surface2)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(song.artist)
                    if (song.album.isNotBlank()) append(" · ${song.album}")
                },
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        SongOverflowMenu(onPlay = onClick, onPlayNext = onPlayNext, onAddToQueue = onAddToQueue)
    }
}

/** Three-dot menu shared by song rows: Play / Play next / Add to queue. */
@Composable
fun SongOverflowMenu(
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(Surface1)
        ) {
            DropdownMenuItem(text = { Text("Play", color = TextPrimary) },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, null, tint = TextSecondary) },
                onClick = { open = false; onPlay() })
            DropdownMenuItem(text = { Text("Play next", color = TextPrimary) },
                leadingIcon = { Icon(Icons.Filled.QueuePlayNext, null, tint = TextSecondary) },
                onClick = { open = false; onPlayNext() })
            DropdownMenuItem(text = { Text("Add to queue", color = TextPrimary) },
                leadingIcon = { Icon(Icons.Filled.AddToQueue, null, tint = TextSecondary) },
                onClick = { open = false; onAddToQueue() })
        }
    }
}
