package com.sonicwave.remote.data.model

/**
 * A song entry in the remote queue.
 */
data class RemoteSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val artUrl: String
)
