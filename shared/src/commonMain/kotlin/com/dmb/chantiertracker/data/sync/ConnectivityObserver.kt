package com.dmb.chantiertracker.data.sync

import dev.jordond.connectivity.Connectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface ConnectivityObserver {
    val online: StateFlow<Boolean>
    suspend fun isOnline(): Boolean
}

/**
 * Wraps jordond/connectivity's native monitors (Android `ConnectivityManager`,
 * iOS `NWPathMonitor`). Desktop uses [DesktopConnectivityObserver] instead —
 * jordond publishes no JVM artefact for the device monitor.
 */
class NativeConnectivityObserver(
    private val connectivity: Connectivity,
    scope: CoroutineScope,
) : ConnectivityObserver {

    private val _online = MutableStateFlow(true)
    override val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        connectivity.start()
        scope.launch {
            _online.value = runCatching { connectivity.status().isConnected }.getOrDefault(true)
            connectivity.statusUpdates.collect { status ->
                _online.value = status.isConnected
            }
        }
    }

    override suspend fun isOnline(): Boolean =
        runCatching { connectivity.status().isConnected }.getOrDefault(_online.value)
}
