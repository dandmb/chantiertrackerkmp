package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.domain.model.UploadFile
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.write

/**
 * An [UploadFile] backed by an in-memory byte array — [openSource] hands back a
 * fresh [Buffer] each call, so it survives Ktor re-sending the body.
 */
fun fakeUploadFile(
    bytes: ByteArray,
    name: String = "clip.mp4",
    mimeType: String = "video/mp4",
): UploadFile = object : UploadFile {
    override val name: String = name
    override val mimeType: String = mimeType
    override fun size(): Long = bytes.size.toLong()
    override fun openSource(): RawSource = Buffer().apply { write(bytes) }
}
