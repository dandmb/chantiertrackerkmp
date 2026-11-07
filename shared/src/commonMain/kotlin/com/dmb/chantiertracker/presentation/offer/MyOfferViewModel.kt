package com.dmb.chantiertracker.presentation.offer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.data.sync.ConnectivityObserver
import com.dmb.chantiertracker.domain.model.BillingAvailability
import com.dmb.chantiertracker.domain.model.FounderOffer
import com.dmb.chantiertracker.domain.model.PlanUsage
import com.dmb.chantiertracker.domain.repository.AccountRepository
import com.dmb.chantiertracker.domain.repository.LaunchStatusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class MyOfferUiState(
    val usage: PlanUsage? = null,
    val billingStatus: BillingAvailability = BillingAvailability.UNKNOWN,
    val online: Boolean = true,
    val founderOffer: FounderOffer? = null,
)

class MyOfferViewModel(
    private val accountRepository: AccountRepository,
    private val launchStatusRepository: LaunchStatusRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {

    private val _state = MutableStateFlow(MyOfferUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                accountRepository.observePlanUsage(),
                launchStatusRepository.billingStatus,
                connectivity.online,
                launchStatusRepository.founderOffer,
            ) { usage, status, online, founderOffer -> MyOfferUiState(usage, status, online, founderOffer) }
                .collect { _state.value = it }
        }
        viewModelScope.launch { accountRepository.refreshPlanUsage() }
        viewModelScope.launch { launchStatusRepository.refresh() }
    }
}
