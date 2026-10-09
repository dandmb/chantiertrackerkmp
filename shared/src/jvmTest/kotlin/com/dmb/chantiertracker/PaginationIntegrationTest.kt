package com.dmb.chantiertracker

import com.dmb.chantiertracker.data.remote.dto.CreateMaterialRequestDto
import com.dmb.chantiertracker.data.remote.dto.CreateProjectRequestDto
import com.dmb.chantiertracker.data.remote.dto.CreateStageRequestDto
import com.dmb.chantiertracker.data.remote.dto.EntryRequestDto
import com.dmb.chantiertracker.data.remote.dto.UpdateMaterialRequestDto
import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.support.DeviceStack
import com.dmb.chantiertracker.support.DisposableAccounts
import com.dmb.chantiertracker.support.IntegrationBackend
import com.dmb.chantiertracker.support.signInForTheFirstTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId
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

    @Test
    fun c5_twenty_five_days_and_twenty_five_materials_all_reach_a_second_device_and_stay_there() = runScenario {
        val owner = device()
        owner.signedInAs("qa-c5-days-owner", "QA C5 Propriétaire journées")
        val supervisor = device()
        val supervisorAccount = supervisor.signedInAs("qa-c5-days-super", "QA C5 Superviseur journées")

        val project = owner.projects.createProject(CreateProjectInput("QA C5 journées", null, "Nîmes", "EUR", "Europe/Paris"))
        owner.sync.syncNow()
        val projectServerId = owner.db.projectDao().findByLocalId(project)!!.serverId!!
        val mainStage = owner.stageApi.create(projectServerId, CreateStageRequestDto("Gros œuvre")).id
        val doomedStage = owner.stageApi.create(projectServerId, CreateStageRequestDto("Étape à supprimer")).id
        val today = LocalDate.now(ZoneId.of("Europe/Paris"))
        val days = (0..24).map { today.minusDays(it.toLong()).toString() }
        days.forEach { owner.dailyLogApi.createPurchaseEntry(mainStage, it, EntryRequestDto("Achat du $it")) }
        owner.dailyLogApi.createPurchaseEntry(doomedStage, today.toString(), EntryRequestDto("Achat de l'étape à supprimer"))
        val materialIds = (1..25).map { owner.materialApi.create(projectServerId, CreateMaterialRequestDto("Matériau $it", "u")).id }
        owner.invitations.invite(project, supervisorAccount.email)
        supervisor.invitations.acceptInvitation(supervisor.invitations.listIncomingInvitations().single().token)
        supervisor.sync.syncNow()
        val supervisorProject = supervisor.projects.observeProjects().first().single().localId

        suspend fun daysOf(stageServerId: Long): Set<String> {
            val stage = supervisor.db.stageDao().findForProject(supervisorProject).firstOrNull { it.serverId == stageServerId } ?: return emptySet()
            return supervisor.db.dailyLogDao().findForStage(stage.localId).map { it.date }.toSet()
        }
        suspend fun materials() = supervisor.db.materialDao().findForProject(supervisorProject)

        assertIs<SyncOutcome.Synced>(supervisor.sync.syncProject(supervisorProject))
        val daysAfterFirstPass = daysOf(mainStage)
        val materialsAfterFirstPass = materials().mapNotNull { it.serverId }.toSet()
        assertIs<SyncOutcome.Synced>(supervisor.sync.syncProject(supervisorProject))
        val daysAfterSecondPass = daysOf(mainStage)
        val materialsAfterSecondPass = materials().mapNotNull { it.serverId }.toSet()
        println(
            "C5 journées et matériaux — serveur 25 et 25 ; superviseur 1re passe=${daysAfterFirstPass.size} journées, " +
                "${materialsAfterFirstPass.size} matériaux ; 2e passe=${daysAfterSecondPass.size} journées, ${materialsAfterSecondPass.size} matériaux",
        )
        assertEquals(days.toSet(), daysAfterFirstPass)
        assertEquals(days.toSet(), daysAfterSecondPass)
        assertEquals(materialIds.toSet(), materialsAfterFirstPass)
        assertEquals(materialIds.toSet(), materialsAfterSecondPass)
        assertEquals(1, daysOf(doomedStage).size)

        owner.materialApi.update(materialIds[24], UpdateMaterialRequestDto(name = "Matériau 25 renommé"))
        owner.stageApi.delete(doomedStage)
        assertIs<SyncOutcome.Synced>(supervisor.sync.syncProject(supervisorProject))
        val renamed = materials().single { it.serverId == materialIds[24] }.name
        val stagesLeft = supervisor.db.stageDao().findForProject(supervisorProject).mapNotNull { it.serverId }
        println("C5 journées et matériaux — après renommage du 25e matériau et suppression d'une étape côté serveur : « $renamed », étapes=${stagesLeft.size}, journées=${daysOf(mainStage).size}, matériaux=${materials().size}")
        assertEquals("Matériau 25 renommé", renamed)
        assertEquals(listOf(mainStage), stagesLeft)
        assertEquals(emptySet(), daysOf(doomedStage))
        assertEquals(days.toSet(), daysOf(mainStage))
        assertEquals(25, materials().size)
    }
}
