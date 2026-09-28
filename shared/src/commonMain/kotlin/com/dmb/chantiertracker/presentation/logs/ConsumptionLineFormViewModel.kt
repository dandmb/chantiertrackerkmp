package com.dmb.chantiertracker.presentation.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.CreateConsumptionLineInput
import com.dmb.chantiertracker.domain.model.MaterialStock
import com.dmb.chantiertracker.domain.model.UpdateConsumptionLineInput
import com.dmb.chantiertracker.domain.repository.ConsumptionLineRepository
import com.dmb.chantiertracker.domain.repository.MaterialRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

data class ConsumptionLineFormUiState(
    val isEdit: Boolean = false,
    val ready: Boolean = false,
    val stock: List<MaterialStock> = emptyList(),
    val alreadyUsedMaterialIds: List<String> = emptyList(),
    val selectedMaterialId: String? = null,
    val quantity: String = "",
    val editingLineQuantity: Double? = null,
    val quantityError: StringResource? = null,
    val isSubmitting: Boolean = false,
    val saved: Boolean = false,
    val isMissing: Boolean = false,
) {
    val selectedStock: MaterialStock? get() = stock.firstOrNull { it.materialLocalId == selectedMaterialId }

    /** Materials with stock left, minus those already consumed today (unless we're editing that very line). */
    val pickable: List<MaterialStock>
        get() = stock.filter { it.available > 0.0 && (it.materialLocalId == selectedMaterialId || it.materialLocalId !in alreadyUsedMaterialIds) }

    val ceiling: Double?
        get() = selectedMaterialId?.let { availableCeiling(stock, it, editingLineQuantity) }

    val exceedsStock: Boolean
        get() = ceiling?.let { (parseAmountOrNull(quantity) ?: 0.0) > it } ?: false

    // A material is picked and a quantity is entered that stays within stock.
    // The positive-number check still runs on submit and surfaces inline.
    val canSave: Boolean
        get() = selectedMaterialId != null && quantity.isNotBlank() && !exceedsStock
}

class ConsumptionLineFormViewModel(
    private val materialRepository: MaterialRepository,
    private val consumptionLineRepository: ConsumptionLineRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ConsumptionLineFormUiState())
    val state = _state.asStateFlow()

    private var entryLocalId: String? = null
    private var lineLocalId: String? = null
    private var loaded = false

    fun load(entryLocalId: String, projectLocalId: String, lineLocalId: String?) {
        if (loaded) return
        loaded = true
        this.entryLocalId = entryLocalId
        this.lineLocalId = lineLocalId
        _state.update { it.copy(isEdit = lineLocalId != null) }

        viewModelScope.launch {
            combine(
                materialRepository.observeStock(projectLocalId),
                consumptionLineRepository.observeLines(entryLocalId),
            ) { stock, lines -> stock to lines }.collect { (stock, lines) ->
                val editingLine = lineLocalId?.let { id -> lines.firstOrNull { it.localId == id } }
                if (lineLocalId != null && editingLine == null && !_state.value.ready) {
                    _state.update { it.copy(isMissing = true) }
                    return@collect
                }
                _state.update { s ->
                    s.copy(
                        stock = stock,
                        alreadyUsedMaterialIds = lines.map { it.materialLocalId },
                        ready = true,
                        isMissing = false,
                        selectedMaterialId = if (!s.ready) editingLine?.materialLocalId ?: s.selectedMaterialId else s.selectedMaterialId,
                        quantity = if (!s.ready && editingLine != null) toInputText(editingLine.quantity) else s.quantity,
                        editingLineQuantity = editingLine?.quantity,
                    )
                }
            }
        }
    }

    fun selectMaterial(materialLocalId: String) = _state.update { it.copy(selectedMaterialId = materialLocalId, quantityError = null) }
    fun onQuantityChange(value: String) = _state.update { it.copy(quantity = value, quantityError = null) }

    fun submit() {
        val entryId = entryLocalId ?: return
        val current = _state.value
        val qError = validateRequiredQuantity(current.quantity)
        if (current.selectedMaterialId == null || qError != null || current.exceedsStock) {
            _state.update { it.copy(quantityError = qError) }
            return
        }
        val quantity = parseAmountOrNull(current.quantity)!!
        val materialId = current.selectedMaterialId
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            if (current.isEdit) {
                lineLocalId?.let { consumptionLineRepository.updateLine(it, UpdateConsumptionLineInput(quantity)) }
            } else {
                consumptionLineRepository.createLine(entryId, CreateConsumptionLineInput(materialId, quantity))
            }
            _state.update { it.copy(isSubmitting = false, saved = true) }
        }
    }
}
