package com.dmb.chantiertracker

import com.dmb.chantiertracker.data.session.SessionNotice
import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.CreatePurchaseLineInput
import com.dmb.chantiertracker.domain.model.CreateStageInput
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.support.DeviceStack
import com.dmb.chantiertracker.support.DisposableAccounts
import com.dmb.chantiertracker.support.IntegrationBackend
import com.dmb.chantiertracker.support.everyRow
import com.dmb.chantiertracker.support.retryingOnRateLimit
import com.dmb.chantiertracker.support.skipUnless
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URI
import java.time.LocalDate
import java.time.ZoneId
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Suppression du compte (exigence Google Play) — le vrai code mobile contre le vrai backend, avec
 * un compte inscrit comme un vrai utilisateur (inscription, code lu dans MailDev). L'inscription
 * prend une place fondateur que le backend ne rend jamais, d'où le drapeau
 * `-Dchantiertracker.integrationAllowFounderSeat=true`.
 */
class AccountDeletionIntegrationTest {

    private val accounts = DisposableAccounts()
    private val devices = mutableListOf<DeviceStack>()

    @AfterTest
    fun cleanUp() {
        devices.forEach { it.close() }
        accounts.close()
    }

    private fun aSmallJpeg(): ByteArray {
        val image = BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 64) for (y in 0 until 48) image.setRGB(x, y, (x * 4 shl 16) or (y * 5 shl 8))
        return ByteArrayOutputStream().use { out -> ImageIO.write(image, "jpg", out); out.toByteArray() }
    }

    private fun verificationCodeFor(email: String): String {
        val codeCell = Regex("""class="ct-code"[^>]*>\s*(\d{6})\s*<""")
        repeat(20) {
            val messages = runCatching { Json.parseToJsonElement(URI("http://localhost:1080/email").toURL().readText()).jsonArray }.getOrNull().orEmpty()
            val html = messages.lastOrNull { it.jsonObject["to"]?.toString()?.contains(email) == true }
                ?.jsonObject?.get("html")?.jsonPrimitive?.contentOrNull.orEmpty()
            codeCell.find(html)?.groupValues?.get(1)?.let { return it }
            Thread.sleep(300)
        }
        error("Aucun code de vérification pour $email dans MailDev")
    }

    @Test
    fun a_registered_account_deleted_from_the_app_leaves_nothing_on_the_device_nor_on_the_server(): Unit = runBlocking {
        com.dmb.chantiertracker.support.skipUnlessRealBackendScenariosAreEnabled()
        skipUnless(
            System.getProperty("chantiertracker.integrationAllowFounderSeat") == "true",
            "inscription réelle : elle prend une place fondateur (-Dchantiertracker.integrationAllowFounderSeat=true requis)",
        )
        val phone = DeviceStack().also { devices += it }
        val email = "qa-suppression-${System.currentTimeMillis()}@local.dev"
        val password = "QaSuppression1234!"
        retryingOnRateLimit { phone.auth.register(email, password, "QA Suppression") }
        retryingOnRateLimit { phone.auth.verifyEmail(email, verificationCodeFor(email)) }
        retryingOnRateLimit { phone.auth.login(email, password) }
        val signedIn = assertIs<AuthState.Authenticated>(phone.authState.state.value)
        var deleted = false
        try {
            println("Suppression — compte jetable $email (id ${signedIn.user.id})")

            val today = LocalDate.now(ZoneId.of("Europe/Paris")).toString()
            val projectId = phone.projects.createProject(CreateProjectInput("QA suppression du compte", null, "Nîmes", "EUR", "Europe/Paris"))
            val stageId = phone.stages.createStage(CreateStageInput(projectId, "Gros œuvre", null, null, null, null))
            val cement = phone.materials.createMaterial(projectId, "Ciment", "sac")
            val purchase = phone.logs.createPurchaseEntry(stageId, today)
            phone.purchaseLines.createLine(purchase.entryLocalId, CreatePurchaseLineInput(cement.localId, 10.0, 6.0, null))
            phone.attachments.addAttachment(purchase.entryLocalId, aSmallJpeg(), "dalle.jpg", "image/jpeg")
            phone.waitForTheRunningSyncPass()
            assertEquals(SyncOutcome.Synced, phone.sync.syncNow())
            assertTrue(phone.fileStore.storedPaths.isNotEmpty(), "la photo est sur l'appareil")
            assertTrue(phone.ownerStore.ownerId() != null, "le propriétaire des données est noté sur l'appareil")

            val preview = phone.accountDeletion.preview()
            println("Suppression — aperçu : $preview")
            assertTrue(preview.canDelete)
            assertEquals(emptyList(), preview.blockers)
            assertTrue(preview.founder, "inscrit et vérifié : fondateur, statut perdu à la suppression")
            assertEquals(listOf("QA suppression du compte"), preview.ownedProjects.map { it.name })

            assertFailsWith<DomainException.InvalidCurrentPassword> { phone.accountDeletion.deleteAccount("PasLeBon1234!") }
            assertIs<AuthState.Authenticated>(phone.authState.state.value, "mot de passe faux : toujours connecté")
            assertTrue(phone.fileStore.storedPaths.isNotEmpty(), "mot de passe faux : rien d'effacé")

            phone.accountDeletion.deleteAccount(password)
            deleted = true

            assertEquals(AuthState.Unauthenticated, phone.authState.state.value)
            assertEquals(SessionNotice.AccountDeleted, phone.sessionNotices.notice.value)
            assertNull(phone.storage.get(), "jetons effacés")
            assertNull(phone.ownerStore.owner(), "nom et e-mail du compte oubliés")
            assertEquals(emptySet(), phone.fileStore.storedPaths, "fichiers du compte effacés")
            val rows = phone.db.everyRow()
            println("Suppression — base locale après :\n$rows")
            assertTrue(rows.lines().all { it.endsWith(": ") }, "plus aucune ligne en base, file de synchro comprise")

            assertFailsWith<DomainException.InvalidCredentials> { retryingOnRateLimit { phone.auth.login(email, password) } }
        } finally {
            if (!deleted) accounts.track(signedIn.user.id)
        }
    }
}
