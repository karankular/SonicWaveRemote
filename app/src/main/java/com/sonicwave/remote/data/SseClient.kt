package com.sonicwave.remote.data

import com.sonicwave.remote.data.model.RemotePlaybackState
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SSE client that connects to SonicWave's /api/events endpoint and delivers
 * real-time playback state updates to the companion UI.
 *
 * On disconnect, reconnects with exponential backoff: 1s → 2s → 4s → 8s → capped at 30s.
 */
@Singleton
class SseClient @Inject constructor(
    private val remoteApi: RemoteApi
) {
    private var eventSource: EventSource? = null
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentBaseUrl: String = ""
    private var onStateCallback: ((RemotePlaybackState) -> Unit)? = null
    private var onDisconnectCallback: (() -> Unit)? = null
    private var onConnectedCallback: (() -> Unit)? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)  // SSE keeps connection open indefinitely
        .build()

    private var reconnectDelaySec = 1L

    /**
     * Opens an SSE connection to [baseUrl]/api/events.
     *
     * [onState] is called on every incoming state event.
     * [onDisconnect] is called when the connection is lost (before reconnect attempts).
     */
    fun connect(
        baseUrl: String,
        onConnected: () -> Unit = {},
        onState: (RemotePlaybackState) -> Unit,
        onDisconnect: () -> Unit
    ) {
        currentBaseUrl = baseUrl
        onStateCallback = onState
        onDisconnectCallback = onDisconnect
        onConnectedCallback = onConnected
        reconnectDelaySec = 1L
        openConnection()
    }

    /** Closes the SSE connection and cancels any pending reconnect. */
    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        eventSource?.cancel()
        eventSource = null
        onStateCallback = null
        onDisconnectCallback = null
    }

    private fun openConnection() {
        eventSource?.cancel()
        val url = "$currentBaseUrl/api/events"
        val request = Request.Builder().url(url).build()
        eventSource = EventSources.createFactory(okHttpClient)
            .newEventSource(request, SseListener())
    }

    private inner class SseListener : EventSourceListener() {

        override fun onOpen(eventSource: EventSource, response: Response) {
            reconnectDelaySec = 1L
            onConnectedCallback?.invoke()
        }

        override fun onEvent(
            eventSource: EventSource,
            id: String?,
            type: String?,
            data: String
        ) {
            // Ignore SSE heartbeat comments (they arrive as empty data or ": keepalive")
            if (data.isBlank()) return

            val state = try {
                remoteApi.parseState(data)
            } catch (_: Exception) {
                null
            }
            if (state != null) {
                onStateCallback?.invoke(state)
            }
        }

        override fun onClosed(eventSource: EventSource) {
            onDisconnectCallback?.invoke()
            scheduleReconnect()
        }

        override fun onFailure(
            eventSource: EventSource,
            t: Throwable?,
            response: Response?
        ) {
            onDisconnectCallback?.invoke()
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (currentBaseUrl.isEmpty()) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = reconnectDelaySec * 1000L
            delay(delayMs)
            // Exponential backoff capped at 30 seconds
            reconnectDelaySec = (reconnectDelaySec * 2).coerceAtMost(30L)
            openConnection()
        }
    }
}
