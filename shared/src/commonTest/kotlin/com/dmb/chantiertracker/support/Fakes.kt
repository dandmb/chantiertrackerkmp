package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.core.BuildInfo
import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.local.OnboardingStore
import com.dmb.chantiertracker.data.local.TokenStorage
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectMember
import com.dmb.chantiertracker.domain.repository.AccountRepository
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeBuildInfo(
    override val isDebug: Boolean,
    override val appVersion: String = "1.0-test",
) : BuildInfo

class FakeTokenStorage(initial: AuthTokens? = null) : TokenStorage {
    var tokens: AuthTokens? = initial
    var clearCount = 0

    override suspend fun get(): AuthTokens? = tokens
    override suspend fun save(tokens: AuthTokens) { this.tokens = tokens }
    override suspend fun clear() { tokens = null; clearCount++ }
}

class FakeOnboardingStore(initial: Boolean = false, onboardingSeen: Boolean = false) : OnboardingStore {
    var firstLoginCompleted = initial
        private set
    var onboardingSeen = onboardingSeen
        private set

    override suspend fun hasCompletedFirstLogin(): Boolean = firstLoginCompleted
    override suspend fun markFirstLoginCompleted() { firstLoginCompleted = true }
    override suspend fun hasSeenOnboarding(): Boolean = onboardingSeen
    override suspend fun markOnboardingSeen() { onboardingSeen = true }
}

class FakeAuthRepository : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    override val authState: StateFlow<AuthState> = _authState

    var error: Throwable? = null
    var firstLoginCompleted = false
    var onboardingSeen = false
    val calls = mutableListOf<String>()

    fun emitState(state: AuthState) { _authState.value = state }

    private fun record(call: String) {
        calls += call
        error?.let { throw it }
    }

    override suspend fun hasCompletedFirstLogin(): Boolean = firstLoginCompleted
    override suspend fun hasSeenOnboarding(): Boolean = onboardingSeen
    override suspend fun markOnboardingSeen() { onboardingSeen = true; calls += "markOnboardingSeen" }
    override suspend fun bootstrap() { record("bootstrap") }
    override suspend fun register(email: String, password: String, name: String) = record("register:$email:$password:$name")
    override suspend fun verifyEmail(email: String, code: String) = record("verifyEmail:$email:$code")
    override suspend fun resendCode(email: String) = record("resendCode:$email")
    override suspend fun login(email: String, password: String) {
        record("login:$email:$password")
        firstLoginCompleted = true
    }
    override suspend fun logout() = record("logout")
    override suspend fun forgotPassword(email: String) = record("forgotPassword:$email")
    override suspend fun resetPassword(email: String, code: String, newPassword: String) =
        record("resetPassword:$email:$code:$newPassword")
}

class FakeSyncer : com.dmb.chantiertracker.data.sync.Syncer {
    var requestCount = 0
        private set
    var syncCount = 0
        private set
    var outcome: com.dmb.chantiertracker.data.sync.SyncOutcome =
        com.dmb.chantiertracker.data.sync.SyncOutcome.Synced
    var onSync: (suspend () -> Unit)? = null

    override fun requestSync() { requestCount++ }

    override suspend fun syncNow(): com.dmb.chantiertracker.data.sync.SyncOutcome {
        syncCount++
        onSync?.invoke()
        return outcome
    }
}

class FakeProjectRepository(
    projects: List<Project> = emptyList(),
    detail: ProjectDetail? = null,
    members: List<ProjectMember> = emptyList(),
) : ProjectRepository {

    val projectsFlow = MutableStateFlow(projects)
    val detailFlow = MutableStateFlow(detail)
    val membersFlow = MutableStateFlow(members)

    val log = mutableListOf<String>()
    var lastCreateInput: CreateProjectInput? = null
    var refreshCount = 0
        private set
    var createError: Throwable? = null
    var newLocalId = "local-new"

    override fun observeProjects() = projectsFlow

    override fun observeProject(localId: String) = detailFlow

    override fun observeMembers(localId: String) = membersFlow

    override suspend fun createProject(input: CreateProjectInput): String {
        log += "createProject:${input.name}"
        lastCreateInput = input
        createError?.let { throw it }
        projectsFlow.value = projectsFlow.value + com.dmb.chantiertracker.domain.model.Project(
            localId = newLocalId,
            name = input.name,
            description = input.description,
            location = input.location,
            status = com.dmb.chantiertracker.domain.model.ProjectStatus.IN_PROGRESS,
            createdAt = null,
        )
        return newLocalId
    }

    override suspend fun refresh() {
        refreshCount++
        log += "refresh"
    }
}

class FakeAccountRepository(
    var plan: Plan = Plan.FREE,
    var error: Throwable? = null,
) : AccountRepository {
    override suspend fun getCurrentPlan(): Plan {
        error?.let { throw it }
        return plan
    }
}
