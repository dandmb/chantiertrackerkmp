package com.dmb.chantiertracker.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun projectDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(DB_FILE_NAME)
    return Room.databaseBuilder<AppDatabase>(
        context = appContext,
        name = dbFile.absolutePath,
    )
}
