package com.dmb.chantiertracker.data.local.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@Database(
    entities = [ProjectEntity::class, ProjectMemberEntity::class, StageEntity::class, PlanUsageEntity::class],
    version = 3,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun stageDao(): StageDao
    abstract fun planUsageDao(): PlanUsageDao
}

@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

fun RoomDatabase.Builder<AppDatabase>.buildChantierDatabase(): AppDatabase =
    setDriver(BundledSQLiteDriver())
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()
