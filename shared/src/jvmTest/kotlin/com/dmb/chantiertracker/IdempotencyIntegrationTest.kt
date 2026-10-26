package com.dmb.chantiertracker

import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.CreatePurchaseLineInput
import com.dmb.chantiertracker.domain.model.CreateStageInput
import com.dmb.chantiertracker.support.DeviceStack
import com.dmb.chantiertracker.support.DisposableAccounts
import com.dmb.chantiertracker.support.IntegrationBackend
import com.dmb.chantiertracker.support.RedeployingProxy
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
import kotlin.test.assertTrue

/**
 * R1b — BE-2 and BE-4 against the real backend: every creation reaches the server, its answer is
 * lost on the way back (the connection is cut after the backend has answered), the app sends it
 * again with the same Idempotency-Key, and the server ends with one row of each.
 */
class IdempotencyIntegrationTest {

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

    private fun aSmallJpeg(): ByteArray {
        val image = BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 64) for (y in 0 until 48) image.setRGB(x, y, (x * 4 shl 16) or (y * 5 shl 8) or 0x20)
        return ByteArrayOutputStream().use { out -> ImageIO.write(image, "jpg", out); out.toByteArray() }
    }

    @Test
    fun r1b_creations_whose_answer_was_lost_are_sent_again_with_their_key_and_exist_once_on_the_server() = runScenario {
        val phone = DeviceStack(proxy.baseUrl).also { devices += it }
        phone.auth.signInForTheFirstTime(accounts.create("qa-r1b", "QA R1b"), "QaPassword1234!")

        phone.goOffline()
        val today = LocalDate.now(ZoneId.of("Europe/Paris")).toString()
        val projectId = phone.projects.createProject(CreateProjectInput("QA R1b idempotence", null, "Nîmes", "EUR", "Europe/Paris"))
        val stageId = phone.stages.createStage(CreateStageInput(projectId, "Gros œuvre", null, null, null, null))
        val cement = phone.materials.createMaterial(projectId, "Ciment", "sac")
        val purchase = phone.logs.createPurchaseEntry(stageId, today)
        val line = phone.purchaseLines.createLine(purchase.entryLocalId, CreatePurchaseLineInput(cement.localId, 10.0, 6.0, null))
        val photo = phone.attachments.addAttachment(purchase.entryLocalId, aSmallJpeg(), "dalle.jpg", "image/jpeg").localId

        val answerLostOnce = mutableSetOf<String>()
        proxy.loseTheAnswerFor = { method, path -> method == "POST" && !path.contains("/auth/") && answerLostOnce.add(path.substringBefore('?')) }
        phone.goOnline()
        phone.waitForTheRunningSyncPass()
        val outcomes = mutableListOf<SyncOutcome>()
        suspend fun everythingLeft() = phone.db.attachmentDao().findByLocalId(photo)?.syncStatus == SyncStatus.SYNCED &&
            phone.db.purchaseLineDao().findByLocalId(line)?.syncStatus == SyncStatus.SYNCED
        repeat(8) { if (!everythingLeft()) outcomes += phone.sync.syncNowAtTheUsersRequest() }
        println("R1b — passes : $outcomes")
        val creations = proxy.forwarded.filter { it.method == "POST" && !it.path.contains("/auth/") }
        creations.forEach { println("R1b — ${it.method} ${it.path} clé=${it.idempotencyKey} statut=${it.status} rejoué=${it.replayed} réponse perdue=${it.answerLost}") }

        val db = phone.db
        val rows = mapOf(
            projectId to db.projectDao().findByLocalId(projectId)?.syncStatus,
            stageId to db.stageDao().findByLocalId(stageId)?.syncStatus,
            cement.localId to db.materialDao().findByLocalId(cement.localId)?.syncStatus,
            purchase.entryLocalId to db.dailyEntryDao().findByLocalId(purchase.entryLocalId)?.syncStatus,
            line to db.purchaseLineDao().findByLocalId(line)?.syncStatus,
            photo to db.attachmentDao().findByLocalId(photo)?.syncStatus,
        )
        rows.forEach { (localId, status) -> assertEquals(SyncStatus.SYNCED, status, "$localId synchronisée") }
        assertEquals(emptyList(), phone.syncIssues.observeIssues().first(), "rien à revoir")

        creations.groupBy { it.path.substringBefore('?') }.forEach { (path, sends) ->
            val keys = sends.map { it.idempotencyKey }.toSet()
            assertEquals(1, keys.size, "$path : la même clé à chaque envoi, $keys")
            assertTrue(keys.single() in rows.keys, "$path : la clé est l'identifiant local de la ligne (${keys.single()})")
        }
        val resent = creations.filter { !it.answerLost }
        assertTrue(resent.filterNot { it.path.endsWith("/attachments") }.all { it.replayed && it.status == 201 }, "chaque renvoi est un rejeu 201 : $resent")

        val projectServerId = db.projectDao().findByLocalId(projectId)!!.serverId!!
        val entryServerId = db.dailyEntryDao().findByLocalId(purchase.entryLocalId)!!.serverId!!
        val serverProjects = phone.projectApi.list(0, 50).content.map { it.name }
        val serverStages = phone.stageApi.list(projectServerId, 0, 50).content.map { it.name }
        val serverMaterials = phone.materialApi.list(projectServerId, 0, 50).content.map { it.name }
        val serverLines = phone.purchaseLineApi.list(entryServerId, 0, 50).content.map { it.quantity }
        val serverPhotos = phone.attachmentApi.list(entryServerId, 0, 50).content.map { it.originalName }
        println("R1b — serveur : projets=$serverProjects étapes=$serverStages matériaux=$serverMaterials lignes=$serverLines photos=$serverPhotos")
        assertEquals(listOf("QA R1b idempotence"), serverProjects, "un seul projet")
        assertEquals(listOf("Gros œuvre"), serverStages, "une seule étape")
        assertEquals(listOf("Ciment"), serverMaterials, "un seul matériau")
        assertEquals(listOf(10.0), serverLines, "une seule ligne d'achat : le stock n'est pas faussé")
        assertEquals(1, serverPhotos.size, "une seule photo : $serverPhotos")
    }
}
