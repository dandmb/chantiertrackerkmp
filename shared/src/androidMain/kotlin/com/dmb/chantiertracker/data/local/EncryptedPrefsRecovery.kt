package com.dmb.chantiertracker.data.local

import java.io.IOException
import java.security.GeneralSecurityException

internal fun <T> openWithKeystoreRecovery(
    open: () -> T,
    onCorruptedState: () -> Unit,
): T =
    try {
        open()
    } catch (firstAttempt: GeneralSecurityException) {
        onCorruptedState()
        open()
    } catch (firstAttempt: IOException) {
        onCorruptedState()
        open()
    }
