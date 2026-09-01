package com.dmb.chantiertracker.presentation.hello

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.WelcomeMessage
import com.dmb.chantiertracker.domain.usecase.GetWelcomeMessageUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HelloUiState {
    data object Loading : HelloUiState
    data class Content(val message: WelcomeMessage) : HelloUiState
}

class HelloViewModel(
    private val getWelcomeMessage: GetWelcomeMessageUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HelloUiState>(HelloUiState.Loading)
    val uiState: StateFlow<HelloUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = HelloUiState.Content(getWelcomeMessage())
        }
    }
}
