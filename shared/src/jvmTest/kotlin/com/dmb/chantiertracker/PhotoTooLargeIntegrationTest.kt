package com.dmb.chantiertracker

import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.SyncError
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.CreateStageInput
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.presentation.sync.reasonSentenceRes
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.sync_reason_image_too_large
import com.dmb.chantiertracker.support.DeviceStack
import com.dmb.chantiertracker.support.DisposableAccounts
import com.dmb.chantiertracker.support.localAttachment
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

/** R2 — a photo above 40 megapixels against the real backend (IMAGE_TOO_LARGE, 400). */
class PhotoTooLargeIntegrationTest {

    private val accounts = DisposableAccounts()
    private val devices = mutableListOf<DeviceStack>()

    @AfterTest
    fun cleanUp() {
        devices.forEach { it.close() }
        accounts.close()
    }

    private fun aGreyJpeg(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
        return ByteArrayOutputStream().use { out -> ImageIO.write(image, "jpg", out); out.toByteArray() }
    }

    @Test
    fun r2_a_photo_above_forty_megapixels_is_refused_when_taken_and_by_the_server_as_a_file_refusal() = runBlocking {
        com.dmb.chantiertracker.support.skipUnlessRealBackendScenariosAreEnabled()
        val phone = DeviceStack().also { devices += it }
        phone.auth.signInForTheFirstTime(accounts.create("qa-r2-photo", "QA R2 Photo"), "QaPassword1234!")
        val projectId = phone.projects.createProject(CreateProjectInput("QA R2 photo", null, "Nîmes", "EUR", "Europe/Paris"))
        val stageId = phone.stages.createStage(CreateStageInput(projectId, "Gros œuvre", null, null, null, null))
        val entry = phone.logs.createPurchaseEntry(stageId, LocalDate.now(ZoneId.of("Europe/Paris")).toString()).entryLocalId
        phone.waitForTheRunningSyncPass()
        phone.sync.syncNow()

        val tooLarge = aGreyJpeg(8000, 5001)
        val refusedWhenTaken = runCatching { phone.attachments.addAttachment(entry, tooLarge, "panorama.jpg", "image/jpeg") }.exceptionOrNull()
        assertEquals<Any?>(DomainException.ImageTooLarge, refusedWhenTaken, "refused when it is taken, before any copy")

        val path = phone.fileStore.save(tooLarge, "panorama.jpg")
        phone.db.attachmentDao().upsert(localAttachment("a-panorama", entryLocalId = entry, localPath = path, originalName = "panorama.jpg", sizeBytes = tooLarge.size.toLong()))
        phone.sync.syncNow()

        val row = phone.db.attachmentDao().findByLocalId("a-panorama")!!
        println("R2 — photo de 40,008 Mpx envoyée : ${row.syncStatus} ${row.lastSyncError} ${row.serverErrorCode}")
        assertEquals(listOf<Any?>(SyncStatus.CONFLICTED, SyncError.FILE_REFUSED, "IMAGE_TOO_LARGE"), listOf(row.syncStatus, row.lastSyncError, row.serverErrorCode))
        val listed = phone.syncIssues.observeIssues().first().single { it.localId == "a-panorama" }
        assertEquals(RefusalReason.FILE_REFUSED, listed.issue.reason)
        assertEquals(Res.string.sync_reason_image_too_large, listed.issue.reasonSentenceRes())
    }
}
