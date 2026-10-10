package com.dmb.chantiertracker.data.session

import com.dmb.chantiertracker.data.AuthStateHolder
import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.remote.AuthApi
import com.dmb.chantiertracker.data.repository.AuthRepositoryImpl
import com.dmb.chantiertracker.data.sync.SyncEngine
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import com.dmb.chantiertracker.support.FakeAppPreferences
import com.dmb.chantiertracker.support.FakeAttachmentDao
import com.dmb.chantiertracker.support.FakeAttachmentFileStore
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeConsumptionLineDao
import com.dmb.chantiertracker.support.FakeDailyEntryDao
import com.dmb.chantiertracker.support.FakeDailyLogDao
import com.dmb.chantiertracker.support.FakeInvitationDao
import com.dmb.chantiertracker.support.FakeMaterialDao
import com.dmb.chantiertracker.support.FakeOnboardingStore
import com.dmb.chantiertracker.support.FakeProjectBackend
import com.dmb.chantiertracker.support.FakeProjectDao
import com.dmb.chantiertracker.support.FakePurchaseLineDao
import com.dmb.chantiertracker.support.FakeStageDao
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.FakeUnsyncedWriteCounter
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.respondJson
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A-1 (ADR-69) — the race the exclusive section exists for: Alice's pending write is being pushed
 * when Bob signs in on the same device. Bob's token must never carry Alice's data, and Bob must
 * never be shown it.
 */
class AccountSwitchDuringSyncTest {

    private val aliceToken = "alice-access"
    private val bobToken = "bob-access"

    @Test
    fun a_push_in_flight_finishes_with_the_previous_token_before_the_new_account_erases_and_signs_in() = runTest {
        val storage = FakeTokenStorage(AuthTokens(aliceToken, "alice-refresh"))
        val projectDao = FakeProjectDao()
        projectDao.upsert(localProject("alice-pending", name = "Projet d'Alice"))

        val backend = FakeProjectBackend()
        val pushStarted = CompletableDeferred<Unit>()
        val releasePush = CompletableDeferred<Unit>()
        backend.beforeHandle = { request ->
            if (request.method == HttpMethod.Post && request.url.encodedPath.endsWith("/projects") && !pushStarted.isCompleted) {
                pushStarted.complete(Unit)
                releasePush.await()
            }
        }
        val engine = SyncEngine(
            dao = projectDao, api = backend.api(storage),
            stageDao = FakeStageDao(), stageApi = backend.stageApi(storage),
            materialDao = FakeMaterialDao(), materialAdoptionDao = com.dmb.chantiertracker.support.FakeMaterialAdoptionDao(), materialApi = backend.materialApi(),
            dailyLogDao = FakeDailyLogDao(), dailyEntryDao = FakeDailyEntryDao(), dailyLogApi = backend.dailyLogApi(),
            purchaseLineDao = FakePurchaseLineDao(), purchaseLineApi = backend.purchaseLineApi(),
            consumptionLineDao = FakeConsumptionLineDao(), consumptionLineApi = backend.consumptionLineApi(),
            attachmentDao = FakeAttachmentDao(), attachmentApi = backend.attachmentApi(),
            attachmentFileStore = FakeAttachmentFileStore(),
            invitationDao = FakeInvitationDao(), invitationApi = backend.invitationApi(),
            stockApi = backend.stockApi(), stockDao = com.dmb.chantiertracker.support.FakeStockDao(),
            connectivity = FakeConnectivityObserver(initiallyOnline = true),
            awaitedServerVersions = com.dmb.chantiertracker.support.NoAwaitedServerVersion,
            syncState = SyncStateHolder(), scope = backgroundScope,
        )

        var pushStillOpenAtErase = true
        val eraser = LocalDataEraser {
            pushStillOpenAtErase = !releasePush.isCompleted
            projectDao.findAll().forEach { projectDao.deleteByLocalId(it.localId) }
        }
        val preferences = FakeAppPreferences(mapOf("local_data_owner_id" to "1"))
        val holder = AuthStateHolder()
        val authClient = RecordingMockClient(storage) { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/login" -> respondJson("""{"accessToken":"$bobToken","refreshToken":"bob-refresh"}""")
                "/api/v1/users/me" -> respondJson("""{"id":2,"email":"bob@chantier.dev","name":"Bob","active":true,"globalRole":"USER"}""")
                else -> respondJson("{}")
            }
        }
        val auth = AuthRepositoryImpl(
            AuthApi(authClient.client), storage, holder, FakeOnboardingStore(), engine,
            LocalDataOwnership(LocalDataOwnerStore(preferences), eraser, FakeUnsyncedWriteCounter()),
        )

        val aliceSync = async { engine.syncNow() }
        withContext(Dispatchers.Default) { withTimeout(5_000) { pushStarted.await() } }

        val bobLogin = async { auth.login("bob@chantier.dev", "BobPass1234!") }
        repeat(20) { yield() }
        assertEquals(AuthTokens(aliceToken, "alice-refresh"), storage.tokens, "Bob's token is not stored while Alice's push is open")
        assertTrue(holder.state.value !is AuthState.Authenticated)

        releasePush.complete(Unit)
        withContext(Dispatchers.Default) { withTimeout(5_000) { aliceSync.await(); bobLogin.await() } }

        assertTrue(!pushStillOpenAtErase, "the erasure waited for the push in flight")
        val projectWrites = backend.receivedAuthorizations.filter { it.first == "POST /projects" }
        assertEquals(listOf("Bearer $aliceToken"), projectWrites.map { it.second }, "Alice's project left with Alice's token only")
        assertTrue(projectDao.findAll().isEmpty(), "nothing of Alice is left for Bob")
        assertIs<AuthState.Authenticated>(holder.state.value)
        assertEquals("2", preferences.store["local_data_owner_id"])

        // A pass requested right after Bob signed in finds nothing to push.
        engine.syncNow()
        assertTrue(backend.receivedAuthorizations.none { it.first == "POST /projects" && it.second == "Bearer $bobToken" })
    }
}
