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
    /**
     * Identity of the queue's content AND order on the phone.
     *
     * The size cannot stand in for this: a reorder, or a replacement with a list of the same
     * length, leaves it unchanged while the list is completely different. Defaulted so a phone on
     * an older build -- which sends no revision -- keeps parsing.
     */
    val queueRevision: Int = 0,
    val isFavorite: Boolean = false,
    val volume: Int = 0,       // current level of whichever control moves the sound
    val volumeMax: Int = 1,    // its maximum (>=1)
    // Format of the SOURCE file. Defaulted so an older phone build, which does not send these,
    // simply shows nothing rather than failing to parse the whole state.
    val sampleRate: Int = 0,
    val codec: String = "",
    val bitrate: Int = 0,
    val isLossless: Boolean = false,
    /**
     * Auto Play: whether the phone will continue into the rest of its library once the queue
     * above ends, and how many songs are in that continuation right now. Defaulted so an older
     * phone build (which sends neither field) just never shows the section, same as every other
     * field a newer client can't assume yet.
     */
    val autoPlayEnabled: Boolean = false,
    val autoPlaySize: Int = 0,
) {

    /**
     * The one-line format summary, or null when the phone told us nothing useful.
     *
     * Built here so the screen has no formatting logic and cannot disagree with itself. Only the
     * parts actually known are shown: a missing rate or codec is left out rather than printed as a
     * zero, because an invented figure on a screen people use to check the format is worse than an
     * absent one.
     */
    val formatSummary: String?
        get() = listOfNotNull(
            codec.takeIf { it.isNotBlank() },
            sampleRate.takeIf { it > 0 }?.let { formatKhz(it) },
            bitrate.takeIf { it > 0 }?.let { "$it kbps" },
        ).joinToString(" · ").takeIf { it.isNotBlank() }

    private fun formatKhz(hz: Int): String {
        val khz = hz / 1000.0
        return if (khz % 1.0 == 0.0) "${khz.toInt()} kHz" else String.format("%.1f kHz", khz)
    }
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
