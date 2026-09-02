package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.sync.Clock
import com.dmb.chantiertracker.data.sync.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MutableClock(private var value: Long = 1_000_000L) : Clock {
    override fun nowEpochMillis(): Long = value

    fun set(millis: Long) {
        value = millis
    }

    fun advanceBy(millis: Long) {
        value += millis
    }
}

class FakeConnectivityObserver(initiallyOnline: Boolean = true) : ConnectivityObserver {
    private val _online = MutableStateFlow(initiallyOnline)
    override val online: StateFlow<Boolean> = _online.asStateFlow()

    override suspend fun isOnline(): Boolean = _online.value

    fun setOnline(online: Boolean) {
        _online.value = online
    }
}
