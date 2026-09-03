package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.remote.ProjectApi
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
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

/**
 * A minimal, stateful stand-in for the projects REST API. Tests mutate
 * [projects] / [planLimitReached] directly to set up scenarios, then read
 * the same state back to assert what a push did.
 */
class FakeProjectBackend {

    val projects = mutableListOf<ServerProject>()
    val members = mutableMapOf<Long, MutableList<ServerMember>>()
    val stages = mutableListOf<ServerStage>()
    var planLimitReached = false
    var nextId = 100L
    var nextStageId = 500L

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

    private suspend fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        val path = request.url.encodedPath.removePrefix("/api/v1")
        receivedMethods += "${request.method.value} $path"
        val idInPath = Regex("""/projects/(\d+)$""").find(path)?.groupValues?.get(1)?.toLong()
        val membersProjectId = Regex("""/projects/(\d+)/members$""").find(path)?.groupValues?.get(1)?.toLong()
        val stagesProjectId = Regex("""/projects/(\d+)/stages$""").find(path)?.groupValues?.get(1)?.toLong()
        val stageId = Regex("""/stages/(\d+)$""").find(path)?.groupValues?.get(1)?.toLong()

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

            request.method == HttpMethod.Get && path == "/projects" -> respondJson(pageJson())

            request.method == HttpMethod.Get && membersProjectId != null -> {
                if (projects.none { it.id == membersProjectId }) {
                    return respondProblem(HttpStatusCode.NotFound, "Projet introuvable.")
                }
                respondJson(membersPageJson(membersProjectId))
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
         "currency":${p.currency.q()},"timezone":${p.timezone.q()},"ownerId":${p.ownerId},"ownerPlan":"FREE",
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
}

private val json = Json { ignoreUnknownKeys = true }

private suspend fun HttpRequestData.jsonBody(): JsonObject =
    json.parseToJsonElement(body.toByteArray().decodeToString()) as JsonObject

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.number(key: String): Double? = this[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

private fun String?.q(): String = if (this == null) "null" else "\"" + replace("\"", "\\\"") + "\""
