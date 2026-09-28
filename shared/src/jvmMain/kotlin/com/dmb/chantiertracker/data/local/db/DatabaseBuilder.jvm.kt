package com.dmb.chantiertracker.data.local.db

import androidx.room.Room
import androidx.room.RoomDatabase
import com.dmb.chantiertracker.data.local.DesktopTokenStorage
import java.nio.file.Files
import kotlin.io.path.absolutePathString

fun projectDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dir = DesktopTokenStorage.defaultConfigDir()
    Files.createDirectories(dir)
    val dbFile = dir.resolve(DB_FILE_NAME)
    return Room.databaseBuilder<AppDatabase>(name = dbFile.absolutePathString())
}
