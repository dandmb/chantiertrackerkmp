package com.dmb.chantiertracker.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.data.session.UnsyncedWriteCounter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExitPrompt(val unsentChanges: Int)

class ExitPromptViewModel(private val counter: UnsyncedWriteCounter) : ViewModel() {

    private val _prompt = MutableStateFlow<ExitPrompt?>(null)
    val prompt: StateFlow<ExitPrompt?> = _prompt.asStateFlow()

    fun request() {
        viewModelScope.launch {
            val unsent = try {
                counter.unsentByKind().total
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                0
            }
            _prompt.value = ExitPrompt(unsent)
        }
    }

    fun dismiss() {
        _prompt.value = null
    }
}
