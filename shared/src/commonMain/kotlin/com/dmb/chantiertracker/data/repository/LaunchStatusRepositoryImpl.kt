package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.remote.BillingApi
import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.data.remote.dto.FounderOfferDto
import com.dmb.chantiertracker.domain.model.BillingAvailability
import com.dmb.chantiertracker.domain.model.FounderOffer
import com.dmb.chantiertracker.domain.repository.LaunchStatusRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class LaunchStatusRepositoryImpl(private val api: BillingApi) : LaunchStatusRepository {

    private val _billingStatus = MutableStateFlow(BillingAvailability.UNKNOWN)
    override val billingStatus = _billingStatus.asStateFlow()

    private val _founderOffer = MutableStateFlow<FounderOffer?>(null)
    override val founderOffer = _founderOffer.asStateFlow()

    override suspend fun refresh() {
        val offer = attempt { apiCall { api.founderOffer() } }
        _founderOffer.value = offer?.takeUnless { it.billingOpen }?.toFounderOffer()
        // Without the offer, /billing/status still tells which way the switch is: no figure shown.
        val open = offer?.billingOpen ?: attempt { apiCall { api.status() } }?.billingOpen
        _billingStatus.value = when (open) {
            true -> BillingAvailability.OPEN
            false -> BillingAvailability.CLOSED
            null -> BillingAvailability.UNKNOWN
        }
    }

    private suspend fun <T> attempt(call: suspend () -> T): T? =
        try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
}

private fun FounderOfferDto.toFounderOffer() = FounderOffer(
    projects = limits?.projects,
    photos = limits?.photos,
    videos = limits?.videos,
    videoMaxSeconds = limits?.videoMaxSeconds,
    supervisorsPerProject = limits?.supervisorsPerProject,
    pdfExport = limits?.pdfExport == true,
    historyDays = limits?.historyDays,
)
