package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.AttachmentDto
import com.dmb.chantiertracker.data.remote.dto.PageDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

class AttachmentApi(private val client: HttpClient) {

    suspend fun list(entryId: Long): PageDto<AttachmentDto> =
        client.get(ApiRoutes.entryAttachments(entryId)).body()

    // The backend compresses/transcodes server-side (photo: resize ≤ 1600px +
    // JPEG re-encode, HEIC decode via ffmpeg; video: H.264 720p MP4 — the
    // backend routes on the Content-Type prefix). The client just hands over
    // the file bytes it picked, as-is. `onProgress(sent, total)` reports upload
    // progress — meaningful for a video (raw, potentially tens of MB).
    suspend fun upload(
        entryId: Long,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        onProgress: (sent: Long, total: Long?) -> Unit = { _, _ -> },
    ): AttachmentDto =
        client.post(ApiRoutes.entryAttachments(entryId)) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "file",
                            value = bytes,
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, mimeType)
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            },
                        )
                    },
                ),
            )
            onUpload { sent, total -> onProgress(sent, total) }
        }.body()

    suspend fun download(id: Long): ByteArray =
        client.get(ApiRoutes.attachment(id)).body()

    suspend fun delete(id: Long) {
        client.delete(ApiRoutes.attachment(id))
    }
}
