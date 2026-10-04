package com.dmb.chantiertracker

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.SyncError
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.CreateConsumptionLineInput
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.CreatePurchaseLineInput
import com.dmb.chantiertracker.domain.model.CreateStageInput
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.UpdateProjectInput
import com.dmb.chantiertracker.support.DeviceStack
import com.dmb.chantiertracker.support.DisposableAccounts
import com.dmb.chantiertracker.support.IntegrationBackend
import com.dmb.chantiertracker.support.retryingOnRateLimit
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Campagne QA, groupe A — synchro et rejets serveur (P2, P3, P8), contre un vrai backend.
 *
 * Désactivé par défaut. Nécessite les identifiants super-admin (comptes jetables, voir
 * [DisposableAccounts]) :
 *
 *     -Dchantiertracker.integrationTests=true
 *     -Dchantiertracker.adminEmail=… -Dchantiertracker.adminPassword=…   (valeurs du .env backend)
 *
 * Chaque assertion décrit le comportement **attendu**. Un scénario rouge est un constat de la
 * campagne (voir docs/walkthrough/campagne-qa.md, groupe A), pas un test cassé : il passera au
 * vert avec le correctif correspondant. Tout ce qui est observé est aussi imprimé, pour que le
 * rapport reste lisible même quand une assertion échoue tôt.
 */
class SyncAndRejectionsIntegrationTest {

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

    private fun todayInParis(): String = LocalDate.now(ZoneId.of("Europe/Paris")).toString()

    private fun aSmallJpeg(): ByteArray {
        val image = BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 64) for (y in 0 until 48) image.setRGB(x, y, (x * 4 shl 16) or (y * 5 shl 8) or 0x40)
        return ByteArrayOutputStream().use { out -> ImageIO.write(image, "jpg", out); out.toByteArray() }
    }

    private suspend fun DeviceStack.signedInAs(prefix: String, name: String): DisposableAccounts.Account {
        val account = accounts.create(prefix, name)
        auth.signInForTheFirstTime(account, "QaPassword1234!")
        return account
    }

    private suspend fun DeviceStack.newProject(name: String) =
        projects.createProject(CreateProjectInput(name, null, "Nîmes", "EUR", "Europe/Paris"))

    // createPurchaseEntry / createWorkEntry return the DAY's local id; the entry is looked up in it.
    private suspend fun DeviceStack.entryOf(logLocalId: String, type: EntryType): String =
        logs.observeLog(logLocalId).first()!!.entries.single { it.type == type }.localId

    private suspend fun DeviceStack.newStage(projectLocalId: String, name: String) =
        stages.createStage(CreateStageInput(projectLocalId, name, null, null, null, null))

    // ─── P2 — a whole offline hierarchy, then one sync ───────────────────────

    @Test
    fun p2_a_whole_hierarchy_created_offline_reaches_the_server_in_one_sync() = runScenario {
        val phone = device()
        phone.signedInAs("qa-p2", "QA P2")

        phone.goOffline()
        val today = todayInParis()
        val projectId = phone.newProject("QA P2 chantier")
        val stageId = phone.newStage(projectId, "Gros œuvre")
        val dayId = phone.logs.createPurchaseEntry(stageId, today)
        phone.logs.createWorkEntry(stageId, today)
        val purchaseEntryId = phone.entryOf(dayId, EntryType.PURCHASE)
        val workEntryId = phone.entryOf(dayId, EntryType.WORK)
        val cement = phone.materials.createMaterial(projectId, "Ciment", "sac")
        val purchaseLineId = phone.purchaseLines.createLine(purchaseEntryId, CreatePurchaseLineInput(cement.localId, 10.0, 5.5, "Brico"))
        val consumptionLineId = phone.consumptionLines.createLine(workEntryId, CreateConsumptionLineInput(cement.localId, 4.0))
        val photo = phone.attachments.addAttachment(purchaseEntryId, aSmallJpeg(), "ticket.jpg", "image/jpeg")
        println("P2 — tout créé hors ligne, rien n'est parti : projet=${phone.db.projectDao().findByLocalId(projectId)?.syncStatus}")

        phone.goOnline()
        val outcome = phone.sync.syncNow()
        println("P2 — 1ʳᵉ synchro : $outcome")

        val project = phone.db.projectDao().findByLocalId(projectId)!!
        val stage = phone.db.stageDao().findByLocalId(stageId)!!
        val purchaseEntry = phone.db.dailyEntryDao().findByLocalId(purchaseEntryId)!!
        val workEntry = phone.db.dailyEntryDao().findByLocalId(workEntryId)!!
        val material = phone.db.materialDao().findByLocalId(cement.localId)!!
        val purchaseLine = phone.db.purchaseLineDao().findByLocalId(purchaseLineId)!!
        val consumptionLine = phone.db.consumptionLineDao().findByLocalId(consumptionLineId)!!
        val attachment = phone.db.attachmentDao().findByLocalId(photo.localId)!!
        val rows = listOf(
            "projet" to project.syncStatus, "étape" to stage.syncStatus, "entrée achat" to purchaseEntry.syncStatus,
            "entrée travaux" to workEntry.syncStatus, "matériau" to material.syncStatus,
            "ligne d'achat" to purchaseLine.syncStatus, "ligne de conso" to consumptionLine.syncStatus,
            "photo" to attachment.syncStatus,
        )
        println("P2 — après une synchro : " + rows.joinToString { "${it.first}=${it.second}" })
        rows.forEach { (what, status) -> assertEquals(SyncStatus.SYNCED, status, "$what synchronisé en une seule passe") }

        val serverLines = phone.purchaseLineApi.list(purchaseEntry.serverId!!).content
        val serverConsumption = phone.consumptionLineApi.list(workEntry.serverId!!).content
        val serverPhotos = phone.attachmentApi.list(purchaseEntry.serverId!!).content
        println("P2 — côté serveur : ${serverLines.size} ligne(s) d'achat, ${serverConsumption.size} conso, ${serverPhotos.size} photo(s)")
        assertEquals(1, serverLines.size)
        assertEquals(10.0, serverLines.single().quantity)
        assertEquals(1, serverConsumption.size)
        assertEquals(1, serverPhotos.size)

        val stock = phone.materials.observeStock(projectId).first().materials.single { it.materialLocalId == cement.localId }
        println("P2 — stock affiché après synchro : entrée=${stock.quantityIn} sortie=${stock.quantityOut} dispo=${stock.available}")
        assertEquals(6.0, stock.available)
    }

    // ─── P3 — server rejections: what is left on the device ──────────────────

    @Test
    fun p3_a_project_over_the_plan_limit_is_rejected_and_must_not_look_like_a_normal_project() = runScenario {
        val phone = device()
        val owner = phone.signedInAs("qa-p3-limit", "QA P3 Limite")

        phone.goOffline()
        val first = phone.newProject("QA P3 premier")
        val second = phone.newProject("QA P3 second (au-delà de la limite FREE)")
        val stageUnderSecond = phone.newStage(second, "Étape sous un projet refusé")
        phone.goOnline()
        phone.sync.syncNow()

        val rejected = phone.db.projectDao().findByLocalId(second)!!
        val stage = phone.db.stageDao().findByLocalId(stageUnderSecond)!!
        println("P3-limite — 1er projet : ${phone.db.projectDao().findByLocalId(first)?.syncStatus}")
        println("P3-limite — 2e projet : ${rejected.syncStatus} / ${rejected.lastSyncError} / serverId=${rejected.serverId}")
        println("P3-limite — étape sous le projet refusé : ${stage.syncStatus} / ${stage.pendingOp} / ${stage.lastSyncError}")
        assertEquals(SyncStatus.CONFLICTED, rejected.syncStatus)
        assertEquals(SyncError.PLAN_LIMIT, rejected.lastSyncError)

        val listed = phone.projects.observeProjects().first().single { it.localId == second }
        println("P3-limite — ce que reçoit l'écran de liste pour le projet refusé : $listed")

        // Une fois la limite levée, la synchro suivante doit le pousser sans intervention
        // (les lignes CONFLICTED sont représentées à chaque passe).
        accounts.grantPlan(owner.id, "SEMI_FLEX")
        phone.sync.syncNow()
        val retried = phone.db.projectDao().findByLocalId(second)
        val stageRetried = phone.db.stageDao().findByLocalId(stageUnderSecond)
        println("P3-limite — après passage en SEMI_FLEX : projet=${retried?.syncStatus}, étape=${stageRetried?.syncStatus}")
        assertEquals(SyncStatus.SYNCED, retried?.syncStatus, "limite levée : le projet refusé part à la synchro suivante")

        // Constat attendu : le modèle du domaine ne porte aucun état de synchro, l'écran ne peut
        // donc pas distinguer ce projet refusé d'un projet normal.
        assertTrue(
            listed.toString().contains("CONFLICTED") || listed.toString().contains("PLAN_LIMIT"),
            "l'écran doit pouvoir savoir que ce projet a été refusé par le serveur",
        )
    }

    @Test
    fun p3_a_consumption_beyond_the_stock_is_rejected_and_the_user_must_be_able_to_see_it() = runScenario {
        val phone = device()
        phone.signedInAs("qa-p3-stock", "QA P3 Stock")
        val today = todayInParis()
        val projectId = phone.newProject("QA P3 stock")
        val stageId = phone.newStage(projectId, "Maçonnerie")
        val cement = phone.materials.createMaterial(projectId, "Ciment", "sac")
        val purchase = phone.entryOf(phone.logs.createPurchaseEntry(stageId, today), EntryType.PURCHASE)
        phone.purchaseLines.createLine(purchase, CreatePurchaseLineInput(cement.localId, 2.0, 5.0, null))
        phone.sync.syncNow()

        phone.goOffline()
        val work = phone.entryOf(phone.logs.createWorkEntry(stageId, today), EntryType.WORK)
        val tooMuch = phone.consumptionLines.createLine(work, CreateConsumptionLineInput(cement.localId, 5.0))
        val stockOffline = phone.materials.observeStock(projectId).first().materials.single { it.materialLocalId == cement.localId }
        println("P3-stock — stock affiché hors ligne avant synchro : dispo=${stockOffline.available}")
        phone.goOnline()
        phone.sync.syncNow()

        val line = phone.db.consumptionLineDao().findByLocalId(tooMuch)!!
        val stockAfter = phone.materials.observeStock(projectId).first().materials.single { it.materialLocalId == cement.localId }
        val shownLines = phone.consumptionLines.observeLines(work).first()
        println("P3-stock — ligne de conso : ${line.syncStatus} / ${line.lastSyncError}")
        println("P3-stock — stock affiché après refus : dispo=${stockAfter.available} ; lignes affichées : $shownLines")
        assertEquals(SyncStatus.CONFLICTED, line.syncStatus, "409 stock insuffisant = rejet définitif")
        assertTrue(stockAfter.available >= 0.0, "le stock affiché ne doit pas rester négatif à cause d'une ligne refusée")
    }

    @Test
    fun p3_a_supervisor_entry_on_a_suspended_project_is_rejected_and_its_lines_must_not_wait_forever() = runScenario {
        val ownerPhone = device()
        ownerPhone.signedInAs("qa-p3-owner", "QA P3 Propriétaire")
        val supervisorPhone = device()
        val supervisor = supervisorPhone.signedInAs("qa-p3-super", "QA P3 Superviseur")

        val projectId = ownerPhone.newProject("QA P3 suspendu")
        val stageId = ownerPhone.newStage(projectId, "Charpente")
        ownerPhone.sync.syncNow()
        ownerPhone.invitations.invite(projectId, supervisor.email)
        val token = supervisorPhone.invitationApi.listMine().single().token
        supervisorPhone.invitations.acceptInvitation(token)
        supervisorPhone.sync.syncNow()
        val supervisorProject = supervisorPhone.projects.observeProjects().first().single()
        supervisorPhone.sync.syncProject(supervisorProject.localId)
        val supervisorStage = supervisorPhone.stages.observeStages(supervisorProject.localId).first().single()

        // The owner suspends the project while the supervisor is offline on site.
        val detail = ownerPhone.projects.observeProject(projectId).first()!!
        ownerPhone.projects.updateProject(
            projectId,
            UpdateProjectInput(detail.name, detail.description, detail.location, detail.currency, detail.timezone, ProjectStatus.SUSPENDED),
        )
        ownerPhone.sync.syncNow()

        supervisorPhone.goOffline()
        val entry = supervisorPhone.entryOf(supervisorPhone.logs.createPurchaseEntry(supervisorStage.localId, todayInParis()), EntryType.PURCHASE)
        val cement = supervisorPhone.materials.createMaterial(supervisorProject.localId, "Ciment", "sac")
        val line = supervisorPhone.purchaseLines.createLine(entry, CreatePurchaseLineInput(cement.localId, 3.0, 5.0, null))
        supervisorPhone.goOnline()
        supervisorPhone.sync.syncNow()
        supervisorPhone.sync.syncNow()

        val entryRow = supervisorPhone.db.dailyEntryDao().findByLocalId(entry)
        val lineRow = supervisorPhone.db.purchaseLineDao().findByLocalId(line)
        val materialRow = supervisorPhone.db.materialDao().findByLocalId(cement.localId)
        println("P3-suspendu — entrée : ${entryRow?.syncStatus} / ${entryRow?.lastSyncError}")
        println("P3-suspendu — matériau : ${materialRow?.syncStatus} / ${materialRow?.lastSyncError}")
        println("P3-suspendu — ligne : ${lineRow?.syncStatus} / ${lineRow?.pendingOp} / ${lineRow?.lastSyncError}")
        assertNotNull(entryRow)
        assertEquals(SyncStatus.CONFLICTED, entryRow.syncStatus, "projet suspendu : 403 = rejet définitif")
        assertTrue(
            lineRow?.syncStatus != SyncStatus.PENDING,
            "une ligne dont le parent est refusé ne doit pas rester en attente indéfiniment, sans signal",
        )
        stageId.let { }
    }

    @Test
    fun p3_a_refused_delete_comes_back_and_the_user_must_be_told_why() = runScenario {
        val ownerPhone = device()
        ownerPhone.signedInAs("qa-p3-del-owner", "QA P3 Suppr Propriétaire")
        val supervisorPhone = device()
        val supervisor = supervisorPhone.signedInAs("qa-p3-del-super", "QA P3 Suppr Superviseur")

        val projectId = ownerPhone.newProject("QA P3 suppression")
        val stageId = ownerPhone.newStage(projectId, "Électricité")
        val entry = ownerPhone.logs.createWorkEntry(stageId, todayInParis())
        ownerPhone.sync.syncNow()
        ownerPhone.invitations.invite(projectId, supervisor.email)
        supervisorPhone.invitations.acceptInvitation(supervisorPhone.invitationApi.listMine().single().token)
        supervisorPhone.sync.syncNow()
        val supervisorProject = supervisorPhone.projects.observeProjects().first().single()
        supervisorPhone.sync.syncProject(supervisorProject.localId)
        val supervisorStage = supervisorPhone.stages.observeStages(supervisorProject.localId).first().single()
        supervisorPhone.sync.syncStage(supervisorStage.localId)
        val supervisorLog = supervisorPhone.logs.observeLogs(supervisorStage.localId).first().single()
        supervisorPhone.sync.syncLog(supervisorLog.localId)
        val supervisorEntry = supervisorPhone.logs.observeLog(supervisorLog.localId).first()!!.entries.single()

        // Deleting is reserved to the ADMIN on the server; the repository still lets anyone queue it.
        supervisorPhone.db.dailyEntryDao().findByLocalId(supervisorEntry.localId)!!.let { row ->
            supervisorPhone.db.dailyEntryDao().upsert(row.copy(pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING))
        }
        supervisorPhone.sync.syncNow()

        val after = supervisorPhone.db.dailyEntryDao().findByLocalId(supervisorEntry.localId)
        val visible = supervisorPhone.logs.observeLog(supervisorLog.localId).first()?.entries.orEmpty()
        println("P3-suppression — entrée après refus : ${after?.syncStatus} / ${after?.pendingOp} / ${after?.lastSyncError} ; visible=${visible.map { it.localId }}")
        assertNotNull(after)
        assertEquals(PendingOp.NONE, after.pendingOp, "la suppression refusée est annulée (ADR-62/63)")
        assertTrue(
            visible.toString().contains("REJECTED"),
            "l'entrée réapparaît : l'écran doit pouvoir dire que la suppression a été refusée",
        )
        entry.let { }
    }

    // ─── P8 — session expired / another account on the same device ───────────

    @Test
    fun p8_writes_pending_when_the_session_is_revoked_are_pushed_after_signing_back_in() = runScenario {
        val phone = device()
        val account = phone.signedInAs("qa-p8-session", "QA P8 Session")
        val projectId = phone.newProject("QA P8 chantier")
        phone.sync.syncNow()

        phone.goOffline()
        val stageId = phone.newStage(projectId, "Étape saisie juste avant l'expiration")

        // The password is changed from another device: every session is revoked server-side.
        val otherDevice = device()
        retryingOnRateLimit { otherDevice.auth.login(account.email, "QaPassword1234!") }
        otherDevice.auth.changePassword("QaPassword1234!", "QaPassNew5678!")

        phone.goOnline()
        phone.sync.syncNow()
        val stateAfter = phone.authState.state.value
        val stageAfter = phone.db.stageDao().findByLocalId(stageId)
        println("P8-session — état d'auth : $stateAfter ; étape en attente : ${stageAfter?.syncStatus} / ${stageAfter?.pendingOp}")
        assertEquals(AuthState.Unauthenticated, stateAfter, "jeton révoqué → retour à la connexion")
        assertEquals(SyncStatus.PENDING, stageAfter?.syncStatus, "la saisie locale n'est pas perdue")

        retryingOnRateLimit { phone.auth.login(account.email, "QaPassNew5678!") }
        phone.sync.syncNow()
        val pushed = phone.db.stageDao().findByLocalId(stageId)
        println("P8-session — après reconnexion : ${pushed?.syncStatus} serverId=${pushed?.serverId}")
        assertEquals(SyncStatus.SYNCED, pushed?.syncStatus, "poussée après reconnexion du même compte")
    }

    @Test
    fun p8_another_account_signing_in_on_the_same_device_never_sees_or_pushes_the_previous_data() = runScenario {
        val phone = device()
        phone.signedInAs("qa-p8-alice", "QA P8 Alice")
        val aliceProject = phone.newProject("Chantier privé d'Alice")
        phone.sync.syncNow()
        phone.goOffline()
        val alicePending = phone.newProject("Projet d'Alice resté en attente")
        val aliceProjectServerId = phone.db.projectDao().findByLocalId(aliceProject)?.serverId
        phone.goOnline()
        phone.auth.logout()

        val bob = accounts.create("qa-p8-bob", "QA P8 Bob")
        phone.auth.signInForTheFirstTime(bob, "QaPassword1234!")
        val shownToBob = phone.projects.observeProjects().first().map { it.name }
        println("P8-compte — projets affichés à Bob juste après sa connexion : $shownToBob")

        phone.sync.syncNow()
        val shownAfterSync = phone.projects.observeProjects().first().map { it.name }
        val bobOnServer = phone.projectApi.list().content.map { it.name }
        val pendingRow = phone.db.projectDao().findByLocalId(alicePending)
        println("P8-compte — après la synchro de Bob : affichés=$shownAfterSync ; côté serveur pour Bob=$bobOnServer ; ligne d'Alice=${pendingRow?.syncStatus}")
        assertTrue("Chantier privé d'Alice" !in shownToBob, "Bob ne doit jamais voir les projets d'Alice, même avant la synchro")
        assertTrue("Projet d'Alice resté en attente" !in bobOnServer, "la saisie en attente d'Alice ne doit jamais être créée sur le compte de Bob")
        assertTrue(aliceProjectServerId != null)
        assertIs<AuthState.Authenticated>(phone.authState.state.value)
    }

    // ─── A-1 (ADR-69) — sign-out guard and session switch, against the real backend ──

    @Test
    fun p8_a_voluntary_sign_out_is_blocked_offline_while_writes_are_unsent_then_goes_through() = runScenario {
        val phone = device()
        phone.signedInAs("qa-p8-logout", "QA P8 Déconnexion")
        val projectId = phone.newProject("QA P8 déconnexion")
        phone.sync.syncNow()

        phone.goOffline()
        val stageId = phone.newStage(projectId, "Saisie faite hors ligne")
        val blocked = phone.signOut.signOut()
        println("P8-déconnexion — hors ligne : $blocked ; état d'auth=${phone.authState.state.value}")
        assertIs<com.dmb.chantiertracker.domain.repository.SignOutResult.Blocked>(blocked)
        assertIs<AuthState.Authenticated>(phone.authState.state.value, "toujours connecté, jeton conservé")
        assertNotNull(phone.storage.get())

        phone.goOnline()
        val result = phone.signOut.signOut()
        val stage = phone.db.stageDao().findByLocalId(stageId)
        println("P8-déconnexion — en ligne : $result ; étape=${stage?.syncStatus} serverId=${stage?.serverId}")
        assertEquals(com.dmb.chantiertracker.domain.repository.SignOutResult.SignedOut, result)
        assertEquals(SyncStatus.SYNCED, stage?.syncStatus, "envoyée avant la déconnexion")
        assertEquals(AuthState.Unauthenticated, phone.authState.state.value)
    }

    @Test
    fun p8_after_an_expired_session_another_account_finds_an_empty_device_and_nothing_of_the_previous_one() = runScenario {
        val phone = device()
        val alice = phone.signedInAs("qa-p8-exp-alice", "QA P8 Alice expirée")
        val projectId = phone.newProject("Projet synchronisé d'Alice")
        phone.sync.syncNow()
        phone.goOffline()
        val pendingId = phone.newProject("Saisie en attente d'Alice")
        phone.fileStore.save(aSmallJpeg(), "photo-alice.jpg")

        // Alice's session is revoked from another device while hers is offline.
        val other = device()
        retryingOnRateLimit { other.auth.login(alice.email, "QaPassword1234!") }
        other.auth.changePassword("QaPassword1234!", "QaPasswordNew5678!")
        phone.goOnline()
        phone.sync.syncNow()
        assertEquals(AuthState.Unauthenticated, phone.authState.state.value, "session expirée")

        val bob = accounts.create("qa-p8-exp-bob", "QA P8 Bob")
        phone.auth.signInForTheFirstTime(bob, "QaPassword1234!")
        val shownToBob = phone.projects.observeProjects().first().map { it.name }
        phone.sync.syncNow()
        val bobOnServer = phone.projectApi.list().content.map { it.name }
        println("P8-expirée — affiché à Bob : $shownToBob ; côté serveur pour Bob : $bobOnServer ; fichiers locaux : ${phone.fileStore.storedPaths}")
        assertTrue(shownToBob.isEmpty(), "rien d'Alice n'est montré à Bob")
        assertTrue("Saisie en attente d'Alice" !in bobOnServer, "la saisie d'Alice n'est jamais créée sur le compte de Bob")
        assertTrue(phone.fileStore.storedPaths.isEmpty(), "les fichiers de justificatifs d'Alice sont effacés")
        assertTrue(phone.db.projectDao().findByLocalId(pendingId) == null && phone.db.projectDao().findByLocalId(projectId) == null)
    }
}

