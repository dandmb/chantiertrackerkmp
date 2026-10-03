package com.dmb.chantiertracker.presentation.billing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.BillingAvailability
import com.dmb.chantiertracker.domain.repository.BillingRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// Defaults to UNKNOWN, so anything composed outside BillingAvailabilityProvider
// (a preview, an isolated screen test) hides paid tiers rather than showing them.
val LocalBillingAvailability = compositionLocalOf { BillingAvailability.UNKNOWN }

/**
 * The single question every screen asks before showing a price, a "subscribe"
 * action or an "upgrade to a higher tier" sentence (ADR-66). Only a confirmed
 * `OPEN` answers yes — `CLOSED` and `UNKNOWN` are indistinguishable on screen.
 */
@Composable
fun paidPlansAreOffered(): Boolean = LocalBillingAvailability.current == BillingAvailability.OPEN

// Re-asks the server each time the app comes back to the foreground (the very
// first ON_RESUME included): the switch is flipped by hand by a super-admin, and
// nothing is persisted that could otherwise be trusted across a relaunch.
@Composable
fun BillingAvailabilityProvider(
    billingRepository: BillingRepository = koinInject(),
    content: @Composable () -> Unit,
) {
    val availability by billingRepository.availability.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { billingRepository.refreshAvailability() }
    }
    CompositionLocalProvider(LocalBillingAvailability provides availability, content = content)
}
