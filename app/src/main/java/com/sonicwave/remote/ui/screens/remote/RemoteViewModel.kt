package com.sonicwave.remote.ui.screens.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonicwave.remote.data.RemoteApi
import com.sonicwave.remote.data.SseClient
import com.sonicwave.remote.data.model.RemoteAlbum
import com.sonicwave.remote.data.model.RemoteArtist
import com.sonicwave.remote.data.model.RemotePlaybackState
import com.sonicwave.remote.data.model.RemotePlaylist
import com.sonicwave.remote.data.model.RemoteSong
import com.sonicwave.remote.discovery.NsdDiscovery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.roundToInt
import javax.inject.Inject

@HiltViewModel
class RemoteViewModel @Inject constructor(
    private val remoteApi: RemoteApi,
    private val sseClient: SseClient,
    private val nsdDiscovery: NsdDiscovery
) : ViewModel() {

    private val _playbackState = MutableStateFlow<RemotePlaybackState?>(null)
    val playbackState: StateFlow<RemotePlaybackState?> = _playbackState

    private val _queue = MutableStateFlow<List<RemoteSong>>(emptyList())
    val queue: StateFlow<List<RemoteSong>> = _queue

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // ── Library state ─────────────────────────────────────────────────────

    private val _favorites = MutableStateFlow<List<RemoteSong>>(emptyList())
    val favorites: StateFlow<List<RemoteSong>> = _favorites

    private val _recent = MutableStateFlow<List<RemoteSong>>(emptyList())
    val recent: StateFlow<List<RemoteSong>> = _recent

    private val _playlists = MutableStateFlow<List<RemotePlaylist>>(emptyList())
    val playlists: StateFlow<List<RemotePlaylist>> = _playlists

    private val _playlistSongs = MutableStateFlow<List<RemoteSong>>(emptyList())
    val playlistSongs: StateFlow<List<RemoteSong>> = _playlistSongs

    private val _albums = MutableStateFlow<List<RemoteAlbum>>(emptyList())
    val albums: StateFlow<List<RemoteAlbum>> = _albums

    private val _artists = MutableStateFlow<List<RemoteArtist>>(emptyList())
    val artists: StateFlow<List<RemoteArtist>> = _artists

    /** Songs shown when drilling into an album or artist. */
    private val _browseSongs = MutableStateFlow<List<RemoteSong>>(emptyList())
    val browseSongs: StateFlow<List<RemoteSong>> = _browseSongs

    private val _isLibraryLoading = MutableStateFlow(false)
    val isLibraryLoading: StateFlow<Boolean> = _isLibraryLoading

    // ── Search state ──────────────────────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<RemoteSong>>(emptyList())
    val searchResults: StateFlow<List<RemoteSong>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private var searchJob: Job? = null

    /**
     * Smooth position that updates every 250ms using time extrapolation.
     * No polling of the phone required.
     */
    val currentPosition: StateFlow<Long> = flow {
        while (true) {
            emit(_playbackState.value?.extrapolatedPosition() ?: 0L)
            delay(250)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private var baseUrl: String = ""

    enum class ConnectionState { Connecting, Connected, Reconnecting, Disconnected }

    /**
     * Initiates connection to SonicWave at [host]:[port].
     * Fetches initial state via REST, then opens SSE stream for real-time updates.
     */
    fun connect(host: String, port: Int) {
        baseUrl = "http://$host:$port"
        _connectionState.value = ConnectionState.Connecting

        viewModelScope.launch {
            // Fetch initial state (best-effort — server may be idle with no song loaded)
            try {
                val state = withContext(Dispatchers.IO) { remoteApi.getState(baseUrl) }
                if (state != null) {
                    _playbackState.value = state
                    _connectionState.value = ConnectionState.Connected
                    fetchQueue()
                }
                // If null (idle), still proceed to SSE — onConnected will set Connected
            } catch (_: Exception) {
                // Network error on initial fetch — SSE will determine real status
            }

            // Poll every 3 s as a safety net in case an SSE event is missed
            startPolling()

            // Open SSE stream — this is the source of truth for connection status
            sseClient.connect(
                baseUrl = baseUrl,
                onConnected = {
                    _connectionState.value = ConnectionState.Connected
                },
                onState = { newState ->
                    val oldQueueSize = _playbackState.value?.queueSize ?: -1
                    _playbackState.value = newState
                    _connectionState.value = ConnectionState.Connected
                    if (newState.queueSize != oldQueueSize) {
                        viewModelScope.launch { fetchQueue() }
                    }
                },
                onDisconnect = {
                    _connectionState.value = ConnectionState.Reconnecting
                }
            )
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                delay(3_000)
                if (baseUrl.isEmpty()) continue
                try {
                    val state = withContext(Dispatchers.IO) { remoteApi.getState(baseUrl) }
                    if (state != null) {
                        val cur = _playbackState.value
                        // Always update if there is no current state, if the
                        // song / play-state changed, or if the polled state is
                        // at least as recent as the current one (avoids
                        // overwriting a fresher SSE-delivered update with a
                        // stale poll).
                        if (cur == null
                            || state.songId != cur.songId
                            || state.isPlaying != cur.isPlaying
                            || state.shuffleEnabled != cur.shuffleEnabled
                            || state.repeatMode != cur.repeatMode
                            || state.positionTimestamp >= cur.positionTimestamp
                        ) {
                            _playbackState.value = state
                        }
                    }
                } catch (_: Exception) { /* server unreachable — SSE will handle reconnect */ }
            }
        }
    }

    private suspend fun fetchQueue() {
        val q = withContext(Dispatchers.IO) { remoteApi.getQueue(baseUrl) }
        _queue.value = q
    }

    // ── Control commands ──────────────────────────────────────────────────

    fun playPause() = sendControl("play_pause")
    fun next() = sendControl("next")
    fun previous() = sendControl("prev")
    fun seek(posMs: Long) = sendControl("seek", position = posMs)
    fun toggleShuffle() = sendControl("shuffle")
    fun cycleRepeat() = sendControl("repeat")
    fun playSong(songId: Long) = sendControl("play_song", songId = songId)
    fun playLibrarySong(songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            remoteApi.sendControl(baseUrl, "play_library_song", librarySongId = songId)
        }
    }

    /** Set device media volume from a 0..100 percentage (optimistic local update for the slider). */
    fun setVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _playbackState.value?.let { st ->
            val max = st.volumeMax.coerceAtLeast(1)
            _playbackState.value = st.copy(volume = (clamped / 100f * max).roundToInt().coerceIn(0, max))
        }
        viewModelScope.launch(Dispatchers.IO) {
            remoteApi.sendControl(baseUrl, "volume", level = clamped)
        }
    }

    /** Toggle the favorite flag of the current song (optimistic local flip). */
    fun toggleFavorite() {
        _playbackState.value?.let { st -> _playbackState.value = st.copy(isFavorite = !st.isFavorite) }
        viewModelScope.launch(Dispatchers.IO) {
            remoteApi.sendControl(baseUrl, "toggle_favorite")
        }
    }

    /**
     * Play a library song NOW but keep the current queue going afterwards (insert + jump).
     * This is the default for tapping a search/library result — it no longer nukes the queue.
     */
    fun playInQueue(librarySongId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            remoteApi.sendControl(baseUrl, "play_in_queue", librarySongId = librarySongId)
        }
    }

    /** Play a whole list (favorites / playlist / recent) as a new queue, optionally shuffled. */
    fun playList(songIds: List<Long>, shuffle: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            remoteApi.playList(baseUrl, songIds, shuffle)
        }
    }

    /** Insert a library song right after the current track. */
    fun playNext(librarySongId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            remoteApi.sendControl(baseUrl, "play_next", librarySongId = librarySongId)
        }
    }

    /** Append a library song to the end of the queue. */
    fun addToQueue(librarySongId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            remoteApi.sendControl(baseUrl, "add_to_queue", librarySongId = librarySongId)
        }
    }

    private fun sendControl(action: String, position: Long? = null, songId: Long? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            remoteApi.sendControl(baseUrl, action, position, songId)
        }
    }

    // ── Library ───────────────────────────────────────────────────────────

    fun loadFavorites() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLibraryLoading.value = true
            _favorites.value = remoteApi.getFavorites(baseUrl)
            _isLibraryLoading.value = false
        }
    }

    fun loadRecent() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLibraryLoading.value = true
            _recent.value = remoteApi.getRecent(baseUrl)
            _isLibraryLoading.value = false
        }
    }

    fun loadPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLibraryLoading.value = true
            _playlists.value = remoteApi.getPlaylists(baseUrl)
            _isLibraryLoading.value = false
        }
    }

    fun loadPlaylistSongs(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLibraryLoading.value = true
            _playlistSongs.value = remoteApi.getPlaylistSongs(baseUrl, playlistId)
            _isLibraryLoading.value = false
        }
    }

    fun loadAlbums() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLibraryLoading.value = true
            _albums.value = remoteApi.getAlbums(baseUrl)
            _isLibraryLoading.value = false
        }
    }

    fun loadArtists() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLibraryLoading.value = true
            _artists.value = remoteApi.getArtists(baseUrl)
            _isLibraryLoading.value = false
        }
    }

    fun loadAlbumSongs(album: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLibraryLoading.value = true
            _browseSongs.value = remoteApi.getAlbumSongs(baseUrl, album)
            _isLibraryLoading.value = false
        }
    }

    fun loadArtistSongs(artist: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLibraryLoading.value = true
            _browseSongs.value = remoteApi.getArtistSongs(baseUrl, artist)
            _isLibraryLoading.value = false
        }
    }

    // ── Search ────────────────────────────────────────────────────────────

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _isSearching.value = true
            val results = withContext(Dispatchers.IO) { remoteApi.searchSongs(baseUrl, query) }
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        sseClient.disconnect()
    }
}
