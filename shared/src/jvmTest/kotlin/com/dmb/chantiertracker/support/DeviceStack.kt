package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.session.LocalDataOwnerStore
import com.dmb.chantiertracker.data.session.LocalDataOwnership
import com.dmb.chantiertracker.data.session.LocalDataWiper
import com.dmb.chantiertracker.data.session.UnsyncedWriteCounter
import androidx.room.Room
import com.dmb.chantiertracker.data.AuthStateHolder
import com.dmb.chantiertracker.data.local.DesktopOnboardingStore
import com.dmb.chantiertracker.data.local.DesktopTokenStorage
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.buildChantierDatabase
import com.dmb.chantiertracker.data.remote.AttachmentApi
import com.dmb.chantiertracker.data.remote.AuthApi
import com.dmb.chantiertracker.data.remote.ConsumptionLineApi
import com.dmb.chantiertracker.data.remote.DailyLogApi
import com.dmb.chantiertracker.data.remote.InvitationApi
import com.dmb.chantiertracker.data.remote.MaterialApi
import com.dmb.chantiertracker.data.remote.ProjectApi
import com.dmb.chantiertracker.data.remote.PurchaseLineApi
import com.dmb.chantiertracker.data.remote.StageApi
import com.dmb.chantiertracker.data.remote.createHttpClient
import com.dmb.chantiertracker.data.remote.httpClientEngine
import com.dmb.chantiertracker.data.repository.AttachmentRepositoryImpl
import com.dmb.chantiertracker.data.repository.AuthRepositoryImpl
import com.dmb.chantiertracker.data.repository.ConsumptionLineRepositoryImpl
import com.dmb.chantiertracker.data.repository.DailyLogRepositoryImpl
import com.dmb.chantiertracker.data.repository.InvitationRepositoryImpl
import com.dmb.chantiertracker.data.repository.MaterialRepositoryImpl
import com.dmb.chantiertracker.data.repository.ProjectRepositoryImpl
import com.dmb.chantiertracker.data.repository.PurchaseLineRepositoryImpl
import com.dmb.chantiertracker.data.repository.StageRepositoryImpl
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.SyncEngine
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import java.nio.file.Files

/**
 * One device, wired like the real app (Ktor client with the Desktop token storage, Room, every
 * repository, the single SyncEngine), against a real backend — the device the QA scenarios drive.
 * Starts online; [connectivity] switches it to airplane mode and back.
 */
class DeviceStack : AutoCloseable {

    private val dir = Files.createTempDirectory("ct-device")
    val storage = DesktopTokenStorage(dir)
    val authState = AuthStateHolder()
    val client = createHttpClient(
        engine = httpClientEngine(),
        tokenStorage = storage,
        baseUrl = IntegrationBackend.baseUrl,
        enableLogging = false,
        onSessionExpired = { authState.update(AuthState.Unauthenticated) },
    )

    val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
    val connectivity = FakeConnectivityObserver(initiallyOnline = true)
    val fileStore = FakeAttachmentFileStore()
    private val scope = AppCoroutineScope()

    val projectApi = ProjectApi(client)
    val stageApi = StageApi(client)
    val dailyLogApi = DailyLogApi(client)
    val purchaseLineApi = PurchaseLineApi(client)
    val consumptionLineApi = ConsumptionLineApi(client)
    val attachmentApi = AttachmentApi(client)
    val invitationApi = InvitationApi(client)
    val materialApi = MaterialApi(client)

    val sync = SyncEngine(
        dao = db.projectDao(),
        api = projectApi,
        stageDao = db.stageDao(),
        stageApi = stageApi,
        materialDao = db.materialDao(),
        materialApi = materialApi,
        dailyLogDao = db.dailyLogDao(),
        dailyEntryDao = db.dailyEntryDao(),
        dailyLogApi = dailyLogApi,
        purchaseLineDao = db.purchaseLineDao(),
        purchaseLineApi = purchaseLineApi,
        consumptionLineDao = db.consumptionLineDao(),
        consumptionLineApi = consumptionLineApi,
        attachmentDao = db.attachmentDao(),
        attachmentApi = attachmentApi,
        attachmentFileStore = fileStore,
        invitationDao = db.invitationDao(),
        invitationApi = invitationApi,
        connectivity = connectivity,
        syncState = SyncStateHolder(),
        scope = scope,
    )

    val preferences = FakeAppPreferences()
    val exportStore = FakeExportFileStore()
    val ownership = LocalDataOwnership(
        LocalDataOwnerStore(preferences),
        LocalDataWiper(db.localDataDao(), fileStore, exportStore) {},
        UnsyncedWriteCounter { db.localDataDao().countUnsynced() },
    )
    val auth = AuthRepositoryImpl(AuthApi(client), storage, authState, DesktopOnboardingStore(dir.resolve("onboarding.flag")), sync, ownership)
    val signOut = com.dmb.chantiertracker.data.repository.SignOutRepositoryImpl(auth, sync, UnsyncedWriteCounter { db.localDataDao().countUnsynced() })

    val projects = ProjectRepositoryImpl(db.projectDao(), sync, scope)
    val stages = StageRepositoryImpl(db.stageDao(), sync, scope)
    val logs = DailyLogRepositoryImpl(db.dailyLogDao(), db.dailyEntryDao(), sync, scope)
    val materials = MaterialRepositoryImpl(db.materialDao(), db.purchaseLineDao(), db.consumptionLineDao(), sync, scope)
    val purchaseLines = PurchaseLineRepositoryImpl(db.purchaseLineDao(), sync, scope)
    val consumptionLines = ConsumptionLineRepositoryImpl(db.consumptionLineDao(), sync, scope)
    val attachments = AttachmentRepositoryImpl(db.attachmentDao(), db.dailyEntryDao(), attachmentApi, fileStore, sync, scope)
    val invitations = InvitationRepositoryImpl(db.invitationDao(), invitationApi, db.projectDao(), sync)
    val reports = com.dmb.chantiertracker.data.repository.ReportRepositoryImpl(
        com.dmb.chantiertracker.data.remote.ReportApi(client), db.dailyEntryDao(), db.projectDao(),
    )

    fun goOffline() = connectivity.setOnline(false)

    fun goOnline() = connectivity.setOnline(true)

    override fun close() {
        client.close()
        db.close()
        dir.toFile().deleteRecursively()
    }
}
