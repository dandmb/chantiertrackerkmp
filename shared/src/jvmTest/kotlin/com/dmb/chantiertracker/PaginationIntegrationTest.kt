package com.dmb.chantiertracker

import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.data.remote.dto.CreateConsumptionLineRequestDto
import com.dmb.chantiertracker.data.remote.dto.CreateInvitationRequestDto
import com.dmb.chantiertracker.data.remote.dto.CreateMaterialRequestDto
import com.dmb.chantiertracker.data.remote.dto.CreateProjectRequestDto
import com.dmb.chantiertracker.data.remote.dto.CreatePurchaseLineRequestDto
import com.dmb.chantiertracker.data.remote.dto.CreateStageRequestDto
import com.dmb.chantiertracker.data.remote.dto.EntryRequestDto
import com.dmb.chantiertracker.data.remote.dto.UpdateMaterialRequestDto
import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.support.DeviceStack
import com.dmb.chantiertracker.support.DisposableAccounts
import com.dmb.chantiertracker.support.IntegrationBackend
import com.dmb.chantiertracker.support.retryingOnRateLimit
import com.dmb.chantiertracker.support.signInForTheFirstTime
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import javax.imageio.ImageIO
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
        com.dmb.chantiertracker.support.skipUnlessRealBackendScenariosAreEnabled()
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

    private fun aSmallJpeg(): ByteArray {
        val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB)
        return ByteArrayOutputStream().use { out -> ImageIO.write(image, "jpg", out); out.toByteArray() }
    }

    @Test
    fun c5_twenty_five_lines_of_each_kind_and_twenty_five_photos_all_reach_a_second_device_and_stay_there() = runScenario {
        val owner = device()
        owner.signedInAs("qa-c5-lines-owner", "QA C5 Propriétaire lignes")
        val supervisor = device()
        val supervisorAccount = supervisor.signedInAs("qa-c5-lines-super", "QA C5 Superviseur lignes")

        val project = owner.projects.createProject(CreateProjectInput("QA C5 lignes", null, "Nîmes", "EUR", "Europe/Paris"))
        owner.sync.syncNow()
        val projectServerId = owner.db.projectDao().findByLocalId(project)!!.serverId!!
        val stage = owner.stageApi.create(projectServerId, CreateStageRequestDto("Gros œuvre")).id
        val today = LocalDate.now(ZoneId.of("Europe/Paris")).toString()
        val purchaseEntry = owner.dailyLogApi.createPurchaseEntry(stage, today, EntryRequestDto("Achats du jour")).id
        val workEntry = owner.dailyLogApi.createWorkEntry(stage, today, EntryRequestDto("Travaux du jour")).id
        val cement = owner.materialApi.create(projectServerId, CreateMaterialRequestDto("Ciment", "sac")).id
        val purchaseIds = (1..25).map { owner.purchaseLineApi.create(purchaseEntry, CreatePurchaseLineRequestDto(cement, 10.0, 2.0, "Fournisseur $it")).id }
        val consumptionIds = (1..25).map { owner.consumptionLineApi.create(workEntry, CreateConsumptionLineRequestDto(cement, 1.0)).id }
        val jpeg = aSmallJpeg()
        val photoIds = (1..25).map {
            retryingOnRateLimit {
                apiCall { owner.attachmentApi.upload(purchaseEntry, jpeg.size.toLong(), "ticket-$it.jpg", "image/jpeg", openSource = { Buffer().apply { write(jpeg) } }) }
            }.id
        }
        owner.invitations.invite(project, supervisorAccount.email)
        supervisor.invitations.acceptInvitation(supervisor.invitations.listIncomingInvitations().single().token)
        supervisor.sync.syncNow()
        val supervisorProject = supervisor.projects.observeProjects().first().single().localId
        assertIs<SyncOutcome.Synced>(supervisor.sync.syncProject(supervisorProject))
        val supervisorStage = supervisor.db.stageDao().findForProject(supervisorProject).single().localId
        val day = supervisor.db.dailyLogDao().findForStage(supervisorStage).single().localId

        suspend fun entry(serverId: Long) = supervisor.db.dailyEntryDao().findForLog(day).single { it.serverId == serverId }.localId
        suspend fun purchases() = supervisor.db.purchaseLineDao().findForEntry(entry(purchaseEntry)).mapNotNull { it.serverId }.toSet()
        suspend fun consumptions() = supervisor.db.consumptionLineDao().findForEntry(entry(workEntry)).mapNotNull { it.serverId }.toSet()
        suspend fun photos() = supervisor.db.attachmentDao().findForEntry(entry(purchaseEntry))

        assertIs<SyncOutcome.Synced>(supervisor.sync.syncLog(day))
        val firstPass = Triple(purchases(), consumptions(), photos().mapNotNull { it.serverId }.toSet())
        assertIs<SyncOutcome.Synced>(supervisor.sync.syncLog(day))
        val secondPass = Triple(purchases(), consumptions(), photos().mapNotNull { it.serverId }.toSet())
        println(
            "C5 lignes et justificatifs — serveur 25/25/25 ; superviseur 1re passe=${firstPass.first.size}/${firstPass.second.size}/${firstPass.third.size} ; " +
                "2e passe=${secondPass.first.size}/${secondPass.second.size}/${secondPass.third.size} ; fichiers locaux=${supervisor.fileStore.storedPaths.size}, supprimés=${supervisor.fileStore.deletedPaths.size}",
        )
        assertEquals(Triple(purchaseIds.toSet(), consumptionIds.toSet(), photoIds.toSet()), firstPass)
        assertEquals(firstPass, secondPass)
        assertEquals(25, supervisor.fileStore.storedPaths.size)
        assertEquals(emptyList(), supervisor.fileStore.deletedPaths)

        owner.purchaseLineApi.delete(purchaseIds[3])
        owner.consumptionLineApi.delete(consumptionIds[3])
        owner.attachmentApi.delete(photoIds[3])
        assertIs<SyncOutcome.Synced>(supervisor.sync.syncLog(day))
        println(
            "C5 lignes et justificatifs — après une suppression de chaque côté serveur : ${purchases().size}/${consumptions().size}/${photos().size} ; " +
                "fichiers locaux=${supervisor.fileStore.storedPaths.size}, supprimés=${supervisor.fileStore.deletedPaths.size}",
        )
        assertEquals(purchaseIds.toSet() - purchaseIds[3], purchases())
        assertEquals(consumptionIds.toSet() - consumptionIds[3], consumptions())
        assertEquals(photoIds.toSet() - photoIds[3], photos().mapNotNull { it.serverId }.toSet())
        assertEquals(1, supervisor.fileStore.deletedPaths.size)
        assertEquals(photos().map { it.localPath }.toSet(), supervisor.fileStore.storedPaths, "un fichier par justificatif, aucun orphelin")
    }

    private suspend fun DeviceStack.serverStock(projectServerId: Long): Map<String, Double> {
        val page = Json.parseToJsonElement(client.get("projects/$projectServerId/stock?page=0&size=100&sort=id,asc").bodyAsText()).jsonObject
        return page["content"]!!.jsonArray.associate {
            it.jsonObject["materialName"]!!.jsonPrimitive.content to it.jsonObject["available"]!!.jsonPrimitive.double
        }
    }

    @Test
    fun c5_twenty_five_invitations_and_twenty_five_stock_counters_all_reach_a_second_device_and_stay_there() = runScenario {
        val phone = device()
        val account = phone.signedInAs("qa-c5-invitations-owner", "QA C5 Propriétaire invitations")
        accounts.grantPlan(account.id, "LIBERTE")

        val project = phone.projects.createProject(CreateProjectInput("QA C5 invitations", null, "Nîmes", "EUR", "Europe/Paris"))
        phone.sync.syncNow()
        val projectServerId = phone.db.projectDao().findByLocalId(project)!!.serverId!!
        val stamp = System.currentTimeMillis()
        val invitationIds = (1..25).map {
            retryingOnRateLimit { apiCall { phone.invitationApi.create(projectServerId, CreateInvitationRequestDto("qa-c5-invite-$it-$stamp@local.dev", "SUPERVISOR")) } }.id
        }
        val stage = phone.stageApi.create(projectServerId, CreateStageRequestDto("Gros œuvre")).id
        val today = LocalDate.now(ZoneId.of("Europe/Paris")).toString()
        val purchaseEntry = phone.dailyLogApi.createPurchaseEntry(stage, today, EntryRequestDto("Achats du jour")).id
        val purchaseIds = (1..25).map {
            val material = phone.materialApi.create(projectServerId, CreateMaterialRequestDto("Matériau $it", "u")).id
            phone.purchaseLineApi.create(purchaseEntry, CreatePurchaseLineRequestDto(material, it.toDouble(), 2.0, null)).id
        }

        val tablet = device()
        retryingOnRateLimit { tablet.auth.login(account.email, "QaPassword1234!") }
        tablet.sync.syncNow()
        val tabletProject = tablet.projects.observeProjects().first().single().localId

        suspend fun invitations() = tablet.db.invitationDao().findForProject(tabletProject).map { it.id }.toSet()
        suspend fun appStock() = tablet.materials.observeStock(tabletProject).first().materials.associate { it.materialName to it.available }

        assertIs<SyncOutcome.Synced>(tablet.sync.syncProject(tabletProject))
        val firstPass = invitations() to appStock()
        assertIs<SyncOutcome.Synced>(tablet.sync.syncProject(tabletProject))
        val secondPass = invitations() to appStock()
        val server = tablet.serverStock(projectServerId)
        val members = tablet.projects.observeMembers(tabletProject).first().size
        println(
            "C5 invitations et stock — serveur 25 invitations, ${server.size} compteurs ; tablette 1re passe=${firstPass.first.size} invitations, " +
                "${firstPass.second.size} compteurs ; 2e passe=${secondPass.first.size} invitations, ${secondPass.second.size} compteurs ; membres=$members",
        )
        assertEquals(invitationIds.toSet(), firstPass.first)
        assertEquals(invitationIds.toSet(), secondPass.first)
        assertEquals(25, server.size)
        assertEquals(server, firstPass.second)
        assertEquals(server, secondPass.second)
        assertEquals(1, members)

        phone.invitationApi.cancel(invitationIds[3])
        phone.purchaseLineApi.delete(purchaseIds[24])
        assertIs<SyncOutcome.Synced>(tablet.sync.syncProject(tabletProject))
        val serverAfter = tablet.serverStock(projectServerId)
        println(
            "C5 invitations et stock — après annulation d'une invitation et suppression d'un achat côté serveur : ${invitations().size} invitations ; " +
                "« Matériau 25 » app=${appStock()["Matériau 25"]}, serveur=${serverAfter["Matériau 25"]}",
        )
        assertEquals(invitationIds.toSet() - invitationIds[3], invitations())
        assertEquals(serverAfter.filterValues { it != 0.0 }, appStock().filterValues { it != 0.0 })
        assertEquals(0.0, appStock()["Matériau 25"] ?: 0.0)
    }
}
