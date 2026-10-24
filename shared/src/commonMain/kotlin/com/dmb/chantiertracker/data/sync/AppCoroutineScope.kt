package com.dmb.chantiertracker.data.sync

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

/**
 * Application-lifetime scope for background sync work — deliberately not tied
 * to any screen or ViewModel. One failing job never cancels the others
 * (`SupervisorJob`).
 */
class AppCoroutineScope(dispatcher: CoroutineDispatcher = Dispatchers.Default) : CoroutineScope {
    override val coroutineContext: CoroutineContext = SupervisorJob() + dispatcher
}
