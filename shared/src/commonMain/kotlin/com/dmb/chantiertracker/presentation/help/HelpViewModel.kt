package com.dmb.chantiertracker.presentation.help

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.core.AppConfig
import com.dmb.chantiertracker.domain.repository.SupportContactRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HelpViewModel(
    private val mailer: SupportMailer,
    private val appConfig: AppConfig,
    supportContact: SupportContactRepository,
) : ViewModel() {

    val supportEmail: StateFlow<String?> = supportContact.observeEmail()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _noMailApp = MutableStateFlow(false)
    val noMailApp = _noMailApp.asStateFlow()

    val appVersion: String = listOf(appConfig.appVersion, appConfig.appBuild).filter { it.isNotBlank() }.joinToString(" / ")
    val deviceModel: String = appConfig.deviceModel
    val osVersion: String = appConfig.osVersion

    init {
        viewModelScope.launch { supportContact.refresh() }
    }

    fun report(subject: String, body: String) {
        val address = supportEmail.value ?: return
        _noMailApp.value = false
        viewModelScope.launch {
            try {
                mailer.compose(address, subject, body)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _noMailApp.value = true
            }
        }
    }
}
