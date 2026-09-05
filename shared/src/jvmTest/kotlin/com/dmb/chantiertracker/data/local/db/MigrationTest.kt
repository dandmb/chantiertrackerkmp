package com.dmb.chantiertracker.data.local.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.dmb.chantiertracker.support.verifyPlanUsageDaoContract
import com.dmb.chantiertracker.support.verifyStageDaoContract
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MigrationTest {

    private val dir = Files.createTempDirectory("chantier-migration")
    private val dbPath = dir.resolve("migration-test.db").absolutePathString()

    @AfterTest fun cleanUp() { dir.toFile().deleteRecursively() }

    /** Seeds a v1 database by hand, then opens [AppDatabase] (v3) and lets MIGRATION_1_2 + MIGRATION_2_3 run. */
    @Test
    fun migrating_from_v1_adds_stages_and_plan_usage_and_keeps_existing_projects() = runTest {
        val driver = BundledSQLiteDriver()
        driver.open(dbPath).use { c ->
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `projects` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`name` TEXT NOT NULL, `description` TEXT, `location` TEXT, `currency` TEXT NOT NULL, " +
                    "`timezone` TEXT NOT NULL, `status` TEXT NOT NULL, `ownerId` INTEGER, `createdAt` TEXT, " +
                    "`syncStatus` TEXT NOT NULL, `pendingOp` TEXT NOT NULL, `locallyModifiedAt` INTEGER NOT NULL, " +
                    "`lastSyncedAt` INTEGER, `remoteUpdatedAt` INTEGER, `lastSyncError` TEXT, PRIMARY KEY(`localId`))",
            )
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `project_members` (`projectLocalId` TEXT NOT NULL, `userId` INTEGER NOT NULL, " +
                    "`name` TEXT NOT NULL, `email` TEXT NOT NULL, `role` TEXT NOT NULL, PRIMARY KEY(`projectLocalId`, `userId`))",
            )
            c.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            c.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '4a52441a1762a68e9f48445ec0913c14')",
            )
            c.execSQL("PRAGMA user_version = 1")
            c.execSQL(
                "INSERT INTO projects (localId, serverId, name, description, location, currency, timezone, status, " +
                    "ownerId, createdAt, syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, lastSyncError) " +
                    "VALUES ('p-v1', 42, 'Chantier v1', NULL, NULL, 'EUR', 'Europe/Paris', 'IN_PROGRESS', 1, NULL, " +
                    "'SYNCED', 'NONE', 1000, NULL, NULL, NULL)",
            )
        }

        val db = Room.databaseBuilder<AppDatabase>(name = dbPath).buildChantierDatabase()
        try {
            assertEquals("Chantier v1", db.projectDao().findByLocalId("p-v1")?.name, "v1 data survives the migration")
            assertEquals(emptyList(), db.stageDao().findForProject("p-v1"), "the new stages table is usable and empty")
            db.planUsageDao().upsert(PlanUsageEntity(0, "FREE", 1, 1L))
            assertEquals("FREE", db.planUsageDao().observe().first()?.plan, "the new plan_usage table is usable")
        } finally {
            db.close()
        }

        // The migrated schema behaves exactly like a freshly built v3 one.
        val fresh = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
        try {
            verifyStageDaoContract(fresh)
            verifyPlanUsageDaoContract(fresh)
        } finally {
            fresh.close()
        }
    }

    /** Seeds a v2 database by hand, then opens [AppDatabase] (v3) and lets MIGRATION_2_3 run. */
    @Test
    fun migrating_from_v2_adds_plan_usage_and_keeps_projects_and_stages() = runTest {
        val v2Path = dir.resolve("migration-v2.db").absolutePathString()
        BundledSQLiteDriver().open(v2Path).use { c ->
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `projects` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`name` TEXT NOT NULL, `description` TEXT, `location` TEXT, `currency` TEXT NOT NULL, " +
                    "`timezone` TEXT NOT NULL, `status` TEXT NOT NULL, `ownerId` INTEGER, `createdAt` TEXT, " +
                    "`syncStatus` TEXT NOT NULL, `pendingOp` TEXT NOT NULL, `locallyModifiedAt` INTEGER NOT NULL, " +
                    "`lastSyncedAt` INTEGER, `remoteUpdatedAt` INTEGER, `lastSyncError` TEXT, PRIMARY KEY(`localId`))",
            )
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `project_members` (`projectLocalId` TEXT NOT NULL, `userId` INTEGER NOT NULL, " +
                    "`name` TEXT NOT NULL, `email` TEXT NOT NULL, `role` TEXT NOT NULL, PRIMARY KEY(`projectLocalId`, `userId`))",
            )
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `stages` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`projectLocalId` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `estimatedBudget` REAL, " +
                    "`startDate` TEXT, `endDate` TEXT, `status` TEXT NOT NULL, `syncStatus` TEXT NOT NULL, " +
                    "`pendingOp` TEXT NOT NULL, `locallyModifiedAt` INTEGER NOT NULL, `lastSyncedAt` INTEGER, " +
                    "`remoteUpdatedAt` INTEGER, `lastSyncError` TEXT, PRIMARY KEY(`localId`), " +
                    "FOREIGN KEY(`projectLocalId`) REFERENCES `projects`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_stages_projectLocalId` ON `stages` (`projectLocalId`)")
            c.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            c.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'b4b5a0a4c20894c8ce00eed950154c98')",
            )
            c.execSQL("PRAGMA user_version = 2")
            c.execSQL(
                "INSERT INTO projects (localId, serverId, name, description, location, currency, timezone, status, " +
                    "ownerId, createdAt, syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, lastSyncError) " +
                    "VALUES ('p-v2', 7, 'Chantier v2', NULL, NULL, 'EUR', 'Europe/Paris', 'IN_PROGRESS', 1, NULL, " +
                    "'SYNCED', 'NONE', 1000, NULL, NULL, NULL)",
            )
        }

        val db = Room.databaseBuilder<AppDatabase>(name = v2Path).buildChantierDatabase()
        try {
            assertEquals("Chantier v2", db.projectDao().findByLocalId("p-v2")?.name, "v2 data survives")
            assertEquals(emptyList(), db.stageDao().findForProject("p-v2"))
            db.planUsageDao().upsert(PlanUsageEntity(0, "SEMI_FLEX", 3, 1L))
            assertEquals(3, db.planUsageDao().observe().first()?.projectsLimit)
        } finally {
            db.close()
        }
    }
}
