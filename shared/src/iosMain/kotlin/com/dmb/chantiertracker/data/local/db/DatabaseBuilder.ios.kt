@file:OptIn(ExperimentalForeignApi::class)

package com.dmb.chantiertracker.data.local.db

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

fun projectDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val fileManager = NSFileManager.defaultManager
    val supportDir: NSURL = fileManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    ) ?: error("Application Support directory unavailable")
    val dbPath = requireNotNull(supportDir.URLByAppendingPathComponent(DB_FILE_NAME)?.path) {
        "Could not resolve database path"
    }
    return Room.databaseBuilder<AppDatabase>(name = dbPath)
}
