package com.dmb.chantiertracker.presentation.logs

import com.dmb.chantiertracker.domain.model.UploadFile
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.source
import kotlinx.io.RawSource

fun PlatformFile.asUploadFile(mimeType: String): UploadFile = object : UploadFile {
    override val name: String = this@asUploadFile.name
    override val mimeType: String = mimeType
    override fun size(): Long = this@asUploadFile.size()
    override fun openSource(): RawSource = this@asUploadFile.source()
}
