package com.dmb.chantiertracker

import androidx.room.Room
import com.dmb.chantiertracker.data.AuthStateHolder
import com.dmb.chantiertracker.data.local.DesktopOnboardingStore
import com.dmb.chantiertracker.data.local.DesktopTokenStorage
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.buildChantierDatabase
import com.dmb.chantiertracker.data.remote.AccountApi
import com.dmb.chantiertracker.data.remote.AuthApi
import com.dmb.chantiertracker.data.remote.BillingApi
import com.dmb.chantiertracker.data.remote.ProjectApi
import com.dmb.chantiertracker.data.remote.createHttpClient
import com.dmb.chantiertracker.data.remote.httpClientEngine
import com.dmb.chantiertracker.data.repository.AccountRepositoryImpl
import com.dmb.chantiertracker.data.repository.AuthRepositoryImpl
import com.dmb.chantiertracker.data.repository.BillingRepositoryImpl
import com.dmb.chantiertracker.data.repository.ProjectRepositoryImpl
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.SyncEngine
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.BillingAvailability
import com.dmb.chantiertracker.domain.model.BillingCycle
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ownerCanExportPdf
import com.dmb.chantiertracker.domain.model.ownerMaxHistoryDays
import com.dmb.chantiertracker.domain.model.ownerMaxSupervisorsPerProject
import com.dmb.chantiertracker.presentation.logs.VideoDurationCheck
import com.dmb.chantiertracker.presentation.logs.VideoLimit
import com.dmb.chantiertracker.presentation.logs.ownerVideoLimits
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.founderOnFreeEntitlements
import com.dmb.chantiertracker.support.ownerEntitlements
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * ADR-66 — le vrai code mobile (client Ktor, repositories, SyncEngine, Room) contre un
 * backend réel. Désactivé par défaut, comme [DesktopAuthIntegrationTest] :
 *
 *     ./gradlew :shared:jvmTest --tests '*FoundersLaunchModeIntegrationTest' \
 *         -Dchantiertracker.integrationTests=true \
 *         -Dchantiertracker.integrationBaseUrl=http://localhost:8080/api/v1 \
 *         -Dchantiertracker.integrationPhase=<phase>
 *
 * L'état du serveur ne se pilote pas depuis un compte ordinaire : chaque phase suppose
 * que la base a été préparée à la main, puis remise en état après.
 *
 * - `non-founder` : `billing_open = false`, `mobile-test@local.dev` FREE, `is_founder = false`.
 * - `founder` : `billing_open = false`, même compte avec `is_founder = true`.
 * - `billing-open` : `billing_open = true` (aucune connexion, aucun checkout lancé).
 *
 * Le compte doit posséder au moins un projet.
 */
class FoundersLaunchModeIntegrationTest {

    private val enabled = System.getProperty("chantiertracker.integrationTests") == "true"
    private val phase = System.getProperty("chantiertracker.integrationPhase")
    private val baseUrl = System.getProperty("chantiertracker.integrationBaseUrl") ?: "http://localhost:8080/api/v1"

    private val dir = Files.createTempDirectory("ct-founders-integ")
    private val storage = DesktopTokenStorage(dir)
    private val holder = AuthStateHolder()
    private val client = createHttpClient(
        engine = httpClientEngine(),
        tokenStorage = storage,
        baseUrl = baseUrl,
        enableLogging = false,
        onSessionExpired = { holder.update(AuthState.Unauthenticated) },
    )
    private val auth = AuthRepositoryImpl(
        AuthApi(client), storage, holder, DesktopOnboardingStore(dir.resolve("onboarding.flag")),
        com.dmb.chantiertracker.support.FakeSyncer(), com.dmb.chantiertracker.support.testOwnership(),
    )
    private val billing = BillingRepositoryImpl(BillingApi(client))

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
    private val account = AccountRepositoryImpl(AccountApi(client), db.planUsageDao())
    private val appScope = AppCoroutineScope()
    private val syncEngine = SyncEngine(
        dao = db.projectDao(),
        api = ProjectApi(client),
        stageDao = db.stageDao(),
        stageApi = com.dmb.chantiertracker.data.remote.StageApi(client),
        materialDao = db.materialDao(),
        materialApi = com.dmb.chantiertracker.data.remote.MaterialApi(client),
        dailyLogDao = db.dailyLogDao(),
        dailyEntryDao = db.dailyEntryDao(),
        dailyLogApi = com.dmb.chantiertracker.data.remote.DailyLogApi(client),
        purchaseLineDao = db.purchaseLineDao(),
        purchaseLineApi = com.dmb.chantiertracker.data.remote.PurchaseLineApi(client),
        consumptionLineDao = db.consumptionLineDao(),
        consumptionLineApi = com.dmb.chantiertracker.data.remote.ConsumptionLineApi(client),
        attachmentDao = db.attachmentDao(),
        attachmentApi = com.dmb.chantiertracker.data.remote.AttachmentApi(client),
        attachmentFileStore = com.dmb.chantiertracker.support.FakeAttachmentFileStore(),
        invitationDao = db.invitationDao(),
        invitationApi = com.dmb.chantiertracker.data.remote.InvitationApi(client),
        connectivity = FakeConnectivityObserver(initiallyOnline = true),
        syncState = SyncStateHolder(),
        scope = appScope,
    )
    private val projects = ProjectRepositoryImpl(db.projectDao(), syncEngine, appScope)

    @AfterTest
    fun cleanUp() {
        client.close()
        db.close()
        dir.toFile().deleteRecursively()
    }

    private fun runPhase(name: String, block: suspend () -> Unit) = runBlocking {
        if (!enabled || phase != name) {
            println("Phase '$name' ignorée (integrationTests=$enabled, integrationPhase=$phase).")
            return@runBlocking
        }
        println("=== phase '$name' contre $baseUrl")
        block()
    }

    private suspend fun signInAndPullFirstProject(): ProjectDetail {
        auth.login("mobile-test@local.dev", "ChantierTest1234!")
        assertIs<AuthState.Authenticated>(holder.state.value)
        account.refreshPlanUsage()
        projects.refresh()
        val localId = projects.observeProjects().first().first().localId
        projects.refreshProject(localId)
        return projects.observeProject(localId).first()!!
    }

    @Test
    fun a_free_account_that_is_not_a_founder_keeps_the_free_limits_and_cannot_buy() = runPhase("non-founder") {
        assertEquals(BillingAvailability.UNKNOWN, billing.availability.value)
        billing.refreshAvailability()
        assertEquals(BillingAvailability.CLOSED, billing.availability.value, "lu sans jeton, avant toute connexion")

        val project = signInAndPullFirstProject()
        val usage = account.observePlanUsage().first()!!
        println("plan-usage -> $usage")
        println("détail projet -> ${project.ownerEntitlements}")

        assertEquals(Plan.FREE, usage.plan)
        assertFalse(usage.isFounder)
        assertTrue(usage.historyDaysLimitKnown, "le backend envoie historyDaysLimit")
        assertEquals(30, usage.maxHistoryDays())

        assertEquals(ownerEntitlements(), project.ownerEntitlements)
        assertEquals(false, project.ownerCanExportPdf())
        assertEquals(30, project.ownerMaxHistoryDays())
        assertEquals(1, project.ownerMaxSupervisorsPerProject())
        assertFalse(VideoLimit.canAdd(project.ownerVideoLimits()))

        // Facturation fermée : le serveur refuse par son code, pas par sa phrase.
        billing.refreshAvailability()
        assertFailsWith<DomainException.BillingNotOpen> { billing.startCheckout(Plan.SEMI_FLEX, BillingCycle.MONTHLY) }
        assertEquals(BillingAvailability.CLOSED, billing.availability.value)
    }

    @Test
    fun a_founder_on_the_free_plan_reads_the_semi_flex_floor_everywhere() = runPhase("founder") {
        billing.refreshAvailability()
        assertEquals(BillingAvailability.CLOSED, billing.availability.value)

        val project = signInAndPullFirstProject()
        val usage = account.observePlanUsage().first()!!
        println("plan-usage -> $usage")
        println("détail projet -> ${project.ownerEntitlements}")

        assertEquals(Plan.FREE, usage.plan, "le palier affiché reste FREE")
        assertTrue(usage.isFounder)
        assertEquals(3, usage.projectsLimit)
        assertEquals(150, usage.photosLimit)
        assertEquals(5, usage.videosLimit)
        assertEquals(120, usage.videoDurationLimitSeconds)
        assertEquals(3, usage.supervisorsLimit)
        assertEquals(180, usage.maxHistoryDays(), "écran de facturation : 6 mois, plus 30 jours")

        assertEquals(Plan.FREE, project.ownerPlan)
        assertEquals(founderOnFreeEntitlements, project.ownerEntitlements)
        assertEquals(true, project.ownerCanExportPdf())
        assertEquals(180, project.ownerMaxHistoryDays())
        assertEquals(3, project.ownerMaxSupervisorsPerProject(), "invitation : 3 superviseurs, plus 1")

        val videoLimits = project.ownerVideoLimits()
        assertTrue(VideoLimit.canAdd(videoLimits), "bouton vidéo présent")
        assertIs<VideoDurationCheck.Ok>(VideoLimit.check(videoLimits, durationSeconds = 119.0))
        val tooLong = VideoLimit.check(videoLimits, durationSeconds = 125.0)
        assertIs<VideoDurationCheck.TooLong>(tooLong)
        assertEquals("2 min 00 s", tooLong.limit)

        // Être fondateur n'ouvre pas la facturation.
        assertFailsWith<DomainException.BillingNotOpen> { billing.startCheckout(Plan.SEMI_FLEX, BillingCycle.MONTHLY) }
        assertEquals(BillingAvailability.CLOSED, billing.availability.value)
    }

    @Test
    fun an_open_switch_is_read_as_open_without_any_account() = runPhase("billing-open") {
        billing.refreshAvailability()
        assertEquals(BillingAvailability.OPEN, billing.availability.value)
    }
}
