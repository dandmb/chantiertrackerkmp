package com.dmb.chantiertracker

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.domain.model.actions
import com.dmb.chantiertracker.presentation.logs.asUploadFile
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.SyncIssueAction
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.canBeRetried
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
import io.ktor.client.plugins.plugin
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
        com.dmb.chantiertracker.support.skipUnlessRealBackendScenariosAreEnabled()
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
        val purchaseEntryId = phone.logs.createPurchaseEntry(stageId, today).entryLocalId
        val workEntryId = phone.logs.createWorkEntry(stageId, today).entryLocalId
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

        val serverLines = phone.purchaseLineApi.list(purchaseEntry.serverId!!, page = 0, size = 100).content
        val serverConsumption = phone.consumptionLineApi.list(workEntry.serverId!!, page = 0, size = 100).content
        val serverPhotos = phone.attachmentApi.list(purchaseEntry.serverId!!, page = 0, size = 100).content
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
        val stageListed = phone.stages.observeStages(second).first().single()
        println("P3-limite — ce que reçoit l'écran pour l'étape créée sous ce projet : $stageListed")

        // Une fois la limite levée, la synchro suivante doit le pousser sans intervention
        // (les lignes CONFLICTED sont représentées à chaque passe).
        accounts.grantPlan(owner.id, "SEMI_FLEX")
        phone.sync.syncNow()
        val retried = phone.db.projectDao().findByLocalId(second)
        val stageRetried = phone.db.stageDao().findByLocalId(stageUnderSecond)
        println("P3-limite — après passage en SEMI_FLEX : projet=${retried?.syncStatus}, étape=${stageRetried?.syncStatus}")
        assertEquals(SyncStatus.SYNCED, retried?.syncStatus, "limite levée : le projet refusé part à la synchro suivante")

        assertEquals(
            com.dmb.chantiertracker.domain.model.SyncIssue(com.dmb.chantiertracker.domain.model.SyncIssueKind.REFUSED, com.dmb.chantiertracker.domain.model.RefusalReason.PLAN_LIMIT, "PLAN_LIMIT_EXCEEDED"),
            listed.syncIssue,
            "l'écran doit pouvoir savoir que ce projet a été refusé par le serveur, et pourquoi",
        )
        assertEquals(com.dmb.chantiertracker.domain.model.SyncIssueKind.BLOCKED_BY_PARENT, stageListed.syncIssue?.kind, "l'étape sous le projet refusé est signalée comme en attente de lui")
        assertEquals(null, phone.projects.observeProjects().first().single { it.localId == second }.syncIssue, "plus rien à signaler une fois le projet accepté")
        assertEquals(null, phone.stages.observeStages(second).first().single().syncIssue)
    }

    @Test
    fun p3_a_consumption_beyond_the_stock_is_rejected_and_the_user_must_be_able_to_see_it() = runScenario {
        val phone = device()
        phone.signedInAs("qa-p3-stock", "QA P3 Stock")
        val today = todayInParis()
        val projectId = phone.newProject("QA P3 stock")
        val stageId = phone.newStage(projectId, "Maçonnerie")
        val cement = phone.materials.createMaterial(projectId, "Ciment", "sac")
        val purchase = phone.logs.createPurchaseEntry(stageId, today).entryLocalId
        phone.purchaseLines.createLine(purchase, CreatePurchaseLineInput(cement.localId, 2.0, 5.0, null))
        phone.sync.syncNow()

        phone.goOffline()
        val work = phone.logs.createWorkEntry(stageId, today).entryLocalId
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
        assertEquals(
            com.dmb.chantiertracker.domain.model.SyncIssue(com.dmb.chantiertracker.domain.model.SyncIssueKind.REFUSED, com.dmb.chantiertracker.domain.model.RefusalReason.INSUFFICIENT_STOCK, "INSUFFICIENT_STOCK"),
            shownLines.single().syncIssue,
            "la ligne refusée doit se distinguer des autres, avec sa raison",
        )
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
        val entry = supervisorPhone.logs.createPurchaseEntry(supervisorStage.localId, todayInParis()).entryLocalId
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
        val shownEntry = supervisorPhone.logs.observeEntry(entry).first()
        val shownLine = supervisorPhone.purchaseLines.observeLines(entry).first().single()
        println("P3-suspendu — ce que reçoit l'écran : entrée=${shownEntry?.syncIssue} ; ligne=${shownLine.syncIssue}")
        assertEquals(
            com.dmb.chantiertracker.domain.model.SyncIssue(com.dmb.chantiertracker.domain.model.SyncIssueKind.REFUSED, com.dmb.chantiertracker.domain.model.RefusalReason.PROJECT_OR_STAGE_INACTIVE, "PROJECT_OR_STAGE_INACTIVE"),
            shownEntry?.syncIssue,
            "l'entrée refusée porte la raison du serveur",
        )
        assertEquals(
            com.dmb.chantiertracker.domain.model.SyncIssueKind.BLOCKED_BY_PARENT,
            shownLine.syncIssue?.kind,
            "une ligne dont le parent est refusé ne doit pas rester en attente sans signal : elle est signalée comme en attente de lui (ADR-74, décision 2)",
        )
        stageId.let { }
    }

    @Test
    fun adr74_an_entry_refused_on_a_suspended_project_is_listed_to_review_and_leaves_with_retry_once_the_project_is_reopened() = runScenario {
        val ownerPhone = device()
        ownerPhone.signedInAs("qa-74-owner", "QA 74 Propriétaire")
        val supervisorPhone = device()
        val supervisor = supervisorPhone.signedInAs("qa-74-super", "QA 74 Superviseur")

        val projectId = ownerPhone.newProject("QA 74 à revoir")
        ownerPhone.newStage(projectId, "Charpente")
        ownerPhone.sync.syncNow()
        ownerPhone.invitations.invite(projectId, supervisor.email)
        val token = supervisorPhone.invitationApi.listMine().single().token
        supervisorPhone.invitations.acceptInvitation(token)
        supervisorPhone.sync.syncNow()
        val supervisorProject = supervisorPhone.projects.observeProjects().first().single()
        supervisorPhone.sync.syncProject(supervisorProject.localId)
        val supervisorStage = supervisorPhone.stages.observeStages(supervisorProject.localId).first().single()

        suspend fun ownerSetsStatus(status: ProjectStatus) {
            val detail = ownerPhone.projects.observeProject(projectId).first()!!
            ownerPhone.projects.updateProject(
                projectId,
                UpdateProjectInput(detail.name, detail.description, detail.location, detail.currency, detail.timezone, status),
            )
            assertEquals(SyncOutcome.Synced, ownerPhone.sync.syncNow())
        }
        ownerSetsStatus(ProjectStatus.SUSPENDED)

        supervisorPhone.goOffline()
        val today = todayInParis()
        val entry = supervisorPhone.logs.createPurchaseEntry(supervisorStage.localId, today).entryLocalId
        val cement = supervisorPhone.materials.createMaterial(supervisorProject.localId, "Ciment", "sac")
        val line = supervisorPhone.purchaseLines.createLine(entry, CreatePurchaseLineInput(cement.localId, 3.0, 5.0, null))
        supervisorPhone.goOnline()
        supervisorPhone.sync.syncNow()
        supervisorPhone.sync.syncNow()

        val issues = supervisorPhone.syncIssues
        val listed = issues.observeIssues().first()
        println("ADR-74 tranche 2 — à revoir, projet suspendu : " + listed.joinToString { "${it.target} ${it.issue.kind} ${it.issue.reason}" })
        val refusedEntry = listed.single { it.target == com.dmb.chantiertracker.domain.model.SyncIssueTarget.ENTRY }
        assertEquals(entry, refusedEntry.localId)
        assertEquals(com.dmb.chantiertracker.domain.model.SyncIssueKind.REFUSED, refusedEntry.issue.kind)
        assertEquals(com.dmb.chantiertracker.domain.model.RefusalReason.PROJECT_OR_STAGE_INACTIVE, refusedEntry.issue.reason)
        assertTrue(refusedEntry.issue.canBeRetried, "un refus qui dépend du propriétaire propose « Réessayer »")
        assertEquals(
            listOf(supervisorProject.localId, "QA 74 à revoir", supervisorStage.localId, "Charpente", today, EntryType.PURCHASE),
            listOf(refusedEntry.projectLocalId, refusedEntry.projectName, refusedEntry.stageLocalId, refusedEntry.stageName, refusedEntry.date, refusedEntry.entryType),
            "la saisie est rangée sous son projet, son étape et sa journée",
        )
        val waitingLine = listed.single { it.target == com.dmb.chantiertracker.domain.model.SyncIssueTarget.PURCHASE_LINE }
        assertEquals(line, waitingLine.localId)
        assertEquals(com.dmb.chantiertracker.domain.model.SyncIssueKind.BLOCKED_BY_PARENT, waitingLine.issue.kind)
        assertEquals(listOf("Ciment", "sac", 3.0), listOf(waitingLine.label, waitingLine.unit, waitingLine.quantity))
        assertEquals(
            com.dmb.chantiertracker.domain.model.SyncIssueParent(com.dmb.chantiertracker.domain.model.SyncIssueTarget.ENTRY, name = null, entryType = EntryType.PURCHASE, date = today),
            waitingLine.blockedBy,
            "la ligne en attente nomme la saisie refusée dont elle dépend",
        )
        assertEquals(
            com.dmb.chantiertracker.domain.model.SyncIssueParent(com.dmb.chantiertracker.domain.model.SyncIssueTarget.MATERIAL, name = "Ciment"),
            waitingLine.alsoBlockedBy,
            "et le matériau refusé dont elle dépend aussi (R2)",
        )
        // Depuis BE-3 (backend e81efb0), un matériau créé sur un projet suspendu est refusé lui aussi.
        val refusedMaterial = listed.single { it.target == com.dmb.chantiertracker.domain.model.SyncIssueTarget.MATERIAL }
        println("ADR-74 — matériau refusé : ${refusedMaterial.label} ${refusedMaterial.issue.kind} ${refusedMaterial.issue.reason} réessayable=${refusedMaterial.issue.canBeRetried} actions=${refusedMaterial.actions} projet=${refusedMaterial.projectName} étape=${refusedMaterial.stageName} jour=${refusedMaterial.date}")
        println("ADR-74 — ligne en attente : actions=${waitingLine.actions} attend=${waitingLine.blockedBy}")
        println("ADR-74 — saisie refusée : actions=${refusedEntry.actions}")
        assertEquals(cement.localId, refusedMaterial.localId)
        assertEquals(com.dmb.chantiertracker.domain.model.SyncIssueKind.REFUSED, refusedMaterial.issue.kind)
        assertEquals(com.dmb.chantiertracker.domain.model.RefusalReason.PROJECT_OR_STAGE_INACTIVE, refusedMaterial.issue.reason)
        assertTrue(refusedMaterial.issue.canBeRetried, "le matériau aussi se réessaie une fois le projet rouvert")
        assertEquals(listOf("Ciment", supervisorProject.localId, "QA 74 à revoir"), listOf(refusedMaterial.label, refusedMaterial.projectLocalId, refusedMaterial.projectName))
        assertEquals(3, listed.size, "l'écran liste la saisie refusée, le matériau refusé et la ligne en attente")
        assertEquals(2, issues.observeIssueCount().first(), "la pastille compte les deux refus, pas la ligne en attente")

        val whileSuspended = issues.retry(refusedEntry)
        println("ADR-74 tranche 2 — « Réessayer » projet encore suspendu : $whileSuspended")
        assertEquals(com.dmb.chantiertracker.domain.repository.RetryOutcome.STILL_REFUSED, whileSuspended)
        assertTrue(issues.observeIssues().first().any { it.localId == entry }, "toujours listée tant que le projet est suspendu")

        supervisorPhone.goOffline()
        assertEquals(com.dmb.chantiertracker.domain.repository.RetryOutcome.NOT_SENT, issues.retry(refusedEntry), "hors ligne, rien ne part et la saisie reste listée")
        assertTrue(issues.observeIssues().first().any { it.localId == entry })
        supervisorPhone.goOnline()

        ownerSetsStatus(ProjectStatus.IN_PROGRESS)

        val afterReopening = issues.retry(refusedEntry)
        println("ADR-74 tranche 2 — « Réessayer » projet rouvert : $afterReopening")
        assertEquals(com.dmb.chantiertracker.domain.repository.RetryOutcome.ACCEPTED, afterReopening)
        println("ADR-74 — après « Réessayer » sur la saisie : ${issues.observeIssues().first().joinToString { "${it.target} ${it.issue.kind}" }}")
        supervisorPhone.sync.syncNow()
        val remaining = issues.observeIssues().first()
        println("ADR-74 tranche 2 — à revoir après réouverture : ${remaining.size}")
        assertTrue(remaining.isEmpty(), "projet rouvert puis « Réessayer » : plus rien à revoir")
        assertEquals(0, issues.observeIssueCount().first())
        assertEquals(SyncStatus.SYNCED, supervisorPhone.db.dailyEntryDao().findByLocalId(entry)?.syncStatus)
        assertEquals(SyncStatus.SYNCED, supervisorPhone.db.purchaseLineDao().findByLocalId(line)?.syncStatus)
        assertEquals(SyncStatus.SYNCED, supervisorPhone.db.materialDao().findByLocalId(cement.localId)?.syncStatus)
        assertEquals(com.dmb.chantiertracker.domain.repository.SignOutResult.SignedOut, supervisorPhone.signOut.signOut(), "plus rien ne retient la déconnexion")
    }

    @Test
    fun adr74_discarding_a_refused_entry_removes_it_with_its_lines_and_its_photo_and_nothing_of_it_ever_reaches_the_server() = runScenario {
        val ownerPhone = device()
        ownerPhone.signedInAs("qa-74d-owner", "QA 74 Abandon Propriétaire")
        val supervisorPhone = device()
        val supervisor = supervisorPhone.signedInAs("qa-74d-super", "QA 74 Abandon Superviseur")

        val projectId = ownerPhone.newProject("QA 74 abandon")
        ownerPhone.newStage(projectId, "Charpente")
        ownerPhone.sync.syncNow()
        ownerPhone.invitations.invite(projectId, supervisor.email)
        supervisorPhone.invitations.acceptInvitation(supervisorPhone.invitationApi.listMine().single().token)
        supervisorPhone.sync.syncNow()
        val supervisorProject = supervisorPhone.projects.observeProjects().first().single()
        supervisorPhone.sync.syncProject(supervisorProject.localId)
        val supervisorStage = supervisorPhone.stages.observeStages(supervisorProject.localId).first().single()
        val cement = supervisorPhone.materials.createMaterial(supervisorProject.localId, "Ciment", "sac")
        supervisorPhone.sync.syncNow()

        suspend fun ownerSetsStatus(status: ProjectStatus) {
            val detail = ownerPhone.projects.observeProject(projectId).first()!!
            ownerPhone.projects.updateProject(
                projectId,
                UpdateProjectInput(detail.name, detail.description, detail.location, detail.currency, detail.timezone, status),
            )
            assertEquals(SyncOutcome.Synced, ownerPhone.sync.syncNow())
        }
        ownerSetsStatus(ProjectStatus.SUSPENDED)

        supervisorPhone.goOffline()
        val created = supervisorPhone.logs.createPurchaseEntry(supervisorStage.localId, todayInParis())
        val day = created.dailyLogLocalId
        val entry = created.entryLocalId
        val firstLine = supervisorPhone.purchaseLines.createLine(entry, CreatePurchaseLineInput(cement.localId, 3.0, 5.0, null))
        val secondLine = supervisorPhone.purchaseLines.createLine(entry, CreatePurchaseLineInput(cement.localId, 2.0, 5.0, null))
        val photo = supervisorPhone.attachments.addAttachment(entry, aSmallJpeg(), "bon.jpg", "image/jpeg")
        supervisorPhone.goOnline()
        supervisorPhone.sync.syncNow()
        supervisorPhone.sync.syncNow()

        val issues = supervisorPhone.syncIssues
        val refusedEntry = issues.observeIssues().first().single { it.localId == entry }
        val linked = issues.linkedCount(refusedEntry)
        println("ADR-74 tranche 3 — abandon : ${issues.observeIssues().first().size} éléments listés, actions de la saisie=${refusedEntry.actions}, liés=$linked")
        assertEquals(4, issues.observeIssues().first().size, "la saisie refusée, ses deux lignes et sa photo en attente")
        assertEquals(1, issues.observeIssueCount().first())
        assertEquals(listOf(com.dmb.chantiertracker.domain.model.SyncIssueAction.RETRY, com.dmb.chantiertracker.domain.model.SyncIssueAction.DISCARD), refusedEntry.actions)
        assertEquals(3, linked, "le dialogue annonce 3 saisies liées : 2 lignes et 1 photo")
        assertEquals(1, supervisorPhone.fileStore.storedPaths.size, "le fichier de la photo est sur l'appareil")

        issues.discard(refusedEntry)
        issues.discard(refusedEntry)

        assertTrue(issues.observeIssues().first().isEmpty(), "plus rien à revoir")
        assertEquals(0, issues.observeIssueCount().first())
        assertEquals(
            listOf<Any?>(null, null, null, null, null),
            listOf(
                supervisorPhone.db.dailyEntryDao().findByLocalId(entry), supervisorPhone.db.purchaseLineDao().findByLocalId(firstLine),
                supervisorPhone.db.purchaseLineDao().findByLocalId(secondLine), supervisorPhone.db.attachmentDao().findByLocalId(photo.localId),
                supervisorPhone.db.dailyLogDao().findByLocalId(day),
            ),
            "la saisie, ses lignes, sa photo et sa journée locale sont supprimées",
        )
        assertTrue(supervisorPhone.fileStore.storedPaths.isEmpty(), "le fichier local de la photo est supprimé")
        assertEquals(com.dmb.chantiertracker.domain.model.UnsentWrites(), supervisorPhone.db.localDataDao().countUnsentByKind().let {
            com.dmb.chantiertracker.domain.model.UnsentWrites(it.projects, it.stages, it.materials, it.entries, it.lines, it.attachments)
        }, "rien ne reste en file")

        ownerSetsStatus(ProjectStatus.IN_PROGRESS)
        assertEquals(SyncOutcome.Synced, supervisorPhone.sync.syncNow())
        assertEquals(SyncOutcome.Synced, supervisorPhone.sync.syncProject(supervisorProject.localId))
        ownerPhone.sync.syncProject(projectId)
        val ownerStage = ownerPhone.stages.observeStages(projectId).first().single()
        ownerPhone.sync.syncStage(ownerStage.localId)
        val daysOnServer = ownerPhone.logs.observeLogs(ownerStage.localId).first()
        println("ADR-74 tranche 3 — abandon : journées vues par le propriétaire après réouverture = ${daysOnServer.size}")
        assertTrue(daysOnServer.isEmpty(), "projet rouvert : rien de la saisie abandonnée n'est arrivé sur le serveur")
        assertEquals(com.dmb.chantiertracker.domain.repository.SignOutResult.SignedOut, supervisorPhone.signOut.signOut())
    }

    @Test
    fun p3_a_refused_delete_comes_back_and_the_user_must_be_told_why() = runScenario {
        val ownerPhone = device()
        ownerPhone.signedInAs("qa-p3-del-owner", "QA P3 Suppr Propriétaire")
        val supervisorPhone = device()
        val supervisor = supervisorPhone.signedInAs("qa-p3-del-super", "QA P3 Suppr Superviseur")

        val projectId = ownerPhone.newProject("QA P3 suppression")
        val stageId = ownerPhone.newStage(projectId, "Électricité")
        ownerPhone.logs.createWorkEntry(stageId, todayInParis())
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
        assertEquals(
            com.dmb.chantiertracker.domain.model.SyncIssueKind.DELETE_REFUSED,
            visible.single().syncIssue?.kind,
            "l'entrée réapparaît : l'écran doit pouvoir dire que la suppression a été refusée",
        )
        supervisorPhone.sync.syncLog(supervisorLog.localId)
        supervisorPhone.sync.syncNow()
        val afterPulls = supervisorPhone.logs.observeLog(supervisorLog.localId).first()!!.entries.single()
        println("P3-suppression — après deux nouvelles synchros : ${afterPulls.syncIssue}")
        assertEquals(visible.single().syncIssue, afterPulls.syncIssue, "la mention reste tant que l'utilisateur ne l'a pas acquittée")
    }

    @Test
    fun adr74_got_it_on_a_refused_delete_brings_the_entry_back_with_the_server_version_online_and_after_reconnection_offline() = runScenario {
        val ownerPhone = device()
        ownerPhone.signedInAs("qa-74g-owner", "QA 74 Compris Propriétaire")
        val supervisorPhone = device()
        val supervisor = supervisorPhone.signedInAs("qa-74g-super", "QA 74 Compris Superviseur")

        val projectId = ownerPhone.newProject("QA 74 compris")
        val stageId = ownerPhone.newStage(projectId, "Électricité")
        val ownerDay = ownerPhone.logs.createWorkEntry(stageId, todayInParis()).dailyLogLocalId
        ownerPhone.logs.createPurchaseEntry(stageId, todayInParis())
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
        val entryDao = supervisorPhone.db.dailyEntryDao()
        supervisorPhone.logs.observeLog(supervisorLog.localId).first()!!.entries.forEach { entry ->
            entryDao.upsert(entryDao.findByLocalId(entry.localId)!!.copy(pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING))
        }
        supervisorPhone.sync.syncNow()

        val issues = supervisorPhone.syncIssues
        val refused = issues.observeIssues().first()
        println("ADR-74 tranche 3bis — j'ai compris : ${refused.map { "${it.entryType} ${it.issue.kind} ${it.issue.reason} actions=${it.actions}" }}")
        assertEquals(2, refused.size)
        refused.forEach {
            assertEquals(com.dmb.chantiertracker.domain.model.SyncIssueKind.DELETE_REFUSED, it.issue.kind)
            assertEquals(listOf(com.dmb.chantiertracker.domain.model.SyncIssueAction.ACKNOWLEDGE), it.actions)
        }
        val work = refused.single { it.entryType == com.dmb.chantiertracker.domain.model.EntryType.WORK }
        val purchase = refused.single { it.entryType == com.dmb.chantiertracker.domain.model.EntryType.PURCHASE }
        val before = listOf(work, purchase).map { entryDao.findByLocalId(it.localId)!! }
        println("ADR-74 tranche 3bis — état local après le refus, avant l'action : ${before.map { "${it.type} ${it.syncStatus}/${it.pendingOp}/${it.lastSyncError} résumé=${it.summary}" }}")

        ownerPhone.waitForTheRunningSyncPass()
        val ownerEntries = ownerPhone.logs.observeLog(ownerDay).first()!!.entries
        ownerPhone.logs.updateEntry(ownerEntries.single { it.type == com.dmb.chantiertracker.domain.model.EntryType.WORK }.localId, "Câblage du tableau")
        ownerPhone.logs.updateEntry(ownerEntries.single { it.type == com.dmb.chantiertracker.domain.model.EntryType.PURCHASE }.localId, "Achat de gaines")
        ownerPhone.sync.syncNow()

        issues.acknowledge(work)
        val workOnline = entryDao.findByLocalId(work.localId)!!
        println("ADR-74 tranche 3bis — j'ai compris en ligne : ${workOnline.syncStatus}/${workOnline.pendingOp}/${workOnline.lastSyncError} résumé=${workOnline.summary}")
        assertEquals<List<Any?>>(listOf("Câblage du tableau", SyncStatus.SYNCED, PendingOp.NONE, null), listOf(workOnline.summary, workOnline.syncStatus, workOnline.pendingOp, workOnline.lastSyncError), "en ligne : la version du serveur est là tout de suite")

        supervisorPhone.goOffline()
        issues.acknowledge(purchase)
        val purchaseOffline = entryDao.findByLocalId(purchase.localId)!!
        val visibleOffline = supervisorPhone.logs.observeLog(supervisorLog.localId).first()!!.entries
        println("ADR-74 tranche 3bis — j'ai compris hors ligne : ${purchaseOffline.syncStatus}/${purchaseOffline.pendingOp}/${purchaseOffline.lastSyncError} résumé=${purchaseOffline.summary} ; visibles=${visibleOffline.size}")
        assertEquals(before[1].summary, purchaseOffline.summary, "hors ligne : la dernière version connue reste affichée")
        assertEquals(listOf<Any?>(true, null), listOf(purchaseOffline.awaitsServerVersion, purchaseOffline.lastSyncError), "le rafraîchissement est noté en base, hors de la colonne d'erreur")
        assertEquals(2, visibleOffline.size, "les deux saisies sont visibles")
        assertTrue(visibleOffline.all { it.syncIssue == null }, "sans mention")
        assertTrue(issues.observeIssues().first().isEmpty(), "plus rien à revoir")
        assertEquals(0, issues.observeIssueCount().first())

        supervisorPhone.goOnline()
        supervisorPhone.sync.syncNow()
        val purchaseRefreshed = entryDao.findByLocalId(purchase.localId)!!
        println("ADR-74 tranche 3bis — après reconnexion : ${purchaseRefreshed.syncStatus}/${purchaseRefreshed.lastSyncError} résumé=${purchaseRefreshed.summary}")
        assertEquals<List<Any?>>(listOf("Achat de gaines", SyncStatus.SYNCED, null), listOf(purchaseRefreshed.summary, purchaseRefreshed.syncStatus, purchaseRefreshed.lastSyncError), "au retour du réseau : la version du serveur")
        assertTrue(issues.observeIssues().first().isEmpty())

        val ownerLog = ownerPhone.logs.observeLogs(stageId).first().single()
        ownerPhone.sync.syncLog(ownerLog.localId)
        assertEquals(2, ownerPhone.logs.observeLog(ownerLog.localId).first()!!.entries.size, "le serveur a toujours les deux saisies")
    }

    // ─── P8 — session expired / another account on the same device ───────────

    @Test
    fun adr74_markers_show_the_refused_entry_and_its_waiting_children_then_leave_once_the_server_accepts_them() = runScenario {
        val ownerPhone = device()
        ownerPhone.signedInAs("qa-74m-owner", "QA 74 marqueurs Propriétaire")
        val supervisorPhone = device()
        val supervisor = supervisorPhone.signedInAs("qa-74m-super", "QA 74 marqueurs Superviseur")

        val projectId = ownerPhone.newProject("QA 74 marqueurs")
        ownerPhone.newStage(projectId, "Charpente")
        ownerPhone.sync.syncNow()
        ownerPhone.invitations.invite(projectId, supervisor.email)
        val token = supervisorPhone.invitationApi.listMine().single().token
        supervisorPhone.invitations.acceptInvitation(token)
        supervisorPhone.sync.syncNow()
        val supervisorProject = supervisorPhone.projects.observeProjects().first().single()
        supervisorPhone.sync.syncProject(supervisorProject.localId)
        val supervisorStage = supervisorPhone.stages.observeStages(supervisorProject.localId).first().single()

        suspend fun ownerSetsStatus(status: ProjectStatus) {
            val detail = ownerPhone.projects.observeProject(projectId).first()!!
            ownerPhone.projects.updateProject(projectId, UpdateProjectInput(detail.name, detail.description, detail.location, detail.currency, detail.timezone, status))
            assertEquals(SyncOutcome.Synced, ownerPhone.sync.syncNow())
        }
        suspend fun markers() = com.dmb.chantiertracker.presentation.sync.SyncIssueMarkers(supervisorPhone.syncIssues.observeIssues().first())
        suspend fun markerOf(target: com.dmb.chantiertracker.domain.model.SyncIssueTarget, localId: String) = markers().of(target, localId)?.issue?.kind
        val entryTarget = com.dmb.chantiertracker.domain.model.SyncIssueTarget.ENTRY
        val lineTarget = com.dmb.chantiertracker.domain.model.SyncIssueTarget.PURCHASE_LINE
        val photoTarget = com.dmb.chantiertracker.domain.model.SyncIssueTarget.ATTACHMENT
        val refused = com.dmb.chantiertracker.domain.model.SyncIssueKind.REFUSED
        val waiting = com.dmb.chantiertracker.domain.model.SyncIssueKind.BLOCKED_BY_PARENT

        val cement = supervisorPhone.materials.createMaterial(supervisorProject.localId, "Ciment", "sac")
        assertEquals(SyncOutcome.Synced, supervisorPhone.sync.syncNow())
        ownerSetsStatus(ProjectStatus.SUSPENDED)

        supervisorPhone.goOffline()
        val entry = supervisorPhone.logs.createPurchaseEntry(supervisorStage.localId, todayInParis()).entryLocalId
        val line = supervisorPhone.purchaseLines.createLine(entry, CreatePurchaseLineInput(cement.localId, 3.0, 5.0, null))
        val photo = supervisorPhone.attachments.addAttachment(entry, aSmallJpeg(), "bon.jpg", "image/jpeg")
        assertEquals(listOf(null, null, null), listOf(markerOf(entryTarget, entry), markerOf(lineTarget, line), markerOf(photoTarget, photo.localId)), "simplement en attente d'envoi : aucun marqueur")
        supervisorPhone.goOnline()
        supervisorPhone.sync.syncNow()
        supervisorPhone.sync.syncNow()

        val marked = listOf(markerOf(entryTarget, entry), markerOf(lineTarget, line), markerOf(photoTarget, photo.localId))
        println("ADR-74 tranche 4 — marqueurs, projet suspendu : saisie=${marked[0]}, ligne=${marked[1]}, photo=${marked[2]}")
        assertEquals(listOf(refused, waiting, waiting), marked)
        assertEquals(
            listOf(null, null, null),
            listOf(
                markerOf(com.dmb.chantiertracker.domain.model.SyncIssueTarget.PROJECT, supervisorProject.localId),
                markerOf(com.dmb.chantiertracker.domain.model.SyncIssueTarget.STAGE, supervisorStage.localId),
                markerOf(com.dmb.chantiertracker.domain.model.SyncIssueTarget.MATERIAL, cement.localId),
            ),
            "le projet, l'étape et le matériau, acceptés, ne portent rien",
        )
        assertEquals(1, supervisorPhone.syncIssues.observeIssueCount().first(), "la pastille compte la saisie, pas ses deux enfants marqués")

        ownerSetsStatus(ProjectStatus.IN_PROGRESS)
        assertEquals(SyncOutcome.Synced, supervisorPhone.sync.syncNow())
        supervisorPhone.sync.syncNow()

        val after = listOf(markerOf(entryTarget, entry), markerOf(lineTarget, line), markerOf(photoTarget, photo.localId))
        println("ADR-74 tranche 4 — marqueurs après réouverture et synchro : $after")
        assertEquals(listOf(null, null, null), after, "une passe de synchro suffit : plus aucun marqueur")
        assertEquals(SyncStatus.SYNCED, supervisorPhone.db.attachmentDao().findByLocalId(photo.localId)?.syncStatus)
        assertEquals(0, supervisorPhone.syncIssues.observeIssueCount().first())
    }

    private fun DeviceStack.countingSends(method: String, pathPart: String): () -> Int {
        var sent = 0
        client.plugin(io.ktor.client.plugins.HttpSend).intercept { request ->
            if (request.method.value == method && request.url.buildString().contains(pathPart)) sent++
            execute(request)
        }
        return { sent }
    }

    @Test
    fun a5_an_entry_refused_for_a_past_day_is_sent_once_over_three_passes_then_once_more_when_it_is_corrected() = runScenario {
        val ownerPhone = device()
        ownerPhone.signedInAs("qa-a5-owner", "QA A5 Propriétaire")
        val supervisorPhone = device()
        val supervisor = supervisorPhone.signedInAs("qa-a5-super", "QA A5 Superviseur")
        val projectId = ownerPhone.newProject("QA A5 refus définitif")
        ownerPhone.newStage(projectId, "Charpente")
        ownerPhone.sync.syncNow()
        ownerPhone.invitations.invite(projectId, supervisor.email)
        supervisorPhone.invitations.acceptInvitation(supervisorPhone.invitationApi.listMine().single().token)
        supervisorPhone.sync.syncNow()
        val supervisorProject = supervisorPhone.projects.observeProjects().first().single()
        supervisorPhone.sync.syncProject(supervisorProject.localId)
        val supervisorStage = supervisorPhone.stages.observeStages(supervisorProject.localId).first().single()
        val yesterday = LocalDate.now(ZoneId.of("Europe/Paris")).minusDays(1).toString()
        val entrySends = supervisorPhone.countingSends("POST", "/logs/$yesterday/purchases")

        supervisorPhone.goOffline()
        val entry = supervisorPhone.logs.createPurchaseEntry(supervisorStage.localId, yesterday).entryLocalId
        supervisorPhone.goOnline()
        repeat(3) { supervisorPhone.sync.syncNow() }
        supervisorPhone.waitForTheRunningSyncPass()

        val refused = supervisorPhone.syncIssues.observeIssues().first().single()
        println("A-5 tranche 2 — journée passée : ${refused.issue.reason}, envois après trois passes = ${entrySends()}")
        assertEquals(com.dmb.chantiertracker.domain.model.RefusalReason.ENTRY_DATE_RESTRICTED, refused.issue.reason)
        assertEquals(1, entrySends(), "trois passes et plus : la saisie refusée pour de bon n'est envoyée qu'une fois")
        assertEquals(1, supervisorPhone.syncIssues.observeIssueCount().first(), "elle reste à revoir")

        supervisorPhone.logs.updateEntry(entry, "Résumé corrigé")
        supervisorPhone.waitForTheRunningSyncPass()
        repeat(3) { supervisorPhone.sync.syncNow() }
        supervisorPhone.waitForTheRunningSyncPass()

        println("A-5 tranche 2 — après correction : envois = ${entrySends()}")
        assertEquals(2, entrySends(), "la correction repart une fois ; toujours un jour passé, donc refusée et figée de nouveau")
        val again = supervisorPhone.syncIssues.observeIssues().first().single()
        assertEquals(listOf<Any?>(entry, com.dmb.chantiertracker.domain.model.RefusalReason.ENTRY_DATE_RESTRICTED), listOf(again.localId, again.issue.reason))
        supervisorPhone.syncIssues.discard(again)
        assertTrue(supervisorPhone.syncIssues.observeIssues().first().isEmpty())
    }

    @Test
    fun a5_a_project_over_the_plan_limit_is_sent_once_while_it_waits_then_leaves_in_the_pass_that_sees_the_plan_changed() = runScenario {
        val phone = device()
        val owner = phone.signedInAs("qa-a5-limit", "QA A5 Limite")
        var projectSends = 0
        phone.client.plugin(io.ktor.client.plugins.HttpSend).intercept { request ->
            if (request.method.value == "POST" && request.url.buildString().endsWith("/projects")) projectSends++
            execute(request)
        }

        phone.goOffline()
        val first = phone.newProject("QA A5 premier")
        val second = phone.newProject("QA A5 second (au-delà de la limite FREE)")
        val stageUnderSecond = phone.newStage(second, "Étape sous un projet refusé")
        phone.goOnline()
        repeat(4) { phone.sync.syncNow() }
        phone.waitForTheRunningSyncPass()

        val rejected = phone.db.projectDao().findByLocalId(second)!!
        println("A-5 tranche 3 — limite de formule : ${rejected.syncStatus} / ${rejected.lastSyncError}, envois de projets après quatre passes = $projectSends")
        assertEquals(SyncStatus.SYNCED, phone.db.projectDao().findByLocalId(first)?.syncStatus)
        assertEquals(listOf<Any?>(SyncStatus.CONFLICTED, SyncError.PLAN_LIMIT), listOf(rejected.syncStatus, rejected.lastSyncError))
        assertEquals(2, projectSends, "un envoi par projet : le projet refusé n'est pas renvoyé avant son échéance")

        accounts.grantPlan(owner.id, "SEMI_FLEX")
        assertEquals(SyncOutcome.Synced, phone.sync.syncNow())

        println("A-5 tranche 3 — après passage en SEMI_FLEX : envois = $projectSends, projet = ${phone.db.projectDao().findByLocalId(second)?.syncStatus}")
        assertEquals(3, projectSends, "le pull voit la formule changée : le projet repart dans cette passe, sans attendre l'échéance")
        assertEquals(SyncStatus.SYNCED, phone.db.projectDao().findByLocalId(second)?.syncStatus)
        assertEquals(SyncStatus.SYNCED, phone.db.stageDao().findByLocalId(stageUnderSecond)?.syncStatus, "et son étape le suit")
        assertTrue(phone.syncIssues.observeIssues().first().isEmpty())
    }

    @Test
    fun a5_an_entry_refused_on_a_suspended_project_is_sent_once_while_it_waits_then_leaves_in_the_pass_that_sees_the_project_reopened() = runScenario {
        val ownerPhone = device()
        ownerPhone.signedInAs("qa-a5-s-owner", "QA A5 Suspendu Propriétaire")
        val supervisorPhone = device()
        val supervisor = supervisorPhone.signedInAs("qa-a5-s-super", "QA A5 Suspendu Superviseur")
        val projectId = ownerPhone.newProject("QA A5 suspendu")
        ownerPhone.newStage(projectId, "Charpente")
        ownerPhone.sync.syncNow()
        ownerPhone.invitations.invite(projectId, supervisor.email)
        supervisorPhone.invitations.acceptInvitation(supervisorPhone.invitationApi.listMine().single().token)
        supervisorPhone.sync.syncNow()
        val supervisorProject = supervisorPhone.projects.observeProjects().first().single()
        supervisorPhone.sync.syncProject(supervisorProject.localId)
        val supervisorStage = supervisorPhone.stages.observeStages(supervisorProject.localId).first().single()
        suspend fun ownerSetsStatus(status: ProjectStatus) {
            val detail = ownerPhone.projects.observeProject(projectId).first()!!
            ownerPhone.projects.updateProject(projectId, UpdateProjectInput(detail.name, detail.description, detail.location, detail.currency, detail.timezone, status))
            ownerPhone.waitForTheRunningSyncPass()
            assertEquals(SyncOutcome.Synced, ownerPhone.sync.syncNow())
        }
        ownerSetsStatus(ProjectStatus.SUSPENDED)
        val today = todayInParis()
        val entrySends = supervisorPhone.countingSends("POST", "/logs/$today/works")

        supervisorPhone.goOffline()
        val entry = supervisorPhone.logs.createWorkEntry(supervisorStage.localId, today).entryLocalId
        supervisorPhone.goOnline()
        repeat(3) { supervisorPhone.sync.syncNow() }
        supervisorPhone.sync.syncProject(supervisorProject.localId)
        supervisorPhone.waitForTheRunningSyncPass()

        val refused = supervisorPhone.syncIssues.observeIssues().first().single()
        println("A-5 tranche 3 — projet suspendu : ${refused.issue.reason}, envois après quatre passes = ${entrySends()}")
        assertEquals(com.dmb.chantiertracker.domain.model.RefusalReason.PROJECT_OR_STAGE_INACTIVE, refused.issue.reason)
        assertEquals(ProjectStatus.SUSPENDED, supervisorPhone.projects.observeProject(supervisorProject.localId).first()?.status)
        assertEquals(1, entrySends(), "le pull qui apporte la suspension ne renvoie rien, et les passes suivantes non plus")

        ownerSetsStatus(ProjectStatus.IN_PROGRESS)
        assertEquals(SyncOutcome.Synced, supervisorPhone.sync.syncNow())

        val after = supervisorPhone.db.dailyEntryDao().findByLocalId(entry)
        println("A-5 tranche 3 — après réouverture : envois = ${entrySends()}, saisie = ${after?.syncStatus}")
        assertEquals(2, entrySends(), "le pull voit le projet rouvert : la saisie repart dans cette passe, sans attendre l'échéance")
        assertEquals(SyncStatus.SYNCED, after?.syncStatus)
        assertTrue(supervisorPhone.syncIssues.observeIssues().first().isEmpty())
    }

    private fun filesInTheBackendUploads(): Set<String>? =
        System.getProperty("chantiertracker.backendUploadsDir")?.let { dir -> java.io.File(dir).walkTopDown().filter { it.isFile }.map { it.relativeTo(java.io.File(dir)).path }.toSet() }

    @Test
    fun d_twenty_five_photos_added_at_once_all_reach_the_server_within_the_upload_quota_without_loss_or_duplicate() = runScenario {
        val phone = device()
        phone.signedInAs("qa-d-photos", "QA D Photos")
        val projectId = phone.newProject("QA D 25 photos")
        val stageId = phone.newStage(projectId, "Charpente")
        val entry = phone.logs.createPurchaseEntry(stageId, todayInParis()).entryLocalId
        assertEquals(SyncOutcome.Synced, phone.sync.syncNow())
        val uploadsBefore = filesInTheBackendUploads()
        var uploads = 0
        var rateLimited = 0
        var readsAfterARateLimit = 0
        phone.client.plugin(io.ktor.client.plugins.HttpSend).intercept { request ->
            val isAnUpload = request.method.value == "POST" && request.url.buildString().endsWith("/attachments")
            val call = execute(request)
            if (isAnUpload) {
                uploads++
                if (call.response.status == io.ktor.http.HttpStatusCode.TooManyRequests) rateLimited++
            } else if (rateLimited > 0 && request.method.value == "GET") {
                readsAfterARateLimit++
            }
            call
        }

        phone.goOffline()
        val jpeg = aSmallJpeg()
        val photos = (1..25).map { phone.attachments.addAttachment(entry, jpeg, "qa-d-photo-${it.toString().padStart(2, '0')}.jpg", "image/jpeg").localId }
        phone.waitForTheRunningSyncPass()
        phone.goOnline()
        suspend fun sent() = photos.count { phone.db.attachmentDao().findByLocalId(it)?.syncStatus == SyncStatus.SYNCED }

        val firstPass = phone.sync.syncNow()
        phone.waitForTheRunningSyncPass()
        val sentByTheFirstPasses = sent()
        println("D tranche 1 — 25 photos, après la première passe : $firstPass, envoyées=$sentByTheFirstPasses, POST=$uploads, 429=$rateLimited, lectures après le 429=$readsAfterARateLimit")
        assertEquals(SyncOutcome.Synced, firstPass, "le 429 ne fait pas échouer la passe")
        assertEquals(20, sentByTheFirstPasses, "le quota du serveur : 20 envois de fichiers par minute")
        assertTrue(rateLimited >= 1, "le serveur a répondu 429 au moins une fois")
        assertTrue(readsAfterARateLimit > 0, "le pull se fait pendant que les fichiers attendent")
        assertTrue(phone.syncIssues.observeIssues().first().isEmpty(), "un 429 n'est pas un refus : rien à revoir")

        var passes = 0
        while (sent() < photos.size && passes < 60) {
            kotlinx.coroutines.delay(1_000)
            phone.sync.syncNow()
            passes++
        }

        val onServer = phone.attachmentApi.list(phone.db.dailyEntryDao().findByLocalId(entry)!!.serverId!!, 0, 100).content
        val added = uploadsBefore?.let { before -> (filesInTheBackendUploads()!! - before).sorted() }
        println("D tranche 1 — 25 photos, à la fin : envoyées=${sent()}, POST=$uploads, 429=$rateLimited, passes d'attente=$passes, sur le serveur=${onServer.size}")
        added?.let { files -> println("D tranche 1 — fichiers ajoutés dans uploads (${files.size}) :\n" + files.joinToString("\n")) }
        assertEquals(25, sent(), "tout est parti")
        assertEquals(25, onServer.size, "rien de perdu, rien en double sur le serveur")
        assertEquals(photos.indices.map { "qa-d-photo-${(it + 1).toString().padStart(2, '0')}.jpg" }, onServer.mapNotNull { it.originalName }.sorted())
        assertEquals(25 + rateLimited, uploads, "un envoi par fichier, plus un par 429 reçu")
        if (added != null) assertEquals(25, added.size, "25 fichiers de plus dans le dossier uploads du backend, pas un de plus")
        assertTrue(phone.syncIssues.observeIssues().first().isEmpty())
    }

    @Test
    fun d_a_photo_the_server_stored_but_whose_answer_was_lost_is_recognised_and_not_stored_twice() = runScenario {
        val phone = device()
        phone.signedInAs("qa-d-lost", "QA D Réponse perdue")
        val projectId = phone.newProject("QA D réponse perdue")
        val stageId = phone.newStage(projectId, "Charpente")
        val created = phone.logs.createPurchaseEntry(stageId, todayInParis())
        assertEquals(SyncOutcome.Synced, phone.sync.syncNow())
        val uploadsBefore = filesInTheBackendUploads()
        var uploads = 0
        phone.client.plugin(io.ktor.client.plugins.HttpSend).intercept { request ->
            val isAnUpload = request.method.value == "POST" && request.url.buildString().endsWith("/attachments")
            val call = execute(request)
            if (isAnUpload && uploads++ == 0) throw java.io.IOException("réponse perdue après l'enregistrement par le serveur")
            call
        }

        phone.goOffline()
        val photo = phone.attachments.addAttachment(created.entryLocalId, aSmallJpeg(), "qa-d-bon.jpg", "image/jpeg").localId
        phone.waitForTheRunningSyncPass()
        phone.goOnline()
        val pass = phone.sync.syncNow()
        phone.waitForTheRunningSyncPass()

        val waiting = phone.db.attachmentDao().findByLocalId(photo)!!
        val entryServerId = phone.db.dailyEntryDao().findByLocalId(created.entryLocalId)!!.serverId!!
        println("D tranche 1 — réponse perdue : passe=$pass, ligne=${waiting.syncStatus}/${waiting.lastSyncError}, sur le serveur=${phone.attachmentApi.list(entryServerId, 0, 100).content.size}")
        assertEquals(SyncOutcome.Synced, pass, "une coupure sur un fichier ne fait pas échouer la passe")
        assertEquals(listOf<Any?>(SyncStatus.PENDING, null, null), listOf(waiting.syncStatus, waiting.lastSyncError, waiting.serverErrorCode), "pas de refus écrit")
        assertTrue(phone.syncIssues.observeIssues().first().isEmpty(), "rien à revoir")

        repeat(2) { phone.sync.syncNow() }
        assertEquals(SyncOutcome.Synced, phone.sync.syncLog(created.dailyLogLocalId))

        val rows = phone.db.attachmentDao().findForEntry(created.entryLocalId)
        val onServer = phone.attachmentApi.list(entryServerId, 0, 100).content
        val added = uploadsBefore?.let { before -> (filesInTheBackendUploads()!! - before).sorted() }
        println("D tranche 1 — réponse perdue, après l'ouverture de la journée : envois=$uploads, lignes locales=${rows.size}, sur le serveur=${onServer.size}, fichiers ajoutés=${added?.size}")
        assertEquals(1, uploads, "le fichier n'est pas envoyé une seconde fois")
        assertEquals(1, onServer.size, "un seul exemplaire sur le serveur")
        assertEquals(listOf<Any?>(photo, SyncStatus.SYNCED, onServer.single().id), rows.single().let { listOf(it.localId, it.syncStatus, it.serverId) }, "la ligne de l'appareil est reconnue, sans doublon local")
        if (added != null) assertEquals(1, added.size)
    }

    @Test
    fun d_a_row_the_server_refuses_with_a_status_the_app_does_not_know_does_not_fail_the_pass_and_is_shown_as_a_refusal() = runScenario {
        val phone = device()
        phone.signedInAs("qa-d-4xx", "QA D Statut inconnu")
        val projectId = phone.newProject("QA D statut inconnu")
        assertEquals(SyncOutcome.Synced, phone.sync.syncNow())
        val statuses = mutableListOf<Int>()
        phone.client.plugin(io.ktor.client.plugins.HttpSend).intercept { request ->
            val isTheStageSentWithTheWrongMethod = request.method.value == "POST" && (request.body as? io.ktor.http.content.TextContent)?.text?.contains("mauvaise méthode") == true
            if (isTheStageSentWithTheWrongMethod) request.method = io.ktor.http.HttpMethod.Put
            execute(request).also { if (isTheStageSentWithTheWrongMethod) statuses += it.response.status.value }
        }

        phone.goOffline()
        val refused = phone.stages.createStage(CreateStageInput(projectId, "Étape envoyée avec la mauvaise méthode", null, null, null, null))
        val sibling = phone.stages.createStage(CreateStageInput(projectId, "Charpente", null, null, null, null))
        phone.waitForTheRunningSyncPass()
        phone.goOnline()
        val pass = phone.sync.syncNow()
        phone.waitForTheRunningSyncPass()
        repeat(2) { phone.sync.syncNow() }

        val row = phone.db.stageDao().findByLocalId(refused)!!
        val item = phone.syncIssues.observeIssues().first().single { it.localId == refused }
        println("D tranche 2 — 4xx inattendu : statuts=$statuses, passe=$pass, ligne=${row.syncStatus}/${row.lastSyncError}/${row.serverErrorCode}, raison=${item.issue.reason}, actions=${item.actions}, autre étape=${phone.db.stageDao().findByLocalId(sibling)!!.syncStatus}")
        assertEquals(listOf(405), statuses, "le vrai serveur répond 405, une seule fois : la ligne n'est pas renvoyée")
        assertEquals(SyncOutcome.Synced, pass, "une ligne refusée avec un statut inconnu ne fait pas échouer la passe")
        assertEquals(listOf<Any?>(SyncStatus.CONFLICTED, SyncError.REJECTED), listOf(row.syncStatus, row.lastSyncError))
        assertEquals(listOf<Any?>(SyncIssueKind.REFUSED, RefusalReason.UNKNOWN), listOf(item.issue.kind, item.issue.reason))
        assertEquals(false, SyncIssueAction.RETRY in item.actions)
        assertEquals(SyncStatus.SYNCED, phone.db.stageDao().findByLocalId(sibling)!!.syncStatus, "l'autre étape est partie")

        phone.syncIssues.discard(item)
        assertEquals(SyncOutcome.Synced, phone.sync.syncProject(projectId))
        assertEquals(listOf("Charpente"), phone.stages.observeStages(projectId).first().map { it.name })
    }

    private fun aShortVideoFilmedOnThePhone(): java.nio.file.Path {
        val file = java.nio.file.Files.createTempFile("qa-d-visite", ".mp4")
        val made = ProcessBuilder(
            "ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i", "testsrc=duration=2:size=320x240:rate=15",
            "-pix_fmt", "yuv420p", "-movflags", "+faststart", file.toString(),
        ).inheritIO().start().waitFor()
        assertEquals(0, made, "ffmpeg fabrique le clip de deux secondes")
        return file
    }

    @Test
    fun d_a_short_video_added_offline_is_kept_on_the_phone_and_sent_when_the_network_is_back() = runScenario {
        val phone = device()
        val owner = phone.signedInAs("qa-d-video", "QA D Vidéo")
        accounts.grantPlan(owner.id, "SEMI_FLEX")
        val projectId = phone.newProject("QA D vidéo hors ligne")
        val stageId = phone.newStage(projectId, "Charpente")
        val created = phone.logs.createPurchaseEntry(stageId, todayInParis())
        assertEquals(SyncOutcome.Synced, phone.sync.syncNow())
        assertEquals(SyncOutcome.Synced, phone.sync.syncProject(projectId))
        val uploadsBefore = filesInTheBackendUploads()
        val filmed = aShortVideoFilmedOnThePhone()
        val rawSize = java.nio.file.Files.size(filmed)
        var uploads = 0
        phone.client.plugin(io.ktor.client.plugins.HttpSend).intercept { request ->
            if (request.method.value == "POST" && request.url.buildString().endsWith("/attachments")) uploads++
            execute(request)
        }

        phone.goOffline()
        val video = phone.attachments.addVideo(
            created.entryLocalId,
            io.github.vinceglb.filekit.PlatformFile(filmed.toString()).asUploadFile("video/mp4"),
        ).localId
        phone.waitForTheRunningSyncPass()
        val offlinePass = phone.sync.syncNow()
        val waiting = phone.db.attachmentDao().findByLocalId(video)!!
        println("D tranche 3 — vidéo hors ligne : passe=$offlinePass, ligne=${waiting.syncStatus}/${waiting.pendingOp}, taille brute=$rawSize, copie locale=${phone.fileStore.sizeOf(waiting.localPath)}, envois=$uploads, fichiers ajoutés=${uploadsBefore?.let { (filesInTheBackendUploads()!! - it).size }}")
        assertEquals(SyncOutcome.Skipped, offlinePass)
        assertEquals(listOf<Any?>(SyncStatus.PENDING, PendingOp.CREATE, null), listOf(waiting.syncStatus, waiting.pendingOp, waiting.lastSyncError), "gardée sur le téléphone, en attente")
        assertEquals(rawSize, phone.fileStore.sizeOf(waiting.localPath), "la copie locale est entière")
        assertEquals(0, uploads, "rien n'est envoyé hors ligne")

        phone.goOnline()
        val pass = phone.sync.syncNow()
        phone.sync.awaitVideosBeingSent()

        val sent = phone.db.attachmentDao().findByLocalId(video)!!
        val entryServerId = phone.db.dailyEntryDao().findByLocalId(created.entryLocalId)!!.serverId!!
        val onServer = phone.attachmentApi.list(entryServerId, 0, 100).content
        val added = uploadsBefore?.let { before -> (filesInTheBackendUploads()!! - before).sorted() }
        println("D tranche 3 — vidéo au retour du réseau : passe=$pass, ligne=${sent.syncStatus}/${sent.mimeType}/${sent.durationSeconds}s/${sent.sizeBytes} octets, envois=$uploads, sur le serveur=${onServer.map { it.originalName to it.size }}, fichiers gardés sur le téléphone=${phone.fileStore.storedPaths.size}, fichiers ajoutés au serveur=$added")
        assertEquals(SyncOutcome.Synced, pass)
        assertEquals(listOf<Any?>(SyncStatus.SYNCED, "video/mp4", onServer.single().id), listOf(sent.syncStatus, sent.mimeType, sent.serverId))
        assertEquals(1, uploads, "un seul envoi")
        assertTrue((sent.durationSeconds ?: 0) >= 1, "le serveur a mesuré la durée")
        assertEquals(setOf(sent.localPath), phone.fileStore.storedPaths, "la vidéo brute est supprimée du téléphone, il reste le fichier fait par le serveur")
        assertEquals(onServer.single().size, phone.fileStore.sizeOf(sent.localPath))
        if (added != null) assertEquals(1, added.size, "un fichier de plus dans uploads")
        java.nio.file.Files.deleteIfExists(filmed)
    }

    @Test
    fun adr74_an_entry_refused_for_a_past_day_offers_no_retry_and_leaves_with_discard() = runScenario {
        val ownerPhone = device()
        ownerPhone.signedInAs("qa-74t-owner", "QA 74 ter Propriétaire")
        val supervisorPhone = device()
        val supervisor = supervisorPhone.signedInAs("qa-74t-super", "QA 74 ter Superviseur")

        val projectId = ownerPhone.newProject("QA 74 ter journée passée")
        ownerPhone.newStage(projectId, "Charpente")
        ownerPhone.sync.syncNow()
        ownerPhone.invitations.invite(projectId, supervisor.email)
        val token = supervisorPhone.invitationApi.listMine().single().token
        supervisorPhone.invitations.acceptInvitation(token)
        supervisorPhone.sync.syncNow()
        val supervisorProject = supervisorPhone.projects.observeProjects().first().single()
        supervisorPhone.sync.syncProject(supervisorProject.localId)
        val supervisorStage = supervisorPhone.stages.observeStages(supervisorProject.localId).first().single()

        supervisorPhone.goOffline()
        val yesterday = LocalDate.now(ZoneId.of("Europe/Paris")).minusDays(1).toString()
        val entry = supervisorPhone.logs.createPurchaseEntry(supervisorStage.localId, yesterday).entryLocalId
        val cement = supervisorPhone.materials.createMaterial(supervisorProject.localId, "Ciment", "sac")
        val line = supervisorPhone.purchaseLines.createLine(entry, CreatePurchaseLineInput(cement.localId, 3.0, 5.0, null))
        supervisorPhone.goOnline()
        supervisorPhone.sync.syncNow()
        supervisorPhone.sync.syncNow()

        val issues = supervisorPhone.syncIssues
        val listed = issues.observeIssues().first()
        println("ADR-74 tranche 3ter — à revoir, journée passée : " + listed.joinToString { "${it.target} ${it.issue.kind} ${it.issue.reason} ${it.actions}" })
        val refusedEntry = listed.single { it.target == com.dmb.chantiertracker.domain.model.SyncIssueTarget.ENTRY }
        assertEquals(entry, refusedEntry.localId)
        assertEquals(com.dmb.chantiertracker.domain.model.SyncIssueKind.REFUSED, refusedEntry.issue.kind)
        assertEquals(com.dmb.chantiertracker.domain.model.RefusalReason.ENTRY_DATE_RESTRICTED, refusedEntry.issue.reason)
        assertEquals(yesterday, refusedEntry.date)
        assertTrue(!refusedEntry.issue.canBeRetried, "une journée passée le reste : pas de « Réessayer »")
        assertEquals(listOf(com.dmb.chantiertracker.domain.model.SyncIssueAction.DISCARD), refusedEntry.actions, "il reste « Abandonner »")

        val daysOnServerBefore = ownerPhone.dailyLogApi.listLogs(ownerPhone.db.stageDao().findForProject(projectId).single().serverId!!, page = 0, size = 100).content
        assertEquals(com.dmb.chantiertracker.domain.repository.RetryOutcome.STILL_REFUSED, issues.retry(refusedEntry), "même appelé de force, rien ne repart")
        assertEquals(SyncStatus.CONFLICTED, supervisorPhone.db.dailyEntryDao().findByLocalId(entry)?.syncStatus)

        assertEquals(1, issues.linkedCount(refusedEntry), "la confirmation annonce la ligne qui attendait sous la saisie")
        issues.discard(refusedEntry)

        println("ADR-74 tranche 3ter — après « Abandonner » : à revoir=${issues.observeIssues().first().size}")
        assertTrue(issues.observeIssues().first().isEmpty())
        assertEquals(0, issues.observeIssueCount().first())
        assertEquals(null, supervisorPhone.db.dailyEntryDao().findByLocalId(entry))
        assertEquals(null, supervisorPhone.db.purchaseLineDao().findByLocalId(line))
        assertEquals(SyncOutcome.Synced, supervisorPhone.sync.syncNow())
        val daysOnServerAfter = ownerPhone.dailyLogApi.listLogs(ownerPhone.db.stageDao().findForProject(projectId).single().serverId!!, page = 0, size = 100).content
        assertEquals(daysOnServerBefore, daysOnServerAfter, "le serveur n'a rien reçu de cette saisie")
        assertTrue(daysOnServerAfter.none { it.date == yesterday }, "aucune journée d'hier côté serveur")
    }

    @Test
    fun adr74_a_photo_waiting_to_be_sent_survives_the_startup_cleanup_and_reaches_the_server_while_an_old_orphan_file_is_removed() = runScenario {
        val phone = device()
        phone.signedInAs("qa-74t-clean", "QA 74 ter Nettoyage")
        val projectId = phone.newProject("QA 74 ter nettoyage")
        val stageId = phone.newStage(projectId, "Gros œuvre")
        val entry = phone.logs.createPurchaseEntry(stageId, todayInParis()).entryLocalId
        assertEquals(SyncOutcome.Synced, phone.sync.syncNow())

        var now = 1_000_000_000L
        phone.fileStore.now = now
        val cleaner = com.dmb.chantiertracker.data.local.OrphanAttachmentFileCleaner(phone.fileStore, phone.db.attachmentDao(), phone.sync, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default), clock = { now })

        phone.goOffline()
        val oldOrphan = phone.fileStore.save(aSmallJpeg(), "orpheline.jpg")
        now += 11 * 60_000L
        phone.fileStore.now = now
        val savedButNotYetRecorded = phone.fileStore.save(aSmallJpeg(), "en-cours-d-ajout.jpg")
        val photo = phone.attachments.addAttachment(entry, aSmallJpeg(), "bon.jpg", "image/jpeg")
        val photoFile = phone.db.attachmentDao().findByLocalId(photo.localId)!!.localPath

        val removedAtStart = cleaner.removeOrphans()
        println("ADR-74 tranche 3ter — nettoyage au démarrage : retiré=$removedAtStart, gardé=${phone.fileStore.storedPaths}")
        assertEquals(listOf(oldOrphan), removedAtStart, "seul le vieux fichier sans ligne part")
        assertEquals(setOf(savedButNotYetRecorded, photoFile), phone.fileStore.storedPaths, "la photo en attente et le fichier tout juste écrit restent")

        phone.goOnline()
        assertEquals(SyncOutcome.Synced, phone.sync.syncNow())
        val sent = phone.db.attachmentDao().findByLocalId(photo.localId)!!
        assertEquals(SyncStatus.SYNCED, sent.syncStatus)
        val serverPhotos = phone.attachmentApi.list(phone.db.dailyEntryDao().findByLocalId(entry)!!.serverId!!, page = 0, size = 100).content
        println("ADR-74 tranche 3ter — côté serveur : ${serverPhotos.size} photo(s)")
        assertEquals(listOf(sent.serverId), serverPhotos.map { it.id })

        now += 11 * 60_000L
        assertEquals(listOf(savedButNotYetRecorded), cleaner.removeOrphans(), "dix minutes plus tard, le fichier resté sans ligne part à son tour")
        assertEquals(setOf(sent.localPath), phone.fileStore.storedPaths, "le fichier de la photo envoyée est toujours là")
    }

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
        val bobOnServer = phone.projectApi.list(page = 0, size = 100).content.map { it.name }
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
        val bobOnServer = phone.projectApi.list(page = 0, size = 100).content.map { it.name }
        println("P8-expirée — affiché à Bob : $shownToBob ; côté serveur pour Bob : $bobOnServer ; fichiers locaux : ${phone.fileStore.storedPaths}")
        assertTrue(shownToBob.isEmpty(), "rien d'Alice n'est montré à Bob")
        assertTrue("Saisie en attente d'Alice" !in bobOnServer, "la saisie d'Alice n'est jamais créée sur le compte de Bob")
        assertTrue(phone.fileStore.storedPaths.isEmpty(), "les fichiers de justificatifs d'Alice sont effacés")
        assertTrue(phone.db.projectDao().findByLocalId(pendingId) == null && phone.db.projectDao().findByLocalId(projectId) == null)
    }
}

