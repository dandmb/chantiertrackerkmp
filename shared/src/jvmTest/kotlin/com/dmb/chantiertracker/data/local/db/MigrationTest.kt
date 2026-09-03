package com.dmb.chantiertracker.data.local.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.dmb.chantiertracker.support.verifyStageDaoContract
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

    /** Seeds a v1 database by hand, then opens [AppDatabase] (v2) and lets [MIGRATION_1_2] run. */
    @Test
    fun migrating_from_v1_adds_stages_and_keeps_existing_projects() = runTest {
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
        } finally {
            db.close()
        }

        // The migrated schema behaves exactly like a freshly built v2 one.
        val fresh = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
        try {
            verifyStageDaoContract(fresh)
        } finally {
            fresh.close()
        }
    }
}
