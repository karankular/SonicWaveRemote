package com.sonicwave.remote.data.model

/** An album as listed by the phone's /api/albums endpoint. */
data class RemoteAlbum(
    val name: String,
    val artist: String,
    val songCount: Int,
    val year: Int,
    val artUrl: String
)
