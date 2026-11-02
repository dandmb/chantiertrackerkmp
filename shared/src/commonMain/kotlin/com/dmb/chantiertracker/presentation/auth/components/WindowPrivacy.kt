package com.dmb.chantiertracker.presentation.auth.components

import androidx.compose.runtime.Composable

@Composable
expect fun KeepWindowPrivate()

class WindowPrivacyHolds(private val setPrivate: (Boolean) -> Unit) {
    private var holds = 0

    fun acquire() {
        if (holds++ == 0) setPrivate(true)
    }

    fun release() {
        if (--holds == 0) setPrivate(false)
    }
}
