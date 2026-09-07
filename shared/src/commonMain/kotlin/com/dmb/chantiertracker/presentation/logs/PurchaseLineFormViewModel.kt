package com.dmb.chantiertracker.presentation.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.CreatePurchaseLineInput
import com.dmb.chantiertracker.domain.model.Material
import com.dmb.chantiertracker.domain.model.UpdatePurchaseLineInput
import com.dmb.chantiertracker.domain.repository.MaterialRepository
import com.dmb.chantiertracker.domain.repository.PurchaseLineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

data class PurchaseLineFormUiState(
    val isEdit: Boolean = false,
    val ready: Boolean = false,
    val materials: List<Material> = emptyList(),
    val materialQuery: String = "",
    val selectedMaterialId: String? = null,
    val quantity: String = "",
    val unitPrice: String = "",
    val supplier: String = "",
    val creatingNewUnit: String? = null,
    val materialError: StringResource? = null,
    val quantityError: StringResource? = null,
    val unitPriceError: StringResource? = null,
    val isSubmitting: Boolean = false,
    val saved: Boolean = false,
    val isMissing: Boolean = false,
) {
    val selectedMaterial: Material? get() = materials.firstOrNull { it.localId == selectedMaterialId }
    val totalPrice: Double get() = (parseAmountOrNull(quantity) ?: 0.0) * (parseAmountOrNull(unitPrice) ?: 0.0)

    // Required fields are filled in — the Save button stays disabled until then.
    // Format / range checks (> 0, >= 0) still run on submit and surface inline.
    val canSave: Boolean
        get() = (isEdit || selectedMaterialId != null) && quantity.isNotBlank() && unitPrice.isNotBlank()

    /** Materials matching the query, unless one is already picked and the query still equals its name. */
    val suggestions: List<Material>
        get() = if (selectedMaterialId != null && selectedMaterial?.name == materialQuery.trim()) {
            emptyList()
        } else {
            materials.filter { it.name.contains(materialQuery.trim(), ignoreCase = true) }
        }

    val canOfferCreate: Boolean
        get() = materialQuery.isNotBlank() && materials.none { it.name.equals(materialQuery.trim(), ignoreCase = true) }
}

class PurchaseLineFormViewModel(
    private val materialRepository: MaterialRepository,
    private val purchaseLineRepository: PurchaseLineRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PurchaseLineFormUiState())
    val state = _state.asStateFlow()

    private var entryLocalId: String? = null
    private var projectLocalId: String? = null
    private var lineLocalId: String? = null
    private var loaded = false

    fun load(entryLocalId: String, projectLocalId: String, lineLocalId: String?) {
        if (loaded) return
        loaded = true
        this.entryLocalId = entryLocalId
        this.projectLocalId = projectLocalId
        this.lineLocalId = lineLocalId
        _state.update { it.copy(isEdit = lineLocalId != null) }

        viewModelScope.launch {
            materialRepository.observeMaterials(projectLocalId).collect { list ->
                _state.update { it.copy(materials = list) }
            }
        }
        if (lineLocalId == null) {
            _state.update { it.copy(ready = true) }
        } else {
            viewModelScope.launch {
                purchaseLineRepository.observeLines(entryLocalId).collect { lines ->
                    val line = lines.firstOrNull { it.localId == lineLocalId }
                    if (line == null) {
                        if (!_state.value.ready) _state.update { it.copy(isMissing = true) }
                        return@collect
                    }
                    if (!_state.value.ready) {
                        _state.update {
                            it.copy(
                                ready = true,
                                isMissing = false,
                                selectedMaterialId = line.materialLocalId,
                                materialQuery = it.materials.firstOrNull { m -> m.localId == line.materialLocalId }?.name.orEmpty(),
                                quantity = toInputText(line.quantity),
                                unitPrice = toInputText(line.unitPrice),
                                supplier = line.supplier.orEmpty(),
                            )
                        }
                    }
                }
            }
        }
    }

    fun onMaterialQueryChange(value: String) = _state.update {
        it.copy(materialQuery = value, selectedMaterialId = null, materialError = null, creatingNewUnit = null)
    }

    fun selectMaterial(material: Material) = _state.update {
        it.copy(materialQuery = material.name, selectedMaterialId = material.localId, materialError = null, creatingNewUnit = null)
    }

    fun startCreateMaterial() = _state.update { it.copy(creatingNewUnit = "") }
    fun cancelCreateMaterial() = _state.update { it.copy(creatingNewUnit = null) }
    fun onNewUnitChange(value: String) = _state.update { it.copy(creatingNewUnit = value) }

    fun confirmCreateMaterial() {
        val projectId = projectLocalId ?: return
        val name = _state.value.materialQuery.trim()
        val unit = _state.value.creatingNewUnit?.trim().orEmpty()
        if (name.isEmpty() || unit.isEmpty()) return
        viewModelScope.launch {
            val created = materialRepository.createMaterial(projectId, name, unit)
            _state.update { it.copy(selectedMaterialId = created.localId, materialQuery = created.name, creatingNewUnit = null, materialError = null) }
        }
    }

    fun onQuantityChange(value: String) = _state.update { it.copy(quantity = value, quantityError = null) }
    fun onUnitPriceChange(value: String) = _state.update { it.copy(unitPrice = value, unitPriceError = null) }
    fun onSupplierChange(value: String) = _state.update { it.copy(supplier = value) }

    fun submit() {
        val entryId = entryLocalId ?: return
        val current = _state.value
        val matError = if (!current.isEdit) validateMaterialSelected(current.selectedMaterialId) else null
        val qError = validateRequiredQuantity(current.quantity)
        val pError = validateRequiredUnitPrice(current.unitPrice)
        if (matError != null || qError != null || pError != null) {
            _state.update { it.copy(materialError = matError, quantityError = qError, unitPriceError = pError) }
            return
        }
        val quantity = parseAmountOrNull(current.quantity)!!
        val unitPrice = parseAmountOrNull(current.unitPrice)!!
        val supplier = current.supplier.trim().ifBlank { null }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            if (current.isEdit) {
                lineLocalId?.let { purchaseLineRepository.updateLine(it, UpdatePurchaseLineInput(quantity, unitPrice, supplier)) }
            } else {
                purchaseLineRepository.createLine(entryId, CreatePurchaseLineInput(current.selectedMaterialId!!, quantity, unitPrice, supplier))
            }
            _state.update { it.copy(isSubmitting = false, saved = true) }
        }
    }
}

internal fun toInputText(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
