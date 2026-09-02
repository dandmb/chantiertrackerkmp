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

/**
 * A minimal, stateful stand-in for the projects REST API. Tests mutate
 * [projects] / [planLimitReached] directly to set up scenarios, then read
 * the same state back to assert what a push did.
 */
class FakeProjectBackend {

    val projects = mutableListOf<ServerProject>()
    val members = mutableMapOf<Long, MutableList<ServerMember>>()
    var planLimitReached = false
    var nextId = 100L
    val receivedMethods = mutableListOf<String>()

    fun seed(project: ServerProject) = project.also { projects += it }

    fun seedMembers(projectId: Long, vararg member: ServerMember) {
        members.getOrPut(projectId) { mutableListOf() }.addAll(member)
    }

    fun api(tokenStorage: FakeTokenStorage = FakeTokenStorage(com.dmb.chantiertracker.data.local.AuthTokens("a", "r"))): ProjectApi {
        val client = RecordingMockClient(tokenStorage) { request -> handle(request) }
        return ProjectApi(client.client)
    }

    private suspend fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        val path = request.url.encodedPath.removePrefix("/api/v1")
        receivedMethods += "${request.method.value} $path"
        val idInPath = Regex("""/projects/(\d+)$""").find(path)?.groupValues?.get(1)?.toLong()
        val membersProjectId = Regex("""/projects/(\d+)/members$""").find(path)?.groupValues?.get(1)?.toLong()

        return when {
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
}

private val json = Json { ignoreUnknownKeys = true }

private suspend fun HttpRequestData.jsonBody(): JsonObject =
    json.parseToJsonElement(body.toByteArray().decodeToString()) as JsonObject

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun String?.q(): String = if (this == null) "null" else "\"" + replace("\"", "\\\"") + "\""
