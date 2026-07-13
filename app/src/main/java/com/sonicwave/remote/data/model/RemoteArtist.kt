package com.sonicwave.remote.data.model

/** An artist as listed by the phone's /api/artists endpoint. */
data class RemoteArtist(
    val name: String,
    val songCount: Int,
    val albumCount: Int
)
