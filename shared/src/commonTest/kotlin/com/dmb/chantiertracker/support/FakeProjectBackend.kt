package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.remote.ProjectApi
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class ServerProject(
    val id: Long,
    var name: String,
    var description: String? = null,
    var location: String? = null,
    var currency: String = "USD",
    var timezone: String = "UTC",
    var ownerId: Long = 1L,
    var ownerPlan: String = "FREE",
    var status: String = "IN_PROGRESS",
    var createdAt: String = "2026-01-01T09:00:00",
    var updatedAt: String = "2026-01-01T09:00:00",
)

class ServerMember(
    val userId: Long,
    val name: String,
    val email: String,
    val role: String = "ADMIN",
)

class ServerInvitation(
    val id: Long,
    val projectId: Long,
    var email: String,
    var role: String = "SUPERVISOR",
    var status: String = "PENDING",
    val createdAt: String = "2026-09-01T10:00:00",
)

class ServerPendingInvitation(
    val token: String,
    val projectId: Long,
    val projectName: String,
    val role: String = "SUPERVISOR",
    val invitedByName: String? = "Jean Marchand",
    val createdAt: String = "2026-09-01T10:00:00",
    val expiresAt: String = "2026-09-08T10:00:00",
)

class ServerStage(
    val id: Long,
    val projectId: Long,
    var name: String,
    var description: String? = null,
    var estimatedBudget: Double? = null,
    var startDate: String? = null,
    var endDate: String? = null,
    var status: String = "IN_PROGRESS",
    var createdAt: String = "2026-01-01T09:00:00",
)

class ServerMaterial(
    val id: Long,
    val projectId: Long,
    var name: String,
    var unit: String,
)

class ServerLog(
    val id: Long,
    val stageId: Long,
    val date: String,
)

class ServerEntry(
    val id: Long,
    val dailyLogId: Long,
    val type: String,
    var summary: String? = null,
    var modifiedAt: String = "2026-01-01T09:00:00",
)

class ServerPurchaseLine(
    val id: Long,
    val entryId: Long,
    var materialId: Long,
    var quantity: Double,
    var unitPrice: Double,
    var supplier: String? = null,
) {
    val totalPrice: Double get() = quantity * unitPrice
}

class ServerConsumptionLine(
    val id: Long,
    val entryId: Long,
    var materialId: Long,
    var quantity: Double,
)

class ServerAttachment(
    val id: Long,
    val entryId: Long,
    val originalName: String = "photo.jpg",
    val mimeType: String = "image/jpeg",
    val bytes: ByteArray = byteArrayOf(1, 2, 3),
)

/**
 * A minimal, stateful stand-in for the projects REST API. Tests mutate
 * [projects] / [planLimitReached] directly to set up scenarios, then read
 * the same state back to assert what a push did.
 */
class FakeProjectBackend {

    val projects = mutableListOf<ServerProject>()
    val members = mutableMapOf<Long, MutableList<ServerMember>>()
    val stages = mutableListOf<ServerStage>()
    val materials = mutableListOf<ServerMaterial>()
    val logs = mutableListOf<ServerLog>()
    val entries = mutableListOf<ServerEntry>()
    val purchaseLines = mutableListOf<ServerPurchaseLine>()
    val consumptionLines = mutableListOf<ServerConsumptionLine>()
    val attachments = mutableListOf<ServerAttachment>()
    val invitations = mutableListOf<ServerInvitation>()
    val myPendingInvitations = mutableListOf<ServerPendingInvitation>()
    var planLimitReached = false
    /** When set, POST /invitations/{token}/accept answers this status. */
    var acceptStatus: HttpStatusCode? = null
    var nextId = 100L
    var nextStageId = 500L
    var nextMaterialId = 700L
    var nextLogId = 800L
    var nextEntryId = 900L
    var nextLineId = 1_000L
    var nextAttachmentId = 1_100L
    var nextInvitationId = 1_200L

    /** When true, POST purchase/consumption line answers 409 (mirrors InsufficientStockException). */
    var lineWriteConflict = false

    /** When true, GET/POST/DELETE on invitations answers 403 (mirrors a non-ADMIN caller). */
    var invitationsForbidden = false

    fun seedInvitation(i: ServerInvitation) = i.also { invitations += it }
    fun seedPendingForMe(i: ServerPendingInvitation) = i.also { myPendingInvitations += it }
    fun seedMaterial(m: ServerMaterial) = m.also { materials += it }
    fun seedLog(l: ServerLog) = l.also { logs += it }
    fun seedEntry(e: ServerEntry) = e.also { entries += it }
    fun seedPurchaseLine(l: ServerPurchaseLine) = l.also { purchaseLines += it }
    fun seedConsumptionLine(l: ServerConsumptionLine) = l.also { consumptionLines += it }
    fun seedAttachment(a: ServerAttachment) = a.also { attachments += it }

    /** When false, a POST stage drops any `estimatedBudget` (mirrors a SUPERVISOR creating a stage). */
    var stageBudgetAllowed = true

    /** When true, PATCH/DELETE on a stage answers 403 (mirrors a non-ADMIN pushing a stage edit). */
    var stageWriteForbidden = false
    val receivedMethods = mutableListOf<String>()

    fun seed(project: ServerProject) = project.also { projects += it }

    fun seedMembers(projectId: Long, vararg member: ServerMember) {
        members.getOrPut(projectId) { mutableListOf() }.addAll(member)
    }

    fun seedStage(stage: ServerStage) = stage.also { stages += it }

    private fun client(tokenStorage: FakeTokenStorage) =
        RecordingMockClient(tokenStorage) { request -> handle(request) }.client

    fun api(tokenStorage: FakeTokenStorage = FakeTokenStorage(com.dmb.chantiertracker.data.local.AuthTokens("a", "r"))): ProjectApi =
        ProjectApi(client(tokenStorage))

    fun stageApi(tokenStorage: FakeTokenStorage = FakeTokenStorage(com.dmb.chantiertracker.data.local.AuthTokens("a", "r"))): com.dmb.chantiertracker.data.remote.StageApi =
        com.dmb.chantiertracker.data.remote.StageApi(client(tokenStorage))

    private val sharedTokenStorage = FakeTokenStorage(com.dmb.chantiertracker.data.local.AuthTokens("a", "r"))
    fun materialApi() = com.dmb.chantiertracker.data.remote.MaterialApi(client(sharedTokenStorage))
    fun dailyLogApi() = com.dmb.chantiertracker.data.remote.DailyLogApi(client(sharedTokenStorage))
    fun purchaseLineApi() = com.dmb.chantiertracker.data.remote.PurchaseLineApi(client(sharedTokenStorage))
    fun consumptionLineApi() = com.dmb.chantiertracker.data.remote.ConsumptionLineApi(client(sharedTokenStorage))
    fun attachmentApi() = com.dmb.chantiertracker.data.remote.AttachmentApi(client(sharedTokenStorage))
    fun invitationApi() = com.dmb.chantiertracker.data.remote.InvitationApi(client(sharedTokenStorage))

    private suspend fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        val path = request.url.encodedPath.removePrefix("/api/v1")
        receivedMethods += "${request.method.value} $path"
        val idInPath = Regex("""/projects/(\d+)$""").find(path)?.groupValues?.get(1)?.toLong()
        val membersProjectId = Regex("""/projects/(\d+)/members$""").find(path)?.groupValues?.get(1)?.toLong()
        val invitationsProjectId = Regex("""/projects/(\d+)/invitations$""").find(path)?.groupValues?.get(1)?.toLong()
        val invitationId = Regex("""/invitations/(\d+)$""").find(path)?.groupValues?.get(1)?.toLong()
        val acceptToken = Regex("""/invitations/([^/]+)/accept$""").find(path)?.groupValues?.get(1)
        val declineToken = Regex("""/invitations/([^/]+)/decline$""").find(path)?.groupValues?.get(1)
        val isMyInvitations = path == "/users/me/invitations"
        val stagesProjectId = Regex("""/projects/(\d+)/stages$""").find(path)?.groupValues?.get(1)?.toLong()
        val stageId = Regex("""/stages/(\d+)$""").find(path)?.groupValues?.get(1)?.toLong()
        val materialsProjectId = Regex("""/projects/(\d+)/materials$""").find(path)?.groupValues?.get(1)?.toLong()
        val materialId = Regex("""/materials/(\d+)$""").find(path)?.groupValues?.get(1)?.toLong()
        val stageLogsId = Regex("""/stages/(\d+)/logs$""").find(path)?.groupValues?.get(1)?.toLong()
        val purchaseEntryMatch = Regex("""/stages/(\d+)/logs/([^/]+)/purchases$""").find(path)?.groupValues
        val workEntryMatch = Regex("""/stages/(\d+)/logs/([^/]+)/works$""").find(path)?.groupValues
        val logDetailId = Regex("""/logs/(\d+)$""").find(path)?.groupValues?.get(1)?.toLong()
        val entryId = Regex("""/entries/(\d+)$""").find(path)?.groupValues?.get(1)?.toLong()
        val purchaseLinesEntryId = Regex("""/entries/(\d+)/purchase-lines$""").find(path)?.groupValues?.get(1)?.toLong()
        val purchaseLineId = Regex("""/purchase-lines/(\d+)$""").find(path)?.groupValues?.get(1)?.toLong()
        val consumptionLinesEntryId = Regex("""/entries/(\d+)/consumption-lines$""").find(path)?.groupValues?.get(1)?.toLong()
        val consumptionLineId = Regex("""/consumption-lines/(\d+)$""").find(path)?.groupValues?.get(1)?.toLong()
        val attachmentsEntryId = Regex("""/entries/(\d+)/attachments$""").find(path)?.groupValues?.get(1)?.toLong()
        val attachmentId = Regex("""/attachments/(\d+)$""").find(path)?.groupValues?.get(1)?.toLong()

        return when {
            request.method == HttpMethod.Get && stagesProjectId != null -> {
                if (projects.none { it.id == stagesProjectId }) {
                    return respondProblem(HttpStatusCode.NotFound, "Projet introuvable.")
                }
                respondJson(stagesPageJson(stagesProjectId))
            }

            request.method == HttpMethod.Post && stagesProjectId != null -> {
                if (projects.none { it.id == stagesProjectId }) {
                    return respondProblem(HttpStatusCode.NotFound, "Projet introuvable.")
                }
                val body = request.jsonBody()
                val created = ServerStage(
                    id = nextStageId++,
                    projectId = stagesProjectId,
                    name = body.string("name") ?: "sans nom",
                    description = body.string("description"),
                    estimatedBudget = if (stageBudgetAllowed) body.number("estimatedBudget") else null,
                    startDate = body.string("startDate"),
                    endDate = body.string("endDate"),
                    createdAt = "2026-06-01T08:00:00",
                )
                stages += created
                respondJson(stageJson(created), HttpStatusCode.Created)
            }

            request.method == HttpMethod.Get && stageId != null -> {
                val stage = stages.firstOrNull { it.id == stageId }
                    ?: return respondProblem(HttpStatusCode.NotFound, "Étape introuvable.")
                respondJson(stageJson(stage))
            }

            request.method == HttpMethod.Patch && stageId != null -> {
                if (stageWriteForbidden) {
                    return respondProblem(HttpStatusCode.Forbidden, "Action réservée à un administrateur.")
                }
                val stage = stages.firstOrNull { it.id == stageId }
                    ?: return respondProblem(HttpStatusCode.NotFound, "Étape introuvable.")
                val body = request.jsonBody()
                body.string("name")?.let { stage.name = it }
                body.string("description")?.let { stage.description = it }
                body.number("estimatedBudget")?.let { stage.estimatedBudget = it }
                body.string("startDate")?.let { stage.startDate = it }
                body.string("endDate")?.let { stage.endDate = it }
                body.string("status")?.let { stage.status = it }
                respondJson(stageJson(stage))
            }

            request.method == HttpMethod.Delete && stageId != null -> {
                if (stageWriteForbidden) {
                    return respondProblem(HttpStatusCode.Forbidden, "Action réservée à un administrateur.")
                }
                stages.removeAll { it.id == stageId }
                respondJson("", HttpStatusCode.NoContent)
            }

            // ─── materials ───────────────────────────────────────────────────
            request.method == HttpMethod.Get && materialsProjectId != null ->
                respondJson(pageOf(materials.filter { it.projectId == materialsProjectId }.map(::materialJson)))

            request.method == HttpMethod.Post && materialsProjectId != null -> {
                val body = request.jsonBody()
                val created = ServerMaterial(nextMaterialId++, materialsProjectId, body.string("name") ?: "", body.string("unit") ?: "")
                materials += created
                respondJson(materialJson(created), HttpStatusCode.Created)
            }

            request.method == HttpMethod.Patch && materialId != null -> {
                val m = materials.firstOrNull { it.id == materialId }
                    ?: return respondProblem(HttpStatusCode.NotFound, "Matériau introuvable.")
                val body = request.jsonBody()
                body.string("name")?.let { m.name = it }
                body.string("unit")?.let { m.unit = it }
                respondJson(materialJson(m))
            }

            // ─── daily logs / entries ────────────────────────────────────────
            request.method == HttpMethod.Get && stageLogsId != null ->
                respondJson(pageOf(logs.filter { it.stageId == stageLogsId }.map(::logSummaryJson)))

            request.method == HttpMethod.Post && purchaseEntryMatch != null ->
                createEntry(purchaseEntryMatch[1].toLong(), purchaseEntryMatch[2], "PURCHASE", request)

            request.method == HttpMethod.Post && workEntryMatch != null ->
                createEntry(workEntryMatch[1].toLong(), workEntryMatch[2], "WORK", request)

            request.method == HttpMethod.Get && logDetailId != null -> {
                val log = logs.firstOrNull { it.id == logDetailId }
                    ?: return respondProblem(HttpStatusCode.NotFound, "Journée introuvable.")
                respondJson(logDetailJson(log))
            }

            request.method == HttpMethod.Patch && entryId != null -> {
                val e = entries.firstOrNull { it.id == entryId }
                    ?: return respondProblem(HttpStatusCode.NotFound, "Entrée introuvable.")
                e.summary = request.jsonBody().string("summary")
                e.modifiedAt = patchAppliedAt
                respondJson(entryJson(e))
            }

            request.method == HttpMethod.Delete && entryId != null -> {
                entries.removeAll { it.id == entryId }
                respondJson("", HttpStatusCode.NoContent)
            }

            // ─── purchase lines ──────────────────────────────────────────────
            request.method == HttpMethod.Get && purchaseLinesEntryId != null ->
                respondJson(pageOf(purchaseLines.filter { it.entryId == purchaseLinesEntryId }.map(::purchaseLineJson)))

            request.method == HttpMethod.Post && purchaseLinesEntryId != null -> {
                if (lineWriteConflict) return respondProblem(HttpStatusCode.Conflict, "Stock insuffisant.")
                val body = request.jsonBody()
                val created = ServerPurchaseLine(
                    id = nextLineId++,
                    entryId = purchaseLinesEntryId,
                    materialId = body.string("materialId")?.toLong() ?: 0L,
                    quantity = body.number("quantity") ?: 0.0,
                    unitPrice = body.number("unitPrice") ?: 0.0,
                    supplier = body.string("supplier"),
                )
                purchaseLines += created
                respondJson(purchaseLineJson(created), HttpStatusCode.Created)
            }

            request.method == HttpMethod.Patch && purchaseLineId != null -> {
                val l = purchaseLines.firstOrNull { it.id == purchaseLineId }
                    ?: return respondProblem(HttpStatusCode.NotFound, "Ligne introuvable.")
                val body = request.jsonBody()
                body.number("quantity")?.let { l.quantity = it }
                body.number("unitPrice")?.let { l.unitPrice = it }
                l.supplier = body.string("supplier")
                respondJson(purchaseLineJson(l))
            }

            request.method == HttpMethod.Delete && purchaseLineId != null -> {
                purchaseLines.removeAll { it.id == purchaseLineId }
                respondJson("", HttpStatusCode.NoContent)
            }

            // ─── consumption lines ───────────────────────────────────────────
            request.method == HttpMethod.Get && consumptionLinesEntryId != null ->
                respondJson(pageOf(consumptionLines.filter { it.entryId == consumptionLinesEntryId }.map(::consumptionLineJson)))

            request.method == HttpMethod.Post && consumptionLinesEntryId != null -> {
                if (lineWriteConflict) return respondProblem(HttpStatusCode.Conflict, "Stock insuffisant.")
                val body = request.jsonBody()
                val created = ServerConsumptionLine(
                    id = nextLineId++,
                    entryId = consumptionLinesEntryId,
                    materialId = body.string("materialId")?.toLong() ?: 0L,
                    quantity = body.number("quantity") ?: 0.0,
                )
                consumptionLines += created
                respondJson(consumptionLineJson(created), HttpStatusCode.Created)
            }

            request.method == HttpMethod.Patch && consumptionLineId != null -> {
                val l = consumptionLines.firstOrNull { it.id == consumptionLineId }
                    ?: return respondProblem(HttpStatusCode.NotFound, "Ligne introuvable.")
                request.jsonBody().number("quantity")?.let { l.quantity = it }
                respondJson(consumptionLineJson(l))
            }

            request.method == HttpMethod.Delete && consumptionLineId != null -> {
                consumptionLines.removeAll { it.id == consumptionLineId }
                respondJson("", HttpStatusCode.NoContent)
            }

            // ─── attachments ─────────────────────────────────────────────────
            request.method == HttpMethod.Get && attachmentsEntryId != null ->
                respondJson(pageOf(attachments.filter { it.entryId == attachmentsEntryId }.map(::attachmentJson)))

            request.method == HttpMethod.Post && attachmentsEntryId != null -> {
                val created = ServerAttachment(nextAttachmentId++, attachmentsEntryId)
                attachments += created
                respondJson(attachmentJson(created), HttpStatusCode.Created)
            }

            request.method == HttpMethod.Get && attachmentId != null -> {
                val a = attachments.firstOrNull { it.id == attachmentId }
                    ?: return respondProblem(HttpStatusCode.NotFound, "Justificatif introuvable.")
                respond(a.bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, a.mimeType))
            }

            request.method == HttpMethod.Delete && attachmentId != null -> {
                attachments.removeAll { it.id == attachmentId }
                respondJson("", HttpStatusCode.NoContent)
            }

            request.method == HttpMethod.Get && path == "/projects" -> respondJson(pageJson())

            request.method == HttpMethod.Get && membersProjectId != null -> {
                if (projects.none { it.id == membersProjectId }) {
                    return respondProblem(HttpStatusCode.NotFound, "Projet introuvable.")
                }
                respondJson(membersPageJson(membersProjectId))
            }

            // ─── invitations (ADMIN only) ────────────────────────────────────
            request.method == HttpMethod.Get && invitationsProjectId != null -> {
                if (invitationsForbidden) return respondProblem(HttpStatusCode.Forbidden, "Action réservée à un administrateur.")
                respondJson(pageOf(invitations.filter { it.projectId == invitationsProjectId }.map(::invitationJson)))
            }

            request.method == HttpMethod.Post && invitationsProjectId != null -> {
                if (invitationsForbidden) return respondProblem(HttpStatusCode.Forbidden, "Action réservée à un administrateur.")
                if (planLimitReached) {
                    return respondProblem(HttpStatusCode.Forbidden, "Vous avez atteint la limite de superviseurs par projet de votre plan.")
                }
                val body = request.jsonBody()
                val created = ServerInvitation(
                    id = nextInvitationId++,
                    projectId = invitationsProjectId,
                    email = body.string("email") ?: "",
                    role = body.string("role") ?: "SUPERVISOR",
                )
                invitations += created
                respondJson(invitationJson(created), HttpStatusCode.Created)
            }

            request.method == HttpMethod.Delete && invitationId != null -> {
                if (invitationsForbidden) return respondProblem(HttpStatusCode.Forbidden, "Action réservée à un administrateur.")
                if (invitations.none { it.id == invitationId }) {
                    return respondProblem(HttpStatusCode.NotFound, "Invitation introuvable.")
                }
                invitations.removeAll { it.id == invitationId }
                respondJson("", HttpStatusCode.NoContent)
            }

            request.method == HttpMethod.Get && isMyInvitations -> {
                val items = myPendingInvitations.joinToString(",") { pendingForMeJson(it) }
                respondJson("[$items]")
            }

            request.method == HttpMethod.Post && acceptToken != null -> {
                acceptStatus?.let { return respondProblem(it, "Cette invitation n'est plus valide.") }
                if (myPendingInvitations.none { it.token == acceptToken }) {
                    return respondProblem(HttpStatusCode.NotFound, "Invitation introuvable.")
                }
                myPendingInvitations.removeAll { it.token == acceptToken }
                respondJson("""{"message":"Invitation acceptée avec succès."}""")
            }

            request.method == HttpMethod.Post && declineToken != null -> {
                if (myPendingInvitations.none { it.token == declineToken }) {
                    return respondProblem(HttpStatusCode.NotFound, "Invitation introuvable.")
                }
                myPendingInvitations.removeAll { it.token == declineToken }
                respondJson("", HttpStatusCode.NoContent)
            }

            request.method == HttpMethod.Get && idInPath != null -> {
                val project = projects.firstOrNull { it.id == idInPath }
                    ?: return respondProblem(HttpStatusCode.NotFound, "Projet introuvable.")
                respondJson(detailJson(project))
            }

            request.method == HttpMethod.Post && path == "/projects" -> {
                if (planLimitReached) {
                    return respondProblem(HttpStatusCode.Forbidden, "Vous avez atteint la limite de projets de votre formule.")
                }
                val body = request.jsonBody()
                val created = ServerProject(
                    id = nextId++,
                    name = body.string("name") ?: "sans nom",
                    description = body.string("description"),
                    location = body.string("location"),
                    currency = body.string("currency") ?: "USD",
                    timezone = body.string("timezone") ?: "UTC",
                    createdAt = "2026-06-01T08:00:00",
                    updatedAt = "2026-06-01T08:00:00",
                )
                projects += created
                respondJson(listItemJson(created), HttpStatusCode.Created)
            }

            request.method == HttpMethod.Patch && idInPath != null -> {
                val project = projects.firstOrNull { it.id == idInPath }
                    ?: return respondProblem(HttpStatusCode.NotFound, "Projet introuvable.")
                val body = request.jsonBody()
                body.string("name")?.let { project.name = it }
                body.string("description")?.let { project.description = it }
                body.string("location")?.let { project.location = it }
                body.string("currency")?.let { project.currency = it }
                body.string("timezone")?.let { project.timezone = it }
                body.string("status")?.let { project.status = it }
                project.updatedAt = patchAppliedAt
                respondJson(listItemJson(project))
            }

            request.method == HttpMethod.Delete && idInPath != null -> {
                projects.removeAll { it.id == idInPath }
                respondJson("", HttpStatusCode.NoContent)
            }

            else -> respondProblem(HttpStatusCode.NotFound, "route inconnue: $path")
        }
    }

    /** The `updatedAt` a PATCH stamps on the server row — overridable per test. */
    var patchAppliedAt: String = "2026-09-02T12:00:00"

    private fun pageJson(): String =
        """{"content":[${projects.joinToString(",") { listItemJson(it) }}],"totalElements":${projects.size}}"""

    private fun membersPageJson(projectId: Long): String {
        val list = members[projectId].orEmpty()
        val items = list.joinToString(",") {
            """{"userId":${it.userId},"name":${it.name.q()},"email":${it.email.q()},"role":${it.role.q()}}"""
        }
        return """{"content":[$items],"totalElements":${list.size}}"""
    }

    private fun listItemJson(p: ServerProject): String = """
        {"id":${p.id},"name":${p.name.q()},"description":${p.description.q()},"location":${p.location.q()},
         "currency":${p.currency.q()},"timezone":${p.timezone.q()},"ownerId":${p.ownerId},
         "status":${p.status.q()},"createdAt":${p.createdAt.q()},"updatedAt":${p.updatedAt.q()}}
    """.trimIndent()

    private fun detailJson(p: ServerProject): String = """
        {"id":${p.id},"name":${p.name.q()},"description":${p.description.q()},"location":${p.location.q()},
         "currency":${p.currency.q()},"timezone":${p.timezone.q()},"ownerId":${p.ownerId},"ownerPlan":${p.ownerPlan.q()},
         "status":${p.status.q()},"createdAt":${p.createdAt.q()},"updatedAt":${p.updatedAt.q()},
         "totalEstimatedBudget":null,"totalSpent":null}
    """.trimIndent()

    private fun stagesPageJson(projectId: Long): String {
        val list = stages.filter { it.projectId == projectId }
        return """{"content":[${list.joinToString(",") { stageJson(it) }}],"totalElements":${list.size}}"""
    }

    private fun stageJson(s: ServerStage): String = """
        {"id":${s.id},"projectId":${s.projectId},"name":${s.name.q()},"description":${s.description.q()},
         "estimatedBudget":${s.estimatedBudget ?: "null"},"spentAmount":0,"spentPercentage":null,
         "startDate":${s.startDate.q()},"endDate":${s.endDate.q()},"status":${s.status.q()},
         "createdAt":${s.createdAt.q()}}
    """.trimIndent()

    // The backend creates the day implicitly if it doesn't exist for this
    // (stage, date) pair, then adds the entry — mirrored here.
    private suspend fun MockRequestHandleScope.createEntry(
        stageId: Long,
        date: String,
        type: String,
        request: HttpRequestData,
    ): HttpResponseData {
        val log = logs.firstOrNull { it.stageId == stageId && it.date == date }
            ?: ServerLog(nextLogId++, stageId, date).also { logs += it }
        val existing = entries.firstOrNull { it.dailyLogId == log.id && it.type == type }
        if (existing != null) {
            return respondProblem(HttpStatusCode.Conflict, "Une entrée de ce type existe déjà pour cette journée.")
        }
        val created = ServerEntry(nextEntryId++, log.id, type, request.jsonBody().string("summary"))
        entries += created
        return respondJson(entryJson(created), HttpStatusCode.Created)
    }

    private fun pageOf(items: List<String>): String =
        """{"content":[${items.joinToString(",")}],"totalElements":${items.size}}"""

    private fun materialJson(m: ServerMaterial): String =
        """{"id":${m.id},"projectId":${m.projectId},"name":${m.name.q()},"unit":${m.unit.q()}}"""

    private fun invitationJson(i: ServerInvitation): String =
        """{"id":${i.id},"projectId":${i.projectId},"email":${i.email.q()},"role":${i.role.q()},
            "invitedById":1,"createdAt":${i.createdAt.q()},"expiresAt":"2026-09-08T10:00:00","status":${i.status.q()}}"""

    private fun pendingForMeJson(i: ServerPendingInvitation): String =
        """{"token":${i.token.q()},"projectId":${i.projectId},"projectName":${i.projectName.q()},"role":${i.role.q()},
            "invitedByName":${i.invitedByName?.q() ?: "null"},"createdAt":${i.createdAt.q()},"expiresAt":${i.expiresAt.q()}}"""

    private fun logSummaryJson(l: ServerLog): String {
        val hasPurchase = entries.any { it.dailyLogId == l.id && it.type == "PURCHASE" }
        val hasWork = entries.any { it.dailyLogId == l.id && it.type == "WORK" }
        return """{"id":${l.id},"stageId":${l.stageId},"date":${l.date.q()},"hasPurchase":$hasPurchase,"hasWork":$hasWork}"""
    }

    private fun logDetailJson(l: ServerLog): String {
        val es = entries.filter { it.dailyLogId == l.id }.joinToString(",") { entryJson(it) }
        return """{"id":${l.id},"stageId":${l.stageId},"date":${l.date.q()},"entries":[$es]}"""
    }

    private fun entryJson(e: ServerEntry): String =
        """{"id":${e.id},"dailyLogId":${e.dailyLogId},"type":${e.type.q()},"summary":${e.summary.q()},
            "createdById":1,"createdAt":"2026-01-01T09:00:00","modifiedById":1,"modifiedAt":${e.modifiedAt.q()}}"""

    private fun purchaseLineJson(l: ServerPurchaseLine): String =
        """{"id":${l.id},"entryId":${l.entryId},"materialId":${l.materialId},"quantity":${l.quantity},
            "unitPrice":${l.unitPrice},"totalPrice":${l.totalPrice},"supplier":${l.supplier.q()},"createdAt":"2026-01-01T09:00:00"}"""

    private fun consumptionLineJson(l: ServerConsumptionLine): String =
        """{"id":${l.id},"entryId":${l.entryId},"materialId":${l.materialId},"quantity":${l.quantity},"createdAt":"2026-01-01T09:00:00"}"""

    private fun attachmentJson(a: ServerAttachment): String =
        """{"id":${a.id},"entryId":${a.entryId},"originalName":${a.originalName.q()},"mimeType":${a.mimeType.q()},
            "size":${a.bytes.size},"durationSeconds":null,"uploadedById":1,"uploadedAt":"2026-01-01T09:00:00"}"""
}

private val json = Json { ignoreUnknownKeys = true }

private suspend fun HttpRequestData.jsonBody(): JsonObject =
    json.parseToJsonElement(body.toByteArray().decodeToString()) as JsonObject

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.number(key: String): Double? = this[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

private fun String?.q(): String = if (this == null) "null" else "\"" + replace("\"", "\\\"") + "\""
