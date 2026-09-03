package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.CreateStageInput
import com.dmb.chantiertracker.domain.model.Stage
import com.dmb.chantiertracker.domain.model.StageDetail
import com.dmb.chantiertracker.domain.model.UpdateStageInput
import kotlinx.coroutines.flow.Flow

interface StageRepository {
    fun observeStages(projectLocalId: String): Flow<List<Stage>>
    fun observeStage(stageLocalId: String): Flow<StageDetail?>

    /** Writes the stage to the local store immediately and returns its stable local id. Sync happens in the background. */
    suspend fun createStage(input: CreateStageInput): String

    /** Applies the edit to the local store immediately, flagged pending. Sync happens in the background. */
    suspend fun updateStage(stageLocalId: String, input: UpdateStageInput)

    /** Marks the stage for deletion locally (or drops it outright if it never reached the server). Sync happens in the background. */
    suspend fun deleteStage(stageLocalId: String)

    /** Best-effort pull of a project's stages from the server into the local store. Never throws. */
    suspend fun refreshStages(projectLocalId: String)

    /** Best-effort pull of one stage from the server into the local store. Never throws. */
    suspend fun refreshStage(stageLocalId: String)
}
