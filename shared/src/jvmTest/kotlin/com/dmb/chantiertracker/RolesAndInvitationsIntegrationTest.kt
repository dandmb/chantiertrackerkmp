package com.dmb.chantiertracker

import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.SyncError
import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.domain.repository.SignOutResult
import kotlin.test.assertIs
import io.ktor.client.request.delete
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.CreatePurchaseLineInput
import com.dmb.chantiertracker.domain.model.CreateStageInput
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.ReportSort
import com.dmb.chantiertracker.domain.model.ReportStatus
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.domain.model.UpdateProjectInput
import com.dmb.chantiertracker.domain.model.UpdateStageInput
import com.dmb.chantiertracker.presentation.invitations.parseInvitationToken
import com.dmb.chantiertracker.support.DeviceStack
import com.dmb.chantiertracker.support.DisposableAccounts
import com.dmb.chantiertracker.support.IntegrationBackend
import com.dmb.chantiertracker.support.signInForTheFirstTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Campagne QA, groupe B — rôles et invitations (P1, P9, P10), contre un vrai backend.
 * Mêmes prérequis et même convention que [SyncAndRejectionsIntegrationTest] : désactivé par
 * défaut, comptes jetables créés par le super-admin, assertions = comportement attendu, tout ce
 * qui est observé est imprimé.
 */
class RolesAndInvitationsIntegrationTest {

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

    private fun today(): String = LocalDate.now(ZoneId.of("Europe/Paris")).toString()

    private fun aSmallJpeg(): ByteArray {
        val image = BufferedImage(48, 32, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 48) for (y in 0 until 32) image.setRGB(x, y, (x * 5 shl 16) or (y * 7 shl 8))
        return ByteArrayOutputStream().use { out -> ImageIO.write(image, "jpg", out); out.toByteArray() }
    }

    private suspend fun DeviceStack.signedInAs(prefix: String, name: String): DisposableAccounts.Account {
        val account = accounts.create(prefix, name)
        auth.signInForTheFirstTime(account, "QaPassword1234!")
        return account
    }

    private suspend fun DeviceStack.entryOf(dayLocalId: String, type: EntryType): String =
        logs.observeLog(dayLocalId).first()!!.entries.single { it.type == type }.localId

    /** The invitation link exactly as the e-mail carries it (MailDev). */
    private suspend fun invitationLinkSentTo(email: String): String {
        repeat(30) {
            val messages = runCatching { Json.parseToJsonElement(URI("http://localhost:1080/email").toURL().readText()).jsonArray }.getOrNull().orEmpty()
            val message = messages.lastOrNull { it.jsonObject.toString().contains(email) }
            val html = message?.jsonObject?.let { (it["html"] ?: it["text"])?.jsonPrimitive?.content }
            Regex("""https?://[^\s"'<>]+/invitations/[A-Za-z0-9_\-]+""").find(html.orEmpty())?.value?.let { return it }
            delay(300)
        }
        error("Aucun lien d'invitation pour $email dans MailDev")
    }

    private class Site(val owner: DeviceStack, val supervisor: DeviceStack, val ownerProject: String, val ownerStage: String,
                       val supervisorProject: String, val supervisorStage: String, val supervisorEmail: String)

    /** Owner creates a project + stage, invites, the supervisor accepts through the e-mailed link. */
    private suspend fun aSiteWithASupervisor(tag: String): Site {
        val owner = device()
        owner.signedInAs("qa-b-$tag-owner", "QA B Propriétaire $tag")
        val supervisor = device()
        val supervisorAccount = supervisor.signedInAs("qa-b-$tag-super", "QA B Superviseur $tag")
        val projectId = owner.projects.createProject(CreateProjectInput("QA B $tag", null, "Nîmes", "EUR", "Europe/Paris"))
        val stageId = owner.stages.createStage(CreateStageInput(projectId, "Gros œuvre", null, null, null, null))
        owner.sync.syncNow()
        owner.invitations.invite(projectId, supervisorAccount.email)
        val token = parseInvitationToken("https://chantiertracker.com" + URI(invitationLinkSentTo(supervisorAccount.email)).path)!!
        supervisor.invitations.acceptInvitation(token)
        supervisor.sync.syncNow()
        val supervisorProject = supervisor.projects.observeProjects().first().single().localId
        supervisor.sync.syncProject(supervisorProject)
        val supervisorStage = supervisor.stages.observeStages(supervisorProject).first().single().localId
        return Site(owner, supervisor, projectId, stageId, supervisorProject, supervisorStage, supervisorAccount.email)
    }

    // ─── P1 — the whole invitation, down to the supervisor's entry seen by the owner ──

    @Test
    fun p1_invitation_by_email_then_the_supervisor_entry_reaches_the_owner() = runScenario {
        val owner = device()
        owner.signedInAs("qa-b-p1-owner", "QA B Propriétaire")
        val supervisor = device()
        val supervisorAccount = supervisor.signedInAs("qa-b-p1-super", "QA B Superviseur")
        val projectId = owner.projects.createProject(CreateProjectInput("QA B P1 chantier", null, "Nîmes", "EUR", "Europe/Paris"))
        owner.stages.createStage(CreateStageInput(projectId, "Maçonnerie", null, null, null, null))
        owner.sync.syncNow()

        owner.invitations.invite(projectId, supervisorAccount.email)
        owner.sync.syncProject(projectId)
        val pendingSeenByOwner = owner.invitations.observeInvitations(projectId).first()
        println("P1 — invitations en attente vues par le propriétaire : ${pendingSeenByOwner.map { it.email to it.status }}")
        assertEquals(listOf(supervisorAccount.email), pendingSeenByOwner.map { it.email })

        val link = invitationLinkSentTo(supervisorAccount.email)
        println("P1 — lien reçu par e-mail : $link")
        assertTrue(URI(link).path.startsWith("/invitations/"), "le chemin de l'e-mail est celui que l'App Link attend")
        val token = assertNotNull(parseInvitationToken("https://chantiertracker.com" + URI(link).path), "le jeton se lit avec le parseur de l'app")

        val preview = supervisor.invitations.getInvitationPreview(token)
        println("P1 — aperçu avant acceptation : $preview")
        assertEquals("QA B P1 chantier", preview.projectName)

        val incoming = supervisor.invitations.listIncomingInvitations()
        println("P1 — bannière d'invitation reçue : $incoming")
        assertEquals(1, incoming.size)

        supervisor.invitations.acceptInvitation(token)
        supervisor.sync.syncNow()
        val supervisorProject = supervisor.projects.observeProjects().first().single()
        supervisor.sync.syncProject(supervisorProject.localId)
        val members = supervisor.projects.observeMembers(supervisorProject.localId).first()
        println("P1 — membres vus par le superviseur : ${members.map { it.email to it.role }}")
        assertTrue(members.any { it.email.equals(supervisorAccount.email, ignoreCase = true) && it.role == ProjectRole.SUPERVISOR })

        val stage = supervisor.stages.observeStages(supervisorProject.localId).first().single()
        val day = supervisor.logs.createPurchaseEntry(stage.localId, today())
        val entry = supervisor.entryOf(day, EntryType.PURCHASE)
        val cement = supervisor.materials.createMaterial(supervisorProject.localId, "Ciment", "sac")
        supervisor.purchaseLines.createLine(entry, CreatePurchaseLineInput(cement.localId, 8.0, 6.0, "Négoce"))
        supervisor.attachments.addAttachment(entry, aSmallJpeg(), "bon.jpg", "image/jpeg")
        supervisor.sync.syncNow()
        val pushed = supervisor.db.dailyEntryDao().findByLocalId(entry)
        println("P1 — saisie du superviseur : ${pushed?.syncStatus} serverId=${pushed?.serverId}")
        assertEquals(SyncStatus.SYNCED, pushed?.syncStatus)

        owner.sync.syncProject(projectId)
        val ownerStage = owner.stages.observeStages(projectId).first().single()
        owner.sync.syncStage(ownerStage.localId)
        val ownerDay = owner.logs.observeLogs(ownerStage.localId).first().single()
        owner.sync.syncLog(ownerDay.localId)
        val ownerEntry = owner.logs.observeLog(ownerDay.localId).first()!!.entries.single { it.type == EntryType.PURCHASE }
        val ownerLines = owner.purchaseLines.observeLines(ownerEntry.localId).first()
        val ownerPhotos = owner.attachments.observeAttachments(ownerEntry.localId).first()
        val ownerStock = owner.materials.observeStock(projectId).first().materials
        println("P1 — vu par le propriétaire : lignes=${ownerLines.map { it.quantity }} photos=${ownerPhotos.size} stock=${ownerStock.map { it.materialName to it.available }}")
        assertEquals(listOf(8.0), ownerLines.map { it.quantity })
        assertEquals(1, ownerPhotos.size)
        assertEquals(8.0, ownerStock.single().available)
    }

    @Test
    fun p1_a_second_supervisor_on_the_free_plan_is_refused_with_the_plan_limit() = runScenario {
        val site = aSiteWithASupervisor("limit")
        val other = accounts.create("qa-b-limit-second", "QA B Second superviseur")

        val refusal = runCatching { site.owner.invitations.invite(site.ownerProject, other.email) }.exceptionOrNull()
        println("P1-limite — 2e invitation sur FREE : $refusal")
        assertTrue(refusal is DomainException.PlanLimitReached, "refus serveur reconnu comme limite de plan (message dédié à l'écran)")
    }

    @Test
    fun p1_an_invitation_opened_by_another_account_is_refused() = runScenario {
        val owner = device()
        owner.signedInAs("qa-b-wrong-owner", "QA B Propriétaire")
        val invited = accounts.create("qa-b-wrong-invited", "QA B Invité")
        val intruder = device()
        intruder.signedInAs("qa-b-wrong-intruder", "QA B Intrus")
        val projectId = owner.projects.createProject(CreateProjectInput("QA B mauvais destinataire", null, null, "EUR", "Europe/Paris"))
        owner.sync.syncNow()
        owner.invitations.invite(projectId, invited.email)
        val token = parseInvitationToken("https://chantiertracker.com" + URI(invitationLinkSentTo(invited.email)).path)!!

        val refusal = runCatching { intruder.invitations.acceptInvitation(token) }.exceptionOrNull()
        intruder.sync.syncNow()
        println("P1-intrus — acceptation par un autre compte : $refusal ; projets de l'intrus=${intruder.projects.observeProjects().first().map { it.name }}")
        assertTrue(refusal is DomainException.Forbidden, "refus reconnu comme accès interdit, pas comme une autre erreur")
        assertTrue(intruder.projects.observeProjects().first().isEmpty())
    }

    @Test
    fun p1_a_cancelled_invitation_reads_as_an_invalid_link() = runScenario {
        val owner = device()
        owner.signedInAs("qa-b-cancel-owner", "QA B Propriétaire")
        val supervisor = device()
        val supervisorAccount = supervisor.signedInAs("qa-b-cancel-super", "QA B Superviseur")
        val projectId = owner.projects.createProject(CreateProjectInput("QA B annulée", null, null, "EUR", "Europe/Paris"))
        owner.sync.syncNow()
        owner.invitations.invite(projectId, supervisorAccount.email)
        val token = parseInvitationToken("https://chantiertracker.com" + URI(invitationLinkSentTo(supervisorAccount.email)).path)!!
        owner.sync.syncProject(projectId)
        val invitation = owner.invitations.observeInvitations(projectId).first().single()
        owner.invitations.cancelInvitation(projectId, invitation.id)

        val preview = runCatching { supervisor.invitations.getInvitationPreview(token) }
        val accept = runCatching { supervisor.invitations.acceptInvitation(token) }.exceptionOrNull()
        println("P1-annulée — aperçu : $preview ; acceptation : $accept")
        assertTrue(accept is DomainException.NotFound, "lien d'une invitation annulée → « lien invalide » à l'écran")
    }

    @Test
    fun p1_a_declined_invitation_leaves_the_owner_without_pending_invitation() = runScenario {
        val owner = device()
        owner.signedInAs("qa-b-decline-owner", "QA B Propriétaire")
        val supervisor = device()
        val supervisorAccount = supervisor.signedInAs("qa-b-decline-super", "QA B Superviseur")
        val projectId = owner.projects.createProject(CreateProjectInput("QA B refusée", null, null, "EUR", "Europe/Paris"))
        owner.sync.syncNow()
        owner.invitations.invite(projectId, supervisorAccount.email)
        val token = supervisor.invitations.listIncomingInvitations().single().token

        supervisor.invitations.declineInvitation(token)
        owner.sync.syncProject(projectId)
        val pending = owner.invitations.observeInvitations(projectId).first()
        println("P1-refus — invitations vues par le propriétaire après refus : ${pending.map { it.email to it.status }}")
        assertTrue(pending.none { it.status == com.dmb.chantiertracker.domain.model.InvitationStatus.PENDING })
        assertTrue(supervisor.invitations.listIncomingInvitations().isEmpty())
    }

    // ─── P9 — project / stage status and the supervisor's rights ─────────────

    @Test
    fun p9_a_suspended_project_reaches_the_supervisor_and_a_reopened_one_lets_the_refused_entry_through() = runScenario {
        val site = aSiteWithASupervisor("suspend")
        val detail = site.owner.projects.observeProject(site.ownerProject).first()!!
        site.owner.projects.updateProject(site.ownerProject, UpdateProjectInput(detail.name, detail.description, detail.location, detail.currency, detail.timezone, ProjectStatus.SUSPENDED))
        site.owner.sync.syncNow()

        site.supervisor.sync.syncProject(site.supervisorProject)
        val seen = site.supervisor.projects.observeProject(site.supervisorProject).first()?.status
        println("P9-suspendu — statut vu par le superviseur après synchro : $seen")
        assertEquals(ProjectStatus.SUSPENDED, seen, "l'écran du superviseur peut bloquer la saisie (canEdit)")

        // A write that slipped through anyway (queued offline before the status arrived).
        val entry = site.supervisor.entryOf(site.supervisor.logs.createWorkEntry(site.supervisorStage, today()), EntryType.WORK)
        site.supervisor.sync.syncNow()
        println("P9-suspendu — saisie pendant la suspension : ${site.supervisor.db.dailyEntryDao().findByLocalId(entry)?.syncStatus}")
        assertEquals(SyncStatus.CONFLICTED, site.supervisor.db.dailyEntryDao().findByLocalId(entry)?.syncStatus)

        val suspended = site.owner.projects.observeProject(site.ownerProject).first()!!
        site.owner.projects.updateProject(site.ownerProject, UpdateProjectInput(suspended.name, suspended.description, suspended.location, suspended.currency, suspended.timezone, ProjectStatus.IN_PROGRESS))
        site.owner.sync.syncNow()
        site.supervisor.sync.syncNow()
        val after = site.supervisor.db.dailyEntryDao().findByLocalId(entry)
        println("P9-suspendu — après réouverture : ${after?.syncStatus} serverId=${after?.serverId}")
        assertEquals(SyncStatus.SYNCED, after?.syncStatus, "projet rouvert : la saisie refusée part à la synchro suivante")
    }

    @Test
    fun p9_a_completed_stage_reaches_the_supervisor_and_refuses_new_entries() = runScenario {
        val site = aSiteWithASupervisor("stage")
        val stage = site.owner.stages.observeStage(site.ownerStage).first()!!
        site.owner.stages.updateStage(
            site.ownerStage,
            UpdateStageInput(stage.name, stage.description, stage.estimatedBudget, stage.startDate, stage.endDate, StageStatus.COMPLETED),
        )
        site.owner.sync.syncNow()

        site.supervisor.sync.syncProject(site.supervisorProject)
        val seen = site.supervisor.stages.observeStage(site.supervisorStage).first()?.status
        println("P9-étape — statut d'étape vu par le superviseur : $seen")
        assertEquals(StageStatus.COMPLETED, seen)

        val entry = site.supervisor.entryOf(site.supervisor.logs.createWorkEntry(site.supervisorStage, today()), EntryType.WORK)
        site.supervisor.sync.syncNow()
        println("P9-étape — saisie sur étape terminée : ${site.supervisor.db.dailyEntryDao().findByLocalId(entry)?.syncStatus}")
        assertEquals(SyncStatus.CONFLICTED, site.supervisor.db.dailyEntryDao().findByLocalId(entry)?.syncStatus)
    }

    @Test
    fun p9_a_supervisor_cannot_write_on_a_past_day() = runScenario {
        val site = aSiteWithASupervisor("past")
        val yesterday = LocalDate.now(ZoneId.of("Europe/Paris")).minusDays(1).toString()

        val entry = site.supervisor.entryOf(site.supervisor.logs.createWorkEntry(site.supervisorStage, yesterday), EntryType.WORK)
        site.supervisor.sync.syncNow()
        val row = site.supervisor.db.dailyEntryDao().findByLocalId(entry)
        println("P9-veille — saisie d'un superviseur sur la veille : ${row?.syncStatus} / ${row?.lastSyncError}")
        assertEquals(SyncStatus.CONFLICTED, row?.syncStatus, "règle serveur : un superviseur n'écrit que le jour même")
    }

    @Test
    fun p9_a_project_deleted_by_the_owner_keeps_the_supervisor_pending_entry_and_never_blocks_the_sync_or_the_sign_out() = runScenario {
        val site = aSiteWithASupervisor("deleted")
        site.supervisor.goOffline()
        val entry = site.supervisor.entryOf(site.supervisor.logs.createWorkEntry(site.supervisorStage, today()), EntryType.WORK)

        site.owner.projects.deleteProject(site.ownerProject)
        site.owner.sync.syncNow()

        site.supervisor.goOnline()
        val fullPass = site.supervisor.sync.syncNow()
        val projectPass = site.supervisor.sync.syncProject(site.supervisorProject)
        val secondPass = site.supervisor.sync.syncNow()
        println("P9-supprimé — passes : complète=$fullPass ; projet=$projectPass ; seconde=$secondPass")
        val stageRow = site.supervisor.db.stageDao().findByLocalId(site.supervisorStage)
        val entryRow = site.supervisor.db.dailyEntryDao().findByLocalId(entry)
        println("P9-supprimé — étape=${stageRow?.syncStatus}/${stageRow?.lastSyncError} ; saisie=${entryRow?.syncStatus}/${entryRow?.lastSyncError}")
        val projectLeft = site.supervisor.db.projectDao().findByLocalId(site.supervisorProject)
        val entryLeft = site.supervisor.db.dailyEntryDao().findByLocalId(entry)
        println("P9-supprimé — chez le superviseur : projet=${projectLeft?.name} ${projectLeft?.syncStatus}/${projectLeft?.lastSyncError} ; saisie=${entryLeft?.syncStatus}/${entryLeft?.lastSyncError}")
        val signOut = site.supervisor.signOut.signOut()
        println("P9-supprimé — déconnexion demandée : $signOut")

        assertEquals(SyncOutcome.Synced, fullPass, "la synchro du compte continue (B-1)")
        assertEquals(SyncOutcome.Synced, secondPass)
        assertEquals(SyncStatus.CONFLICTED, entryLeft?.syncStatus, "la saisie orpheline reste, refusée : pas de perte silencieuse")
        assertEquals(SyncError.DELETED_ON_SERVER, entryLeft?.lastSyncError)
        assertEquals(SyncError.DELETED_ON_SERVER, projectLeft?.lastSyncError, "le projet reste en fantôme tant qu'il porte une saisie non envoyée")
        assertIs<SignOutResult.RefusedWritesLeft>(signOut, "plus de blocage sans issue : l'avertissement « Se déconnecter quand même »")
        assertEquals(
            com.dmb.chantiertracker.domain.model.UnsentWrites(entries = 1),
            (signOut as SignOutResult.RefusedWritesLeft).unsent,
            "une seule saisie est annoncée : le projet et l'étape fantômes ne comptent pas",
        )
    }

    // ─── B-1 — a parent gone on the server while a supervisor's write waits ──

    private suspend fun DeviceStack.describe(label: String, rows: Map<String, suspend () -> Any?>) {
        val outcomes = listOf(sync.syncNow(), sync.syncNow())
        val seen = rows.map { (name, read) -> "$name=${read()}" }
        println("$label — passes=$outcomes ; ${seen.joinToString(" ; ")}")
    }

    private fun com.dmb.chantiertracker.data.local.db.DailyEntryEntity?.state() = this?.let { "${it.syncStatus}/${it.lastSyncError}" } ?: "EFFACÉE"

    @Test
    fun b1_a_stage_deleted_by_the_owner_while_a_supervisor_entry_waits() = runScenario {
        val site = aSiteWithASupervisor("b1stage")
        site.supervisor.goOffline()
        val entry = site.supervisor.entryOf(site.supervisor.logs.createWorkEntry(site.supervisorStage, today()), EntryType.WORK)

        site.owner.stages.deleteStage(site.ownerStage)
        site.owner.sync.syncNow()

        site.supervisor.goOnline()
        site.supervisor.describe("B-1 étape supprimée", mapOf(
            "saisie" to { site.supervisor.db.dailyEntryDao().findByLocalId(entry).state() },
            "étape" to { site.supervisor.db.stageDao().findByLocalId(site.supervisorStage)?.syncStatus ?: "EFFACÉE" },
        ))
        val projectPass = site.supervisor.sync.syncProject(site.supervisorProject)
        println("B-1 étape supprimée — après syncProject=$projectPass : saisie=${site.supervisor.db.dailyEntryDao().findByLocalId(entry).state()} ; étape=${site.supervisor.db.stageDao().findByLocalId(site.supervisorStage)?.syncStatus ?: "EFFACÉE"}")

        assertEquals(SyncOutcome.Synced, site.supervisor.sync.syncNow(), "la synchro du compte continue")
        assertEquals(SyncStatus.CONFLICTED, site.supervisor.db.dailyEntryDao().findByLocalId(entry)?.syncStatus, "la saisie orpheline reste, refusée")
        assertEquals(SyncError.DELETED_ON_SERVER, site.supervisor.db.dailyEntryDao().findByLocalId(entry)?.lastSyncError)
    }

    @Test
    fun b1_an_entry_deleted_by_the_owner_while_the_supervisor_adds_to_it() = runScenario {
        val site = aSiteWithASupervisor("b1entry")
        val day = site.supervisor.logs.createPurchaseEntry(site.supervisorStage, today())
        val entry = site.supervisor.entryOf(day, EntryType.PURCHASE)
        val cement = site.supervisor.materials.createMaterial(site.supervisorProject, "Ciment", "sac")
        site.supervisor.sync.syncNow()
        val entryServerId = site.supervisor.db.dailyEntryDao().findByLocalId(entry)!!.serverId!!

        site.supervisor.goOffline()
        val line = site.supervisor.purchaseLines.createLine(entry, CreatePurchaseLineInput(cement.localId, 4.0, 6.0, "Négoce"))
        val photo = site.supervisor.attachments.addAttachment(entry, aSmallJpeg(), "bon.jpg", "image/jpeg")
        site.supervisor.logs.updateEntry(entry, "Livraison partielle")

        site.owner.dailyLogApi.deleteEntry(entryServerId)

        site.supervisor.goOnline()
        site.supervisor.describe("B-1 saisie supprimée", mapOf(
            "saisie" to { site.supervisor.db.dailyEntryDao().findByLocalId(entry).state() },
            "ligne" to { site.supervisor.db.purchaseLineDao().findByLocalId(line)?.let { "${it.syncStatus}/${it.lastSyncError}" } ?: "EFFACÉE" },
            "photo" to { site.supervisor.db.attachmentDao().findByLocalId(photo.localId)?.let { "${it.syncStatus}/${it.lastSyncError}" } ?: "EFFACÉE" },
            "journée" to { site.supervisor.db.dailyLogDao().findByLocalId(day)?.let { "présente serverId=${it.serverId}" } ?: "EFFACÉE" },
        ))
        val logPass = site.supervisor.sync.syncLog(day)
        println("B-1 saisie supprimée — après syncLog=$logPass : saisie=${site.supervisor.db.dailyEntryDao().findByLocalId(entry).state()} ; ligne=${site.supervisor.db.purchaseLineDao().findByLocalId(line)?.syncStatus ?: "EFFACÉE"} ; photo=${site.supervisor.db.attachmentDao().findByLocalId(photo.localId)?.syncStatus ?: "EFFACÉE"}")

        assertEquals(SyncOutcome.Synced, site.supervisor.sync.syncNow(), "la synchro du compte continue")
        assertEquals(SyncStatus.CONFLICTED, site.supervisor.db.purchaseLineDao().findByLocalId(line)?.syncStatus, "la ligne orpheline reste, refusée")
        assertEquals(SyncStatus.CONFLICTED, site.supervisor.db.attachmentDao().findByLocalId(photo.localId)?.syncStatus, "la photo orpheline reste, refusée")
        assertEquals(SyncStatus.CONFLICTED, site.supervisor.db.dailyEntryDao().findByLocalId(entry)?.syncStatus, "la saisie modifiée reste, avec sa modification")
        assertEquals("Livraison partielle", site.supervisor.db.dailyEntryDao().findByLocalId(entry)?.summary)
    }

    @Test
    fun b1_a_supervisor_removed_from_the_project_while_an_entry_waits() = runScenario {
        val site = aSiteWithASupervisor("b1member")
        site.supervisor.goOffline()
        val entry = site.supervisor.entryOf(site.supervisor.logs.createWorkEntry(site.supervisorStage, today()), EntryType.WORK)

        site.owner.sync.syncProject(site.ownerProject)
        val supervisorId = site.owner.projects.observeMembers(site.ownerProject).first().single { it.role == ProjectRole.SUPERVISOR }.userId
        val projectServerId = site.owner.db.projectDao().findByLocalId(site.ownerProject)!!.serverId!!
        site.owner.client.delete("${com.dmb.chantiertracker.data.remote.ApiRoutes.projectMembers(projectServerId)}/$supervisorId")

        site.supervisor.goOnline()
        site.supervisor.describe("B-1 membre retiré", mapOf(
            "saisie" to { site.supervisor.db.dailyEntryDao().findByLocalId(entry).state() },
            "projet" to { site.supervisor.db.projectDao().findByLocalId(site.supervisorProject)?.let { "${it.syncStatus}/${it.lastSyncError}" } ?: "EFFACÉ" },
        ))

        assertEquals(SyncOutcome.Synced, site.supervisor.sync.syncNow(), "la synchro du compte continue")
        assertEquals(SyncStatus.CONFLICTED, site.supervisor.db.dailyEntryDao().findByLocalId(entry)?.syncStatus, "la saisie orpheline reste, refusée")
        assertEquals(SyncError.DELETED_ON_SERVER, site.supervisor.db.dailyEntryDao().findByLocalId(entry)?.lastSyncError)
        assertEquals(SyncError.DELETED_ON_SERVER, site.supervisor.db.projectDao().findByLocalId(site.supervisorProject)?.lastSyncError, "projet fantôme")
    }

    @Test
    fun b1_a_reinvited_supervisor_gets_the_ghost_project_back_and_the_orphan_entry_stays_deleted_on_server() = runScenario {
        val site = aSiteWithASupervisor("b1reinvite")
        site.supervisor.goOffline()
        val entry = site.supervisor.entryOf(site.supervisor.logs.createWorkEntry(site.supervisorStage, today()), EntryType.WORK)
        site.owner.sync.syncProject(site.ownerProject)
        val supervisorId = site.owner.projects.observeMembers(site.ownerProject).first().single { it.role == ProjectRole.SUPERVISOR }.userId
        val projectServerId = site.owner.db.projectDao().findByLocalId(site.ownerProject)!!.serverId!!
        site.owner.client.delete("${com.dmb.chantiertracker.data.remote.ApiRoutes.projectMembers(projectServerId)}/$supervisorId")
        site.supervisor.goOnline()
        site.supervisor.sync.syncNow()
        val ghost = site.supervisor.db.projectDao().findByLocalId(site.supervisorProject)
        println("B-1 réinvitation — après retrait : projet=${ghost?.syncStatus}/${ghost?.lastSyncError} ; saisie=${site.supervisor.db.dailyEntryDao().findByLocalId(entry).state()}")
        assertEquals(SyncError.DELETED_ON_SERVER, ghost?.lastSyncError)

        site.owner.invitations.invite(site.ownerProject, site.supervisorEmail)
        site.supervisor.invitations.acceptInvitation(site.supervisor.invitations.listIncomingInvitations().single().token)
        val passes = listOf(site.supervisor.sync.syncNow(), site.supervisor.sync.syncProject(site.supervisorProject))

        val back = site.supervisor.db.projectDao().findByLocalId(site.supervisorProject)
        val stage = site.supervisor.db.stageDao().findByLocalId(site.supervisorStage)
        val orphan = site.supervisor.db.dailyEntryDao().findByLocalId(entry)
        val projects = site.supervisor.projects.observeProjects().first()
        println("B-1 réinvitation — passes=$passes ; projet=${back?.syncStatus}/${back?.lastSyncError} ; étape=${stage?.syncStatus}/${stage?.lastSyncError} ; saisie=${orphan.state()} ; projets listés=${projects.map { it.name }}")

        assertEquals(SyncStatus.SYNCED, back?.syncStatus, "le fantôme redevient le projet")
        assertEquals(null, back?.lastSyncError)
        assertEquals(1, projects.size, "retrouvé par son id serveur, pas dupliqué")
        assertEquals(SyncStatus.CONFLICTED, orphan?.syncStatus, "la saisie orpheline n'est pas renvoyée d'elle-même")
        assertEquals(SyncError.DELETED_ON_SERVER, orphan?.lastSyncError)
        assertEquals(null, orphan?.serverId)
    }

    // ─── P10 — a report, processed by the owner ──────────────────────────────

    @Test
    fun p10_a_report_by_the_supervisor_is_listed_for_the_owner_and_marked_processed() = runScenario {
        val site = aSiteWithASupervisor("report")
        site.owner.logs.createPurchaseEntry(site.ownerStage, today())
        site.owner.sync.syncNow()

        site.supervisor.sync.syncStage(site.supervisorStage)
        val day = site.supervisor.logs.observeLogs(site.supervisorStage).first().single()
        site.supervisor.sync.syncLog(day.localId)
        val supervisorEntry = site.supervisor.logs.observeLog(day.localId).first()!!.entries.single { it.type == EntryType.PURCHASE }
        site.supervisor.reports.createReport(supervisorEntry.localId, "Le bon de livraison ne correspond pas aux quantités.")

        val listed = site.owner.reports.projectReports(site.ownerProject, 0, ReportSort.NEWEST_FIRST)
        println("P10 — signalements vus par le propriétaire : ${listed.items.map { Triple(it.authorName, it.message, it.status) }}")
        val report = listed.items.single()
        assertEquals(ReportStatus.NEW, report.status)
        assertEquals("QA B Superviseur report", report.authorName)

        val processed = site.owner.reports.markProcessed(report.id)
        println("P10 — après traitement : ${processed.status} le ${processed.processedAt}")
        assertEquals(ReportStatus.PROCESSED, processed.status)

        val seenBySupervisor = runCatching { site.supervisor.reports.projectReports(site.supervisorProject, 0, ReportSort.NEWEST_FIRST) }
        println("P10 — ce que le superviseur peut voir de ses signalements : $seenBySupervisor")
    }
}
