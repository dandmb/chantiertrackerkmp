package com.dmb.chantiertracker.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.data.sync.ConnectivityObserver
import com.dmb.chantiertracker.domain.model.AccountDeletionPreview
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.repository.AccountDeletionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeleteAccountUiState(
    val online: Boolean = true,
    val loading: Boolean = false,
    val preview: AccountDeletionPreview? = null,
    val loadError: DomainException? = null,
    val password: String = "",
    val understood: Boolean = false,
    val submitting: Boolean = false,
    val wrongPassword: Boolean = false,
    val formError: DomainException? = null,
) {
    val canSubmit: Boolean
        get() = online && preview?.canDelete == true && understood && password.isNotBlank() && !submitting
}

// The preview first (what disappears, who loses access, what blocks it), then the password and
// an explicit checkbox. Online only: the account lives on the server. Once deleted, the session
// ends and the app goes back to the sign-in screen on its own.
class DeleteAccountViewModel(
    private val repository: AccountDeletionRepository,
    private val connectivity: ConnectivityObserver,
) : ViewModel() {

    private val _state = MutableStateFlow(DeleteAccountUiState())
    val state: StateFlow<DeleteAccountUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            connectivity.online.collect { online ->
                _state.update { it.copy(online = online) }
                if (online && _state.value.preview == null && !_state.value.loading) load()
            }
        }
    }

    fun load() {
        if (!_state.value.online) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = null) }
            try {
                val preview = repository.preview()
                _state.update { it.copy(loading = false, preview = preview) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DomainException) {
                _state.update { it.copy(loading = false, loadError = e) }
            }
        }
    }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, wrongPassword = false, formError = null) }

    fun onUnderstoodChange(value: Boolean) = _state.update { it.copy(understood = value) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, wrongPassword = false, formError = null) }
            try {
                repository.deleteAccount(current.password)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DomainException.InvalidCurrentPassword) {
                _state.update { it.copy(submitting = false, wrongPassword = true) }
            } catch (e: DomainException.AccountDeletionBlocked) {
                _state.update { it.copy(submitting = false, formError = e) }
                load()
            } catch (e: DomainException) {
                _state.update { it.copy(submitting = false, formError = e) }
            }
        }
    }
}
