package com.dmb.chantiertracker

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.CreatePurchaseLineInput
import com.dmb.chantiertracker.domain.model.CreateStageInput
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.UpdateProjectInput
import com.dmb.chantiertracker.support.DeviceStack
import com.dmb.chantiertracker.support.DisposableAccounts
import com.dmb.chantiertracker.support.IntegrationBackend
import com.dmb.chantiertracker.support.RedeployingProxy
import com.dmb.chantiertracker.support.everyRow
import com.dmb.chantiertracker.support.signInForTheFirstTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * R1 — the real mobile stack against the real backend, behind a proxy that answers like the
 * production reverse proxy while the API is redeployed (404 in plain text, about two minutes).
 */
class RedeployIntegrationTest {

    private val accounts = DisposableAccounts()
    private val proxy = RedeployingProxy(IntegrationBackend.baseUrl)
    private val devices = mutableListOf<DeviceStack>()

    @AfterTest
    fun cleanUp() {
        devices.forEach { it.close() }
        accounts.close()
        proxy.close()
    }

    private fun runScenario(block: suspend () -> Unit) = runBlocking {
        com.dmb.chantiertracker.support.skipUnlessRealBackendScenariosAreEnabled()
        block()
    }

    private fun aSmallJpeg(shade: Int): ByteArray {
        val image = BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 64) for (y in 0 until 48) image.setRGB(x, y, (x * 4 shl 16) or (y * 5 shl 8) or shade)
        return ByteArrayOutputStream().use { out -> ImageIO.write(image, "jpg", out); out.toByteArray() }
    }

    @Test
    fun r1_a_sync_during_a_redeploy_leaves_the_device_as_it_was_then_everything_leaves_once_the_api_is_back() = runScenario {
        val phone = DeviceStack(proxy.baseUrl).also { devices += it }
        phone.auth.signInForTheFirstTime(accounts.create("qa-r1", "QA R1"), "QaPassword1234!")

        val today = LocalDate.now(ZoneId.of("Europe/Paris")).toString()
        val projectId = phone.projects.createProject(CreateProjectInput("QA R1 redéploiement", null, "Nîmes", "EUR", "Europe/Paris"))
        val stageId = phone.stages.createStage(CreateStageInput(projectId, "Gros œuvre", null, null, null, null))
        val stageToDelete = phone.stages.createStage(CreateStageInput(projectId, "Clôture", null, null, null, null))
        val cement = phone.materials.createMaterial(projectId, "Ciment", "sac")
        val purchase = phone.logs.createPurchaseEntry(stageId, today)
        phone.purchaseLines.createLine(purchase.entryLocalId, CreatePurchaseLineInput(cement.localId, 10.0, 6.0, null))
        val lineToDelete = phone.purchaseLines.createLine(purchase.entryLocalId, CreatePurchaseLineInput(cement.localId, 4.0, 6.0, null))
        phone.attachments.addAttachment(purchase.entryLocalId, aSmallJpeg(0x10), "dalle.jpg", "image/jpeg")
        val photoToDelete = phone.attachments.addAttachment(purchase.entryLocalId, aSmallJpeg(0x30), "flou.jpg", "image/jpeg").localId
        phone.waitForTheRunningSyncPass()
        assertEquals(SyncOutcome.Synced, phone.sync.syncNow())
        phone.invitations.invite(projectId, "qa-r1-invite-${System.currentTimeMillis()}@local.dev")
        phone.sync.syncProject(projectId)
        assertTrue(phone.db.invitationDao().findForProject(projectId).isNotEmpty(), "the invitation is on the device")

        phone.goOffline()
        val detail = phone.projects.observeProject(projectId).first()!!
        phone.projects.updateProject(projectId, UpdateProjectInput("QA R1 renommé", detail.description, detail.location, detail.currency, detail.timezone, ProjectStatus.IN_PROGRESS))
        val newStage = phone.stages.createStage(CreateStageInput(projectId, "Toiture", null, null, null, null))
        val sand = phone.materials.createMaterial(projectId, "Sable", "t")
        val newLine = phone.purchaseLines.createLine(purchase.entryLocalId, CreatePurchaseLineInput(sand.localId, 2.0, 30.0, null))
        val newPhoto = phone.attachments.addAttachment(purchase.entryLocalId, aSmallJpeg(0x50), "fissure.jpg", "image/jpeg").localId
        phone.stages.deleteStage(stageToDelete)
        phone.purchaseLines.deleteLine(lineToDelete)
        phone.attachments.deleteAttachment(photoToDelete)
        val tokensBefore = phone.storage.get()!!
        phone.storage.save(AuthTokens("an-expired-access-token", tokensBefore.refreshToken))
        val before = phone.db.everyRow()
        println("R1 — avant le redéploiement :\n$before")

        proxy.redeployingFor = { _, _ -> true }
        phone.goOnline()
        phone.waitForTheRunningSyncPass()
        val passes = listOf(
            "passe" to suspend { phone.sync.syncNow() },
            "rafraîchissement manuel" to suspend { phone.sync.syncNowAtTheUsersRequest() },
            "ouverture du projet" to suspend { phone.sync.syncProject(projectId) },
            "ouverture de l'étape" to suspend { phone.sync.syncStage(stageId) },
            "ouverture de la journée" to suspend { phone.sync.syncLog(purchase.dailyLogLocalId) },
        )
        for ((name, pass) in passes) {
            val outcome = pass()
            println("R1 — pendant le redéploiement, $name : $outcome")
            assertEquals(SyncOutcome.Failed(DomainException.Network), outcome, "$name : un échec comme une coupure")
            assertEquals(before, phone.db.everyRow(), "$name : la base locale est identique, rien d'effacé, rien en refus, aucune suppression prise pour faite")
            assertIs<AuthState.Authenticated>(phone.authState.state.value, "$name : toujours connecté")
            assertEquals("an-expired-access-token", phone.storage.get()?.accessToken, "$name : la session n'est pas effacée")
        }
        println("R1 — réponses du proxy pendant le redéploiement : ${proxy.answeredWhileRedeploying.size} (${proxy.answeredWhileRedeploying.distinct().take(12)})")
        assertTrue(proxy.answeredWhileRedeploying.any { it.startsWith("POST") }, "des envois ont bien été tentés")
        assertTrue(proxy.answeredWhileRedeploying.any { it.startsWith("DELETE") }, "des suppressions ont bien été tentées")


        proxy.redeployingFor = { _, path -> path.contains("/auth/refresh-token") }
        val renewal = phone.sync.syncNow()
        println("R1 — jeton refusé par le backend, renouvellement tombé sur le proxy : $renewal")
        assertEquals(SyncOutcome.Failed(DomainException.Network), renewal, "le renouvellement sans réponse du backend échoue comme une coupure")
        assertTrue(proxy.answeredWhileRedeploying.any { it.contains("auth/refresh-token") }, "le renouvellement a bien été tenté pendant le redéploiement")
        assertEquals(before, phone.db.everyRow(), "renouvellement : la base locale est identique")
        assertIs<AuthState.Authenticated>(phone.authState.state.value, "renouvellement : toujours connecté")
        assertEquals(AuthTokens("an-expired-access-token", tokensBefore.refreshToken), phone.storage.get(), "renouvellement : la session n'est pas effacée")

        proxy.redeployingFor = { _, _ -> false }
        assertEquals(SyncOutcome.Synced, phone.sync.syncNowAtTheUsersRequest(), "l'API revenue, le rafraîchissement manuel lève l'attente et tout part")
        assertEquals(SyncOutcome.Synced, phone.sync.syncProject(projectId))
        assertIs<AuthState.Authenticated>(phone.authState.state.value)
        assertTrue(phone.storage.get()?.accessToken != "an-expired-access-token", "la session a été renouvelée une fois l'API revenue")

        val db = phone.db
        assertEquals(emptyList(), phone.syncIssues.observeIssues().first(), "rien à revoir")
        listOf(
            db.projectDao().findByLocalId(projectId), db.stageDao().findByLocalId(newStage), db.materialDao().findByLocalId(sand.localId),
            db.purchaseLineDao().findByLocalId(newLine), db.attachmentDao().findByLocalId(newPhoto),
        ).forEach { assertEquals(SyncStatus.SYNCED, it?.syncStatus, "$it") }
        assertEquals(null, db.stageDao().findByLocalId(stageToDelete))
        assertEquals(null, db.purchaseLineDao().findByLocalId(lineToDelete))
        assertEquals(null, db.attachmentDao().findByLocalId(photoToDelete))

        val projectServerId = db.projectDao().findByLocalId(projectId)!!.serverId!!
        val entryServerId = db.dailyEntryDao().findByLocalId(purchase.entryLocalId)!!.serverId!!
        val serverStages = phone.stageApi.list(projectServerId, 0, 50).content.map { it.name }.sorted()
        val serverLines = phone.purchaseLineApi.list(entryServerId, 0, 50).content.map { it.quantity }.sorted()
        val serverPhotos = phone.attachmentApi.list(entryServerId, 0, 50).content.map { it.originalName }.sortedBy { it }
        println("R1 — serveur après le retour : projet=${phone.projectApi.get(projectServerId).name}, étapes=$serverStages, lignes=$serverLines, photos=$serverPhotos")
        assertEquals("QA R1 renommé", phone.projectApi.get(projectServerId).name)
        assertEquals(listOf("Gros œuvre", "Toiture"), serverStages)
        assertEquals(listOf(2.0, 10.0), serverLines, "une seule ligne de sable, la ligne supprimée est partie")
        assertEquals(2, serverPhotos.size, "dalle et fissure, une fois chacune ; flou supprimée : $serverPhotos")
        assertEquals(1, phone.invitationApi.list(projectServerId, 0, 50).content.size, "l'invitation est toujours là")
    }
}
