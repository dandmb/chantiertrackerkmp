package com.dmb.chantiertracker.data.local

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class DesktopAppPreferences(
    private val file: Path = DesktopTokenStorage.defaultConfigDir().resolve("preferences.properties"),
) : AppPreferences {

    override fun read(key: String): String? {
        if (!file.exists()) return null
        return runCatching {
            Properties().apply { file.inputStream().use { load(it) } }.getProperty(key)
        }.getOrNull()
    }

    override fun write(key: String, value: String?) {
        runCatching {
            Files.createDirectories(file.parent)
            val props = Properties().apply { if (file.exists()) file.inputStream().use { load(it) } }
            if (value == null) props.remove(key) else props.setProperty(key, value)
            file.outputStream().use { props.store(it, null) }
        }
    }
}
