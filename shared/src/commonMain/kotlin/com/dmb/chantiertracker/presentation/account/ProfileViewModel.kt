package com.dmb.chantiertracker.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.ProfileField
import com.dmb.chantiertracker.domain.model.ProfileLanguage
import com.dmb.chantiertracker.domain.model.ProfileUpdate
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.profile_language_invalid
import com.dmb.chantiertracker.resources.profile_name_invalid
import com.dmb.chantiertracker.resources.profile_phone_invalid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

data class ProfileUiState(
    val loaded: Boolean = false,
    val email: String = "",
    val name: String = "",
    val phone: String = "",
    val language: ProfileLanguage = ProfileLanguage.FRENCH,
    val nameError: StringResource? = null,
    val phoneError: StringResource? = null,
    val languageError: StringResource? = null,
    val formError: DomainException? = null,
    val isSubmitting: Boolean = false,
    val saved: Boolean = false,
)

private const val MAX_NAME_LENGTH = 255
private val VALID_PHONE = Regex("^\\+?[0-9][0-9 .()-]{4,27}[0-9]$")

class ProfileViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.authState.collect { authState ->
                if (authState is AuthState.Authenticated && !_state.value.loaded) prefill(authState.user)
            }
        }
    }

    private fun prefill(user: User) = _state.update {
        it.copy(
            loaded = true,
            email = user.email,
            name = user.name,
            phone = user.phone.orEmpty(),
            language = user.language ?: ProfileLanguage.FRENCH,
        )
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null, formError = null) }

    fun onPhoneChange(value: String) = _state.update { it.copy(phone = value, phoneError = null, formError = null) }

    fun onLanguageChange(value: ProfileLanguage) = _state.update { it.copy(language = value, languageError = null, formError = null) }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return
        val name = current.name.trim()
        val phone = current.phone.trim()
        val nameError = Res.string.profile_name_invalid.takeIf { name.isEmpty() || name.length > MAX_NAME_LENGTH }
        val phoneError = Res.string.profile_phone_invalid.takeIf { phone.isNotEmpty() && !VALID_PHONE.matches(phone) }
        if (nameError != null || phoneError != null) {
            _state.update { it.copy(nameError = nameError, phoneError = phoneError) }
            return
        }
        _state.update { it.copy(isSubmitting = true, formError = null) }
        viewModelScope.launch {
            try {
                authRepository.updateProfile(ProfileUpdate(name, phone, current.language))
                _state.update { it.copy(isSubmitting = false, saved = true) }
            } catch (e: DomainException.InvalidProfile) {
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        nameError = Res.string.profile_name_invalid.takeIf { ProfileField.NAME in e.fields },
                        phoneError = Res.string.profile_phone_invalid.takeIf { ProfileField.PHONE in e.fields },
                        languageError = Res.string.profile_language_invalid.takeIf { ProfileField.LANGUAGE in e.fields },
                    )
                }
            } catch (e: DomainException) {
                _state.update { it.copy(isSubmitting = false, formError = e) }
            }
        }
    }
}
