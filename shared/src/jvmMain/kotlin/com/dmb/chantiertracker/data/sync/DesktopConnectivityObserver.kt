package com.dmb.chantiertracker.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Desktop has no OS-level connectivity callback we can rely on across
 * platforms, so we poll: a short TCP connect to a well-known host tells us
 * whether the machine can currently reach the internet. Cheap, and the sync
 * loop only needs coarse online/offline transitions.
 */
class DesktopConnectivityObserver(
    scope: CoroutineScope,
    private val probeHost: String = "1.1.1.1",
    private val probePort: Int = 443,
    private val probeTimeoutMillis: Int = 2_000,
    private val interval: Duration = 15.seconds,
) : ConnectivityObserver {

    private val _online = MutableStateFlow(true)
    override val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                _online.value = probe()
                delay(interval)
            }
        }
    }

    override suspend fun isOnline(): Boolean = withContext(Dispatchers.IO) { probe() }

    private fun probe(): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(probeHost, probePort), probeTimeoutMillis)
            true
        }
    }.getOrDefault(false)
}
