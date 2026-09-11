package com.dmb.chantiertracker.presentation.billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.BillingCycle
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.PlanUsage
import com.dmb.chantiertracker.domain.repository.AccountRepository
import com.dmb.chantiertracker.domain.repository.BillingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BillingUiState(
    val planUsage: PlanUsage? = null,
    val isProcessingAction: Boolean = false,
    val actionError: DomainException? = null,
)

// planUsage comes from AccountRepository — offline-first (ADR-25/49), last
// known value visible immediately, refreshed best-effort on open. The two
// actions go through BillingRepository — online only (ADR-49): each one
// fetches a fresh, single-use Stripe URL and hands it to UrlOpener, never
// cached, never retried automatically.
class BillingViewModel(
    private val accountRepository: AccountRepository,
    private val billingRepository: BillingRepository,
    private val urlOpener: UrlOpener,
) : ViewModel() {

    private val _state = MutableStateFlow(BillingUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepository.observePlanUsage().collect { usage ->
                _state.update { it.copy(planUsage = usage) }
            }
        }
        viewModelScope.launch { accountRepository.refreshPlanUsage() }
    }

    fun startCheckout(plan: Plan, billingCycle: BillingCycle) {
        runAction { billingRepository.startCheckout(plan, billingCycle) }
    }

    fun openManageSubscription() {
        runAction { billingRepository.openManageSubscription() }
    }

    fun clearActionError() = _state.update { it.copy(actionError = null) }

    private fun runAction(fetchUrl: suspend () -> String) {
        if (_state.value.isProcessingAction) return
        _state.update { it.copy(isProcessingAction = true, actionError = null) }
        viewModelScope.launch {
            try {
                val url = fetchUrl()
                urlOpener.open(url)
                _state.update { it.copy(isProcessingAction = false) }
            } catch (e: DomainException) {
                _state.update { it.copy(isProcessingAction = false, actionError = e) }
            } catch (e: Throwable) {
                _state.update { it.copy(isProcessingAction = false, actionError = DomainException.Unexpected) }
            }
        }
    }
}
