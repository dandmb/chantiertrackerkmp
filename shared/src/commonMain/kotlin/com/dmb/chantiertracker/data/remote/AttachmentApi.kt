package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.AttachmentDto
import com.dmb.chantiertracker.data.remote.dto.PageDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.io.Source

class AttachmentApi(private val client: HttpClient) {

    suspend fun list(entryId: Long): PageDto<AttachmentDto> =
        client.get(ApiRoutes.entryAttachments(entryId)).body()

    // The backend compresses/transcodes server-side (photo: resize ≤ 1600px +
    // JPEG re-encode, HEIC decode via ffmpeg; video: H.264 720p MP4 — routed on
    // the Content-Type prefix). The client streams the picked file straight
    // through (`openSource`, called once per send attempt) so a big raw video
    // never sits in memory as one ByteArray (ADR-38). `onProgress(sent, total)`
    // tracks the bytes leaving the device.
    //
    // Timeouts are lifted well past the client default: a ~2-min clip's upload
    // plus the **synchronous server transcode** (ffmpeg, up to 10 min) all
    // happen inside this one request before the response comes back.
    suspend fun upload(
        entryId: Long,
        contentLength: Long,
        fileName: String,
        mimeType: String,
        openSource: () -> Source,
        onProgress: (sent: Long, total: Long?) -> Unit = { _, _ -> },
    ): AttachmentDto =
        client.post(ApiRoutes.entryAttachments(entryId)) {
            timeout {
                requestTimeoutMillis = 15 * 60_000L
                socketTimeoutMillis = 5 * 60_000L
            }
            setBody(
                MultiPartFormDataContent(
                    formData {
                        appendInput(
                            key = "file",
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, mimeType)
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            },
                            size = contentLength,
                            block = { openSource() },
                        )
                    },
                ),
            )
            onUpload { sent, total -> onProgress(sent, total) }
        }.body()

    suspend fun download(id: Long): ByteArray =
        client.get(ApiRoutes.attachment(id)) {
            timeout {
                requestTimeoutMillis = 5 * 60_000L
                socketTimeoutMillis = 60_000L
            }
        }.body()

    suspend fun delete(id: Long) {
        client.delete(ApiRoutes.attachment(id))
    }
}
