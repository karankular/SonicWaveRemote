package com.sonicwave.remote.ui.screens.discovery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonicwave.remote.data.HandshakeResult
import com.sonicwave.remote.data.RemoteApi
import com.sonicwave.remote.discovery.NsdDiscovery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class DiscoveryState { Searching, Found, ManualEntry, Error }

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val nsdDiscovery: NsdDiscovery,
    private val remoteApi: RemoteApi
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoveryState.Searching)
    val state: StateFlow<DiscoveryState> = _state

    private val _foundDevice = MutableStateFlow<Pair<String, Int>?>(null)
    val foundDevice: StateFlow<Pair<String, Int>?> = _foundDevice

    /** True while the post-tap-Connect version handshake is in flight. */
    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting

    /** Non-null when the handshake found this app too old for the server — shown as a dialog. */
    private val _updateRequired = MutableStateFlow<HandshakeResult.Incompatible?>(null)
    val updateRequired: StateFlow<HandshakeResult.Incompatible?> = _updateRequired

    var manualIp by mutableStateOf("")

    init {
        startSearch()
    }

    fun startSearch() {
        _state.value = DiscoveryState.Searching
        nsdDiscovery.startDiscovery { host, port ->
            _foundDevice.value = host to port
            _state.value = DiscoveryState.Found
        }
    }

    fun connectManual() {
        if (manualIp.isNotBlank()) {
            val (host, port) = nsdDiscovery.setManualHost(manualIp.trim())
            _foundDevice.value = host to port
            _state.value = DiscoveryState.Found
        }
    }

    fun showManualEntry() {
        _state.value = DiscoveryState.ManualEntry
    }

    fun dismissUpdateRequired() {
        _updateRequired.value = null
    }

    /**
     * Called on the user tapping "Connect". Checks version compatibility with the server FIRST
     * (see RemoteApi.checkHandshake) — only calls [onCompatible] (which the screen wires to actually
     * navigate into the remote UI) if the server accepts this app's declared protocol version, or the
     * check couldn't be completed at all (fails OPEN on a network error / older server, never blocks
     * a working connection because of a hiccup unrelated to version compatibility).
     */
    fun connect(host: String, port: Int, onCompatible: () -> Unit) {
        _connecting.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                remoteApi.checkHandshake("http://$host:$port")
            }
            _connecting.value = false
            when (result) {
                is HandshakeResult.Incompatible -> _updateRequired.value = result
                HandshakeResult.Compatible, HandshakeResult.Unreachable -> onCompatible()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        nsdDiscovery.stopDiscovery()
    }
}
