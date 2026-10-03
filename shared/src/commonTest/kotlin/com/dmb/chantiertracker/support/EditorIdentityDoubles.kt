package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.EditorIdentityDao
import com.dmb.chantiertracker.data.local.db.EditorIdentityEntity
import com.dmb.chantiertracker.domain.model.EditorIdentity
import com.dmb.chantiertracker.domain.repository.EditorIdentityRepository
import com.dmb.chantiertracker.presentation.legal.LegalDocumentViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeEditorIdentityDao(initial: EditorIdentityEntity? = null) : EditorIdentityDao {
    private val row = MutableStateFlow(initial)
    override fun observe(): Flow<EditorIdentityEntity?> = row
    override suspend fun upsert(entity: EditorIdentityEntity) { row.value = entity }
}

class FakeEditorIdentityRepository(identity: EditorIdentity? = null) : EditorIdentityRepository {
    val identityFlow = MutableStateFlow(identity)
    var refreshCount = 0
        private set

    override fun observe(): Flow<EditorIdentity?> = identityFlow
    override suspend fun refresh() { refreshCount++ }
}

fun legalVm(identity: EditorIdentity? = null) = LegalDocumentViewModel(FakeEditorIdentityRepository(identity))

/** Every field filled in, as a SUPER_ADMIN would once the legal structure exists. */
val filledEditorIdentity = EditorIdentity(
    firstName = "Jean",
    lastName = "Martin",
    companyName = "Chantier Martin SAS",
    legalStatus = "SAS au capital de 1 000 €",
    siret = "123 456 789 00012",
    address = "12 rue des Lilas, 30000 Nîmes",
    contactEmail = "contact@chantiertracker.com",
    vatNumber = "FR12123456789",
    hostingProviderName = "Hetzner Online GmbH",
    hostingProviderAddress = "Industriestr. 25, 91710 Gunzenhausen, Allemagne",
)
