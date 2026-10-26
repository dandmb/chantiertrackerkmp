package com.dmb.chantiertracker.data.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SessionNotice { AccountDeleted }

// What the sign-in screen tells the user once a session has ended for a reason of its own.
class SessionNotices {
    private val _notice = MutableStateFlow<SessionNotice?>(null)
    val notice: StateFlow<SessionNotice?> = _notice.asStateFlow()

    fun publish(notice: SessionNotice) {
        _notice.value = notice
    }

    fun clear() {
        _notice.value = null
    }
}
