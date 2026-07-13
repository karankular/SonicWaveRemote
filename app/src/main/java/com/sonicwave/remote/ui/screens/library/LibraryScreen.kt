package com.sonicwave.remote.ui.screens.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sonicwave.remote.data.model.RemoteAlbum
import com.sonicwave.remote.data.model.RemoteArtist
import com.sonicwave.remote.data.model.RemotePlaylist
import com.sonicwave.remote.data.model.RemoteSong
import com.sonicwave.remote.ui.screens.remote.RemoteViewModel
import com.sonicwave.remote.ui.screens.search.SongOverflowMenu

private val Background = Color(0xFF121212)
private val Surface1 = Color(0xFF1E1E2E)
private val Surface2 = Color(0xFF2A2A3E)
private val Primary = Color(0xFFBB86FC)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF888888)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: RemoteViewModel) {
    val tabs = listOf("Favorites", "Recent", "Playlists", "Albums", "Artists")
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedPlaylist by remember { mutableStateOf<RemotePlaylist?>(null) }
    var selectedAlbum by remember { mutableStateOf<RemoteAlbum?>(null) }
    var selectedArtist by remember { mutableStateOf<RemoteArtist?>(null) }

    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlistSongs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val browseSongs by viewModel.browseSongs.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLibraryLoading.collectAsStateWithLifecycle()

    // Load data when tab changes
    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            0 -> viewModel.loadFavorites()
            1 -> viewModel.loadRecent()
            2 -> viewModel.loadPlaylists()
            3 -> viewModel.loadAlbums()
            4 -> viewModel.loadArtists()
        }
    }

    // ── Drill-down view (playlist / album / artist → its song list) ──────────
    val drillTitle = selectedPlaylist?.name ?: selectedAlbum?.name ?: selectedArtist?.name
    if (drillTitle != null) {
        val songs = if (selectedPlaylist != null) playlistSongs else browseSongs
        Column(
            modifier = Modifier.fillMaxSize().background(Background).statusBarsPadding()
        ) {
            Text(
                text = drillTitle,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedPlaylist = null; selectedAlbum = null; selectedArtist = null
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back", color = Primary, fontSize = 14.sp)
            }
            HorizontalDivider(color = Surface2)

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                SongList(
                    songs = songs,
                    onSongClick = { song -> viewModel.playInQueue(song.id) },
                    onPlayNext = { song -> viewModel.playNext(song.id) },
                    onAddToQueue = { song -> viewModel.addToQueue(song.id) },
                    onPlayList = { shuffle -> viewModel.playList(songs.map { it.id }, shuffle) }
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Background).statusBarsPadding()
    ) {
        Text(
            text = "Library",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Surface1,
            contentColor = Primary,
            edgePadding = 12.dp
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            color = if (selectedTab == index) Primary else TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            when (selectedTab) {
                0 -> SongList(
                    songs = favorites,
                    emptyText = "No favorites yet",
                    onSongClick = { song -> viewModel.playInQueue(song.id) },
                    onPlayNext = { song -> viewModel.playNext(song.id) },
                    onAddToQueue = { song -> viewModel.addToQueue(song.id) },
                    onPlayList = { shuffle -> viewModel.playList(favorites.map { it.id }, shuffle) }
                )
                1 -> SongList(
                    songs = recent,
                    emptyText = "No recently added songs",
                    onSongClick = { song -> viewModel.playInQueue(song.id) },
                    onPlayNext = { song -> viewModel.playNext(song.id) },
                    onAddToQueue = { song -> viewModel.addToQueue(song.id) },
                    onPlayList = { shuffle -> viewModel.playList(recent.map { it.id }, shuffle) }
                )
                2 -> PlaylistList(
                    playlists = playlists,
                    emptyText = "No playlists",
                    onPlaylistClick = { playlist ->
                        selectedPlaylist = playlist
                        viewModel.loadPlaylistSongs(playlist.id)
                    }
                )
                3 -> AlbumGrid(
                    albums = albums,
                    onAlbumClick = { album ->
                        selectedAlbum = album
                        viewModel.loadAlbumSongs(album.name)
                    }
                )
                4 -> ArtistList(
                    artists = artists,
                    onArtistClick = { artist ->
                        selectedArtist = artist
                        viewModel.loadArtistSongs(artist.name)
                    }
                )
            }
        }
    }
}

@Composable
private fun SongList(
    songs: List<RemoteSong>,
    emptyText: String = "Nothing here yet",
    onSongClick: (RemoteSong) -> Unit,
    onPlayNext: (RemoteSong) -> Unit = {},
    onAddToQueue: (RemoteSong) -> Unit = {},
    onPlayList: (shuffle: Boolean) -> Unit = {}
) {
    if (songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = TextSecondary, fontSize = 15.sp)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                PlayAllHeader(
                    count = songs.size,
                    onPlayAll = { onPlayList(false) },
                    onShuffle = { onPlayList(true) }
                )
            }
            items(songs, key = { it.id }) { song ->
                LibrarySongRow(
                    song = song,
                    onClick = { onSongClick(song) },
                    onPlayNext = { onPlayNext(song) },
                    onAddToQueue = { onAddToQueue(song) }
                )
            }
            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun PlayAllHeader(count: Int, onPlayAll: () -> Unit, onShuffle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onPlayAll,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.Black)
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Play all", fontSize = 14.sp)
        }
        OutlinedButton(
            onClick = onShuffle,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Surface2),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
        ) {
            Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Shuffle", fontSize = 14.sp)
        }
    }
}

@Composable
private fun LibrarySongRow(
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

@Composable
private fun AlbumGrid(albums: List<RemoteAlbum>, onAlbumClick: (RemoteAlbum) -> Unit) {
    if (albums.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No albums", color = TextSecondary, fontSize = 15.sp)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(albums, key = { it.name + "|" + it.artist }) { album ->
            AlbumCard(album = album, onClick = { onAlbumClick(album) })
        }
        item { Spacer(modifier = Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun AlbumCard(album: RemoteAlbum, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        AsyncImage(
            model = album.artUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Surface2)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.name,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artist,
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtistList(artists: List<RemoteArtist>, onArtistClick: (RemoteArtist) -> Unit) {
    if (artists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No artists", color = TextSecondary, fontSize = 15.sp)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(artists, key = { it.name }) { artist ->
            ArtistRow(artist = artist, onClick = { onArtistClick(artist) })
        }
        item { Spacer(modifier = Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun ArtistRow(artist: RemoteArtist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = Primary, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${artist.songCount} song${if (artist.songCount != 1) "s" else ""}" +
                    if (artist.albumCount > 0) " · ${artist.albumCount} album${if (artist.albumCount != 1) "s" else ""}" else "",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PlaylistList(
    playlists: List<RemotePlaylist>,
    emptyText: String = "No playlists",
    onPlaylistClick: (RemotePlaylist) -> Unit
) {
    if (playlists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = TextSecondary, fontSize = 15.sp)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(playlists, key = { it.id }) { playlist ->
                PlaylistRow(playlist = playlist, onClick = { onPlaylistClick(playlist) })
            }
            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: RemotePlaylist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.QueueMusic,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${playlist.songCount} song${if (playlist.songCount != 1) "s" else ""}",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
