package com.sonicwave.remote.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sonicwave.remote.data.model.RemoteAlbum
import com.sonicwave.remote.data.model.RemoteArtist
import com.sonicwave.remote.data.model.RemotePlaybackState
import com.sonicwave.remote.data.model.RemotePlaylist
import com.sonicwave.remote.data.model.RemoteSong
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The result of checking compatibility with the main app's server on Connect. See [RemoteApi]'s
 * [RemoteApi.REMOTE_PROTOCOL_VERSION] KDoc for why this exists (a future forced-migration lever once
 * this app ships on the Play Store).
 */
sealed interface HandshakeResult {
    /** Server accepts this remote's declared protocol version — safe to proceed. */
    data object Compatible : HandshakeResult
    /** Server requires a newer remote than this app declares — block, show an update prompt. */
    data class Incompatible(val minRequired: Int, val declared: Int) : HandshakeResult
    /**
     * Handshake failed (network error, or an older main-app server that predates this endpoint).
     * Treated as "let the user try to connect anyway" — fails OPEN, not closed, so this check can
     * never itself break connecting to a main app that hasn't been updated yet.
     */
    data object Unreachable : HandshakeResult
}

/**
 * OkHttp-based REST client for communicating with SonicWave's RemoteControlServer.
 */
@Singleton
class RemoteApi @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Checks compatibility with the main app's server via GET /api/handshake — called once, when the
     * user taps Connect, BEFORE entering the remote UI (not on every request; this is a one-time gate,
     * not a per-call auth check). Blocking, like every other method here — call from a background
     * dispatcher.
     */
    fun checkHandshake(baseUrl: String): HandshakeResult {
        return try {
            val request = Request.Builder().url("$baseUrl/api/handshake").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return HandshakeResult.Unreachable
                val body = response.body?.string() ?: return HandshakeResult.Unreachable
                val obj = JsonParser.parseString(body).asJsonObject
                val minRequired = obj.get("minRemoteProtocolVersion")
                    ?.takeIf { !it.isJsonNull }?.asInt ?: return HandshakeResult.Unreachable
                if (REMOTE_PROTOCOL_VERSION < minRequired) {
                    HandshakeResult.Incompatible(minRequired = minRequired, declared = REMOTE_PROTOCOL_VERSION)
                } else {
                    HandshakeResult.Compatible
                }
            }
        } catch (e: Exception) {
            HandshakeResult.Unreachable
        }
    }

    /**
     * Fetches the current playback state from /api/state.
     * Returns null if the request fails or the response cannot be parsed.
     */
    fun getState(baseUrl: String): RemotePlaybackState? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/state")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                parseState(body)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetches the current queue from /api/queue.
     * Returns empty list on failure.
     */
    fun getQueue(baseUrl: String): List<RemoteSong> {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/queue")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                parseQueue(body)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Fetches favorited songs from /api/favorites.
     */
    fun getFavorites(baseUrl: String): List<RemoteSong> = fetchSongList("$baseUrl/api/favorites")

    /**
     * Fetches recently added songs from /api/recent.
     */
    fun getRecent(baseUrl: String): List<RemoteSong> = fetchSongList("$baseUrl/api/recent")

    /**
     * Fetches all playlists from /api/playlists.
     */
    fun getPlaylists(baseUrl: String): List<RemotePlaylist> {
        return try {
            val request = Request.Builder().url("$baseUrl/api/playlists").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val array = JsonParser.parseString(body).asJsonArray
                array.mapNotNull { element ->
                    try {
                        val obj = element.asJsonObject
                        RemotePlaylist(
                            id = obj.getLong("id"),
                            name = obj.getString("name"),
                            songCount = obj.getInt("songCount")
                        )
                    } catch (_: Exception) { null }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Fetches songs in a specific playlist from /api/playlist/{id}.
     */
    fun getPlaylistSongs(baseUrl: String, playlistId: Long): List<RemoteSong> =
        fetchSongList("$baseUrl/api/playlist/$playlistId")

    /** Fetches all albums from /api/albums. */
    fun getAlbums(baseUrl: String): List<RemoteAlbum> {
        return try {
            val request = Request.Builder().url("$baseUrl/api/albums").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                JsonParser.parseString(body).asJsonArray.mapNotNull { el ->
                    try {
                        val o = el.asJsonObject
                        RemoteAlbum(
                            name = o.getString("name"),
                            artist = o.getString("artist"),
                            songCount = o.getInt("songCount"),
                            year = o.getInt("year"),
                            artUrl = o.getString("artUrl")
                        )
                    } catch (_: Exception) { null }
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    /** Fetches all artists from /api/artists. */
    fun getArtists(baseUrl: String): List<RemoteArtist> {
        return try {
            val request = Request.Builder().url("$baseUrl/api/artists").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                JsonParser.parseString(body).asJsonArray.mapNotNull { el ->
                    try {
                        val o = el.asJsonObject
                        RemoteArtist(
                            name = o.getString("name"),
                            songCount = o.getInt("songCount"),
                            albumCount = o.getInt("albumCount")
                        )
                    } catch (_: Exception) { null }
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    /** Songs in an album via /api/album?name=... */
    fun getAlbumSongs(baseUrl: String, album: String): List<RemoteSong> =
        fetchSongList("$baseUrl/api/album?name=${java.net.URLEncoder.encode(album, "UTF-8")}")

    /** Songs by an artist via /api/artist?name=... */
    fun getArtistSongs(baseUrl: String, artist: String): List<RemoteSong> =
        fetchSongList("$baseUrl/api/artist?name=${java.net.URLEncoder.encode(artist, "UTF-8")}")

    /**
     * Searches songs matching [query] via /api/search?q=...
     */
    fun searchSongs(baseUrl: String, query: String): List<RemoteSong> {
        if (query.isBlank()) return emptyList()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return fetchSongList("$baseUrl/api/search?q=$encoded")
    }

    private fun fetchSongList(url: String): List<RemoteSong> {
        return try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                parseQueue(body)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Sends a control command to /api/control.
     * Returns true if the server accepted the command.
     */
    fun sendControl(
        baseUrl: String,
        action: String,
        position: Long? = null,
        songId: Long? = null,
        librarySongId: Long? = null,
        level: Int? = null
    ): Boolean {
        return try {
            val jsonBody = buildString {
                append("{\"action\":\"$action\"")
                if (position != null) append(",\"position\":$position")
                if (songId != null) append(",\"id\":$songId")
                if (librarySongId != null) append(",\"id\":$librarySongId")
                if (level != null) append(",\"level\":$level")
                append("}")
            }
            val requestBody = jsonBody.toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$baseUrl/api/control")
                .post(requestBody)
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Plays a whole list of library songs (replaces the queue), optionally shuffled.
     * Order of [ids] is preserved server-side.
     */
    fun playList(baseUrl: String, ids: List<Long>, shuffle: Boolean): Boolean {
        if (ids.isEmpty()) return false
        return try {
            val body = "{\"action\":\"play_list\",\"ids\":[${ids.joinToString(",")}],\"shuffle\":$shuffle}"
                .toRequestBody(jsonMediaType)
            val request = Request.Builder().url("$baseUrl/api/control").post(body).build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    // ── JSON parsers ──────────────────────────────────────────────────────

    fun parseState(json: String): RemotePlaybackState? {
        return try {
            val obj = JsonParser.parseString(json).asJsonObject
            RemotePlaybackState(
                songId = obj.getLong("songId"),
                title = obj.getString("title"),
                artist = obj.getString("artist"),
                album = obj.getString("album"),
                artUrl = obj.getString("artUrl"),
                isPlaying = obj.getBoolean("isPlaying"),
                position = obj.getLong("position"),
                duration = obj.getLong("duration"),
                positionTimestamp = obj.getLong("positionTimestamp"),
                shuffleEnabled = obj.getBoolean("shuffleEnabled"),
                repeatMode = obj.getString("repeatMode"),
                queueSize = obj.getInt("queueSize"),
                queueIndex = obj.getInt("queueIndex"),
                isFavorite = obj.getBoolean("isFavorite"),
                volume = obj.getInt("volume"),
                volumeMax = obj.get("volumeMax")?.takeIf { !it.isJsonNull }?.asInt?.coerceAtLeast(1) ?: 1
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseQueue(json: String): List<RemoteSong> {
        return try {
            val array = JsonParser.parseString(json).asJsonArray
            array.mapNotNull { element ->
                try {
                    val obj = element.asJsonObject
                    RemoteSong(
                        id = obj.getLong("id"),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        album = obj.getString("album"),
                        artUrl = obj.getString("artUrl")
                    )
                } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── JsonObject extension helpers ──────────────────────────────────────

    private fun JsonObject.getString(key: String): String =
        get(key)?.takeIf { !it.isJsonNull }?.asString ?: ""

    private fun JsonObject.getLong(key: String): Long =
        get(key)?.takeIf { !it.isJsonNull }?.asLong ?: 0L

    private fun JsonObject.getInt(key: String): Int =
        get(key)?.takeIf { !it.isJsonNull }?.asInt ?: 0

    private fun JsonObject.getBoolean(key: String): Boolean =
        get(key)?.takeIf { !it.isJsonNull }?.asBoolean ?: false

    companion object {
        /**
         * This remote app's own protocol version, checked against the main app's
         * minRemoteProtocolVersion on every Connect tap (see [checkHandshake]). Bump this whenever a
         * NEW remote app release (e.g. the eventual Play Store build) needs to declare a higher
         * version to satisfy a raised server requirement — see the main app's `RemoteProtocol.kt` KDoc
         * for the full "force sideloaded copies to update" migration story.
         */
        const val REMOTE_PROTOCOL_VERSION = 1
    }
}
