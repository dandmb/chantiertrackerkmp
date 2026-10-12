package com.dmb.chantiertracker.data

import androidx.room.Room
import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.local.FileKitAttachmentFileStore
import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.local.db.buildChantierDatabase
import com.dmb.chantiertracker.data.remote.AccountApi
import com.dmb.chantiertracker.data.remote.AttachmentApi
import com.dmb.chantiertracker.data.remote.ConsumptionLineApi
import com.dmb.chantiertracker.data.remote.DailyLogApi
import com.dmb.chantiertracker.data.remote.InvitationApi
import com.dmb.chantiertracker.data.remote.MaterialApi
import com.dmb.chantiertracker.data.remote.ProjectApi
import com.dmb.chantiertracker.data.remote.PurchaseLineApi
import com.dmb.chantiertracker.data.remote.StageApi
import com.dmb.chantiertracker.data.remote.StockApi
import com.dmb.chantiertracker.data.remote.createHttpClient
import com.dmb.chantiertracker.data.remote.httpClientEngine
import com.dmb.chantiertracker.data.repository.AttachmentRepositoryImpl
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.SyncEngine
import com.dmb.chantiertracker.presentation.logs.asUploadFile
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import com.dmb.chantiertracker.support.FakeBackgroundSync
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.localDailyEntry
import com.dmb.chantiertracker.support.localDailyLog
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.localStage
import com.sun.net.httpserver.HttpServer
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AVideoIsNeverLoadedWholeInMemoryTest {

    private val megabyte = 1024L * 1024
    private val videoBytes = 100 * megabyte
    private val allowedGrowth = 32 * megabyte

    private val dir = Files.createTempDirectory("ct-video-memory")
    private val received = AtomicLong()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val (status, body) = when {
                exchange.requestMethod == "POST" && path.endsWith("/attachments") -> {
                    val counted = object : OutputStream() {
                        override fun write(b: Int) { received.incrementAndGet() }
                        override fun write(b: ByteArray, off: Int, len: Int) { received.addAndGet(len.toLong()) }
                    }
                    exchange.requestBody.use { it.transferTo(counted) }
                    201 to """{"id":1,"entryId":900,"originalName":"gros.mp4","mimeType":"video/mp4","size":3,"durationSeconds":30,"uploadedById":1,"uploadedAt":"2026-01-01T09:00:00"}""".toByteArray()
                }
                exchange.requestMethod == "GET" && path.endsWith("/attachments/1") -> 200 to byteArrayOf(9, 9, 9)
                exchange.requestMethod == "GET" && path.endsWith("/projects") ->
                    200 to (if (exchange.requestURI.query.orEmpty().contains("page=0")) """{"content":[{"id":5,"name":"Villa","status":"IN_PROGRESS","currency":"USD","timezone":"UTC","ownerId":1}],"totalElements":1}""" else """{"content":[],"totalElements":1}""").toByteArray()
                else -> 404 to """{"status":404,"detail":"route inconnue"}""".toByteArray()
            }
            exchange.responseHeaders.add("Content-Type", if (status == 200 && body.size == 3) "video/mp4" else "application/json")
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        start()
    }

    private val client = createHttpClient(
        engine = httpClientEngine(),
        tokenStorage = FakeTokenStorage(AuthTokens("a", "r")),
        baseUrl = "http://127.0.0.1:${server.address.port}/api/v1/",
        enableLogging = false,
        onSessionExpired = {},
    )
    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
    private val fileStore = FileKitAttachmentFileStore(newFileName = { UUID.randomUUID().toString() }, directory = { PlatformFile(dir.resolve("attachments").toString()) })
    private val scope = AppCoroutineScope()
    private val engine = SyncEngine(
        dao = db.projectDao(), api = ProjectApi(client), stageDao = db.stageDao(), stageApi = StageApi(client),
        materialDao = db.materialDao(), materialAdoptionDao = db.materialAdoptionDao(), materialApi = MaterialApi(client),
        dailyLogDao = db.dailyLogDao(), dailyEntryDao = db.dailyEntryDao(), dailyLogApi = DailyLogApi(client),
        purchaseLineDao = db.purchaseLineDao(), purchaseLineApi = PurchaseLineApi(client),
        consumptionLineDao = db.consumptionLineDao(), consumptionLineApi = ConsumptionLineApi(client),
        attachmentDao = db.attachmentDao(), attachmentApi = AttachmentApi(client), attachmentFileStore = fileStore,
        invitationDao = db.invitationDao(), invitationApi = InvitationApi(client), accountApi = AccountApi(client),
        stockApi = StockApi(client), stockDao = db.stockDao(), connectivity = FakeConnectivityObserver(initiallyOnline = true),
        syncState = SyncStateHolder(), scope = scope, backgroundSync = FakeBackgroundSync(), awaitedServerVersions = db.syncIssueActionDao(),
    )
    private val attachments = AttachmentRepositoryImpl(db.attachmentDao(), db.dailyEntryDao(), AttachmentApi(client), fileStore, FakeSyncer(), scope)

    @AfterTest
    fun close() {
        client.close()
        server.stop(0)
        db.close()
        dir.toFile().deleteRecursively()
    }

    private fun liveHeap(): Long {
        System.gc()
        return Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
    }

    private fun <T> growthOfTheLiveHeapDuring(work: () -> T): Pair<Long, T> {
        val before = liveHeap()
        val peak = AtomicLong(before)
        val working = AtomicBoolean(true)
        val sampler = thread {
            while (working.get()) {
                peak.accumulateAndGet(liveHeap(), ::maxOf)
                Thread.sleep(20)
            }
        }
        val result = try {
            work()
        } finally {
            working.set(false)
            sampler.join()
        }
        return (peak.get() - before) to result
    }

    @Test
    fun a_video_of_100_megabytes_is_copied_then_sent_without_ever_being_held_in_memory() {
        val picked = dir.resolve("filmee.mp4")
        Files.newOutputStream(picked).use { out -> ByteArray(megabyte.toInt()) { 5 }.let { chunk -> repeat(100) { out.write(chunk) } } }
        runBlocking {
            val synced = SyncStatus.SYNCED
            val none = PendingOp.NONE
            db.projectDao().upsert(localProject("p5", name = "Villa", serverId = 5, pendingOp = none, syncStatus = synced))
            db.stageDao().upsert(localStage("st90", projectLocalId = "p5", name = "Gros œuvre", serverId = 90, pendingOp = none, syncStatus = synced))
            db.dailyLogDao().upsert(localDailyLog("l800", stageLocalId = "st90", serverId = 800))
            db.dailyEntryDao().upsert(localDailyEntry("e900", dailyLogLocalId = "l800", type = "PURCHASE", summary = "Livraison", serverId = 900, pendingOp = none, syncStatus = synced))
        }

        val (copyGrowth, added) = growthOfTheLiveHeapDuring {
            runBlocking { runCatching { attachments.addVideo("e900", PlatformFile(picked.toString()).asUploadFile("video/mp4")) }.getOrNull() }
        }
        val (sendGrowth, _) = growthOfTheLiveHeapDuring {
            runBlocking {
                withTimeoutOrNull(120_000) {
                    engine.syncNow()
                    engine.awaitVideosBeingSent()
                }
            }
        }

        val row = runBlocking { added?.let { db.attachmentDao().findByLocalId(it.localId) } }
        println("D tranche 3 — mémoire pour une vidéo de ${videoBytes / megabyte} Mo : copie +${copyGrowth / megabyte} Mo, envoi +${sendGrowth / megabyte} Mo, reçu par le serveur ${received.get() / megabyte} Mo, ligne=${row?.syncStatus}")
        assertTrue(received.get() >= videoBytes, "the server received the whole video: ${received.get()} bytes")
        assertEquals(SyncStatus.SYNCED, row?.syncStatus)
        assertTrue(copyGrowth < allowedGrowth, "copying: the live heap grew by ${copyGrowth / megabyte} MB")
        assertTrue(sendGrowth < allowedGrowth, "sending: the live heap grew by ${sendGrowth / megabyte} MB")
    }
}
