package com.dmb.chantiertracker

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.domain.model.actions
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
        val entry = supervisorPhone.entryOf(supervisorPhone.logs.createPurchaseEntry(supervisorStage.localId, today), EntryType.PURCHASE)
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
        assertEquals(2, listed.size, "l'écran liste la saisie refusée et sa ligne en attente")
        assertEquals(1, issues.observeIssueCount().first(), "la pastille ne compte que la saisie refusée, pas la ligne en attente")

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
        supervisorPhone.sync.syncNow()
        val remaining = issues.observeIssues().first()
        println("ADR-74 tranche 2 — à revoir après réouverture : ${remaining.size}")
        assertTrue(remaining.isEmpty(), "projet rouvert puis « Réessayer » : plus rien à revoir")
        assertEquals(0, issues.observeIssueCount().first())
        assertEquals(SyncStatus.SYNCED, supervisorPhone.db.dailyEntryDao().findByLocalId(entry)?.syncStatus)
        assertEquals(SyncStatus.SYNCED, supervisorPhone.db.purchaseLineDao().findByLocalId(line)?.syncStatus)
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
        val day = supervisorPhone.logs.createPurchaseEntry(supervisorStage.localId, todayInParis())
        val entry = supervisorPhone.entryOf(day, EntryType.PURCHASE)
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
        entry.let { }
    }

    @Test
    fun adr74_got_it_on_a_refused_delete_brings_the_entry_back_with_the_server_version_online_and_after_reconnection_offline() = runScenario {
        val ownerPhone = device()
        ownerPhone.signedInAs("qa-74g-owner", "QA 74 Compris Propriétaire")
        val supervisorPhone = device()
        val supervisor = supervisorPhone.signedInAs("qa-74g-super", "QA 74 Compris Superviseur")

        val projectId = ownerPhone.newProject("QA 74 compris")
        val stageId = ownerPhone.newStage(projectId, "Électricité")
        val ownerDay = ownerPhone.logs.createWorkEntry(stageId, todayInParis())
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
        assertEquals(com.dmb.chantiertracker.data.sync.SyncError.AWAITING_SERVER_VERSION, purchaseOffline.lastSyncError, "le rafraîchissement est noté en base")
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
        val entry = supervisorPhone.entryOf(supervisorPhone.logs.createPurchaseEntry(supervisorStage.localId, yesterday), EntryType.PURCHASE)
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
        val entry = phone.entryOf(phone.logs.createPurchaseEntry(stageId, todayInParis()), EntryType.PURCHASE)
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

