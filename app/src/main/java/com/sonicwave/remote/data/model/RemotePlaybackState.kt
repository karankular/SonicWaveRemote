package com.sonicwave.remote.data.model

/**
 * Holds the playback state received from the SonicWave phone app via SSE or HTTP polling.
 *
 * [position] is the playback position in milliseconds at the time [positionTimestamp] was recorded.
 * [positionTimestamp] is System.currentTimeMillis() on the phone when the event was sent.
 * Use [extrapolatedPosition] to get a smooth current position estimate without polling.
 */
data class RemotePlaybackState(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val artUrl: String,
    val isPlaying: Boolean,
    val position: Long,
    val duration: Long,
    val positionTimestamp: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: String,   // "off" | "all" | "one"
    val queueSize: Int,
    val queueIndex: Int,
    val isFavorite: Boolean = false,
    val volume: Int = 0,       // current STREAM_MUSIC index
    val volumeMax: Int = 1     // STREAM_MUSIC max (>=1)
) {
    /** Current volume as a 0..1 fraction for the remote's slider. */
    val volumeFraction: Float
        get() = if (volumeMax > 0) volume.toFloat() / volumeMax else 0f

    /**
     * Extrapolates the current playback position based on elapsed time since [positionTimestamp].
     * When paused, returns [position] as-is.
     */
    fun extrapolatedPosition(now: Long = System.currentTimeMillis()): Long {
        if (!isPlaying) return position
        val elapsed = now - positionTimestamp
        return (position + elapsed).coerceIn(0, duration)
    }

    /** 0.0..1.0 progress using extrapolated position. */
    val progress: Float
        get() = if (duration > 0) extrapolatedPosition().toFloat() / duration else 0f
}
