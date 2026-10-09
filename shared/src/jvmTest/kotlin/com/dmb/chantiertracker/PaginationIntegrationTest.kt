package com.dmb.chantiertracker

import com.dmb.chantiertracker.data.remote.dto.CreateProjectRequestDto
import com.dmb.chantiertracker.data.remote.dto.CreateStageRequestDto
import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.support.DeviceStack
import com.dmb.chantiertracker.support.DisposableAccounts
import com.dmb.chantiertracker.support.IntegrationBackend
import com.dmb.chantiertracker.support.signInForTheFirstTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Campagne QA, constat C-5 — listes de plus de 20 éléments, contre un vrai backend (ADR-73). Même
 * convention que les groupes A, B et C : désactivé par défaut, comptes jetables créés par le
 * super-admin, un scénario à la fois, tout ce qui est observé est imprimé.
 */
class PaginationIntegrationTest {

    private val accounts = DisposableAccounts()
    private val devices = mutableListOf<DeviceStack>()

    @AfterTest
    fun cleanUp() {
        devices.forEach { it.close() }
        accounts.close()
    }

    private fun device() = DeviceStack().also { devices += it }

    private fun runScenario(block: suspend () -> Unit) = runBlocking {
        if (!IntegrationBackend.enabled || !IntegrationBackend.adminConfigured) {
            println("Scénario ignoré (integrationTests + identifiants super-admin requis).")
            return@runBlocking
        }
        block()
    }

    private suspend fun DeviceStack.signedInAs(prefix: String, name: String): DisposableAccounts.Account {
        val account = accounts.create(prefix, name)
        auth.signInForTheFirstTime(account, "QaPassword1234!")
        return account
    }

    @Test
    fun c5_twenty_five_stages_all_reach_a_second_device_and_stay_there() = runScenario {
        val owner = device()
        owner.signedInAs("qa-c5-stages-owner", "QA C5 Propriétaire étapes")
        val supervisor = device()
        val supervisorAccount = supervisor.signedInAs("qa-c5-stages-super", "QA C5 Superviseur étapes")

        val project = owner.projects.createProject(CreateProjectInput("QA C5 étapes", null, "Nîmes", "EUR", "Europe/Paris"))
        owner.sync.syncNow()
        val projectServerId = owner.db.projectDao().findByLocalId(project)!!.serverId!!
        val stageServerIds = (1..25).map { owner.stageApi.create(projectServerId, CreateStageRequestDto("Étape $it")).id }
        owner.invitations.invite(project, supervisorAccount.email)
        supervisor.invitations.acceptInvitation(supervisor.invitations.listIncomingInvitations().single().token)
        supervisor.sync.syncNow()
        val supervisorProject = supervisor.projects.observeProjects().first().single().localId

        assertIs<SyncOutcome.Synced>(supervisor.sync.syncProject(supervisorProject))
        val afterFirstPass = supervisor.stages.observeStages(supervisorProject).first().size
        assertIs<SyncOutcome.Synced>(supervisor.sync.syncProject(supervisorProject))
        val afterSecondPass = supervisor.stages.observeStages(supervisorProject).first().size
        println("C5 étapes — serveur 25 ; superviseur après 1re passe=$afterFirstPass, après 2e passe=$afterSecondPass")
        assertEquals(25, afterFirstPass)
        assertEquals(25, afterSecondPass)

        owner.stageApi.delete(stageServerIds[3])
        assertIs<SyncOutcome.Synced>(supervisor.sync.syncProject(supervisorProject))
        val afterServerDelete = supervisor.db.stageDao().findForProject(supervisorProject).mapNotNull { it.serverId }.toSet()
        println("C5 étapes — après suppression d'une étape côté serveur : ${afterServerDelete.size}")
        assertEquals(stageServerIds.toSet() - stageServerIds[3], afterServerDelete)
    }

    @Test
    fun c5_twenty_two_projects_all_reach_the_device_and_stay_there() = runScenario {
        val owner = device()
        val account = owner.signedInAs("qa-c5-projects-owner", "QA C5 Propriétaire projets")
        accounts.grantPlan(account.id, "LIBERTE")

        val projectServerIds = (1..22).map {
            owner.projectApi.create(CreateProjectRequestDto("QA C5 projet $it", null, "Nîmes", "EUR", "Europe/Paris")).id
        }

        assertIs<SyncOutcome.Synced>(owner.sync.syncNow())
        val afterFirstPass = owner.db.projectDao().findAll().mapNotNull { it.serverId }.toSet()
        assertIs<SyncOutcome.Synced>(owner.sync.syncNow())
        val afterSecondPass = owner.db.projectDao().findAll().mapNotNull { it.serverId }.toSet()
        println("C5 projets — serveur 22 ; app après 1re passe=${afterFirstPass.size}, après 2e passe=${afterSecondPass.size}")
        assertEquals(projectServerIds.toSet(), afterFirstPass)
        assertEquals(projectServerIds.toSet(), afterSecondPass)

        owner.projectApi.delete(projectServerIds[5])
        assertIs<SyncOutcome.Synced>(owner.sync.syncNow())
        val afterServerDelete = owner.db.projectDao().findAll().mapNotNull { it.serverId }.toSet()
        println("C5 projets — après suppression d'un projet côté serveur : ${afterServerDelete.size}")
        assertEquals(projectServerIds.toSet() - projectServerIds[5], afterServerDelete)
    }
}
