package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.Material
import com.dmb.chantiertracker.domain.model.MaterialStock
import kotlinx.coroutines.flow.Flow

interface MaterialRepository {
    fun observeMaterials(projectLocalId: String): Flow<List<Material>>

    /** Each material's running stock for the project — `available` computed from purchase/consumption lines, never stored. */
    fun observeStock(projectLocalId: String): Flow<List<MaterialStock>>

    /** Writes the material to the local store immediately and returns it. Sync happens in the background. */
    suspend fun createMaterial(projectLocalId: String, name: String, unit: String): Material
}
