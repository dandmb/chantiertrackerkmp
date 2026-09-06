package com.dmb.chantiertracker.data.local.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.dmb.chantiertracker.support.verifyAttachmentDaoContract
import com.dmb.chantiertracker.support.verifyDailyLogDaoContract
import com.dmb.chantiertracker.support.verifyInvitationDaoContract
import com.dmb.chantiertracker.support.verifyMaterialAndLineDaoContract
import com.dmb.chantiertracker.support.verifyPlanUsageDaoContract
import com.dmb.chantiertracker.support.verifyProjectDaoContract
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

    /** Seeds a v3 database by hand, then opens [AppDatabase] (v4) and lets MIGRATION_3_4 run. */
    @Test
    fun migrating_from_v3_adds_daily_logs_and_daily_entries_and_keeps_existing_data() = runTest {
        val v3Path = dir.resolve("migration-v3.db").absolutePathString()
        BundledSQLiteDriver().open(v3Path).use { c ->
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
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `plan_usage` (`id` INTEGER NOT NULL, `plan` TEXT NOT NULL, " +
                    "`projectsLimit` INTEGER, `refreshedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            c.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            c.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '8a3379cc91095148c613c22541622ee9')",
            )
            c.execSQL("PRAGMA user_version = 3")
            c.execSQL(
                "INSERT INTO projects (localId, serverId, name, description, location, currency, timezone, status, " +
                    "ownerId, createdAt, syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, lastSyncError) " +
                    "VALUES ('p-v3', 9, 'Chantier v3', NULL, NULL, 'EUR', 'Europe/Paris', 'IN_PROGRESS', 1, NULL, " +
                    "'SYNCED', 'NONE', 1000, NULL, NULL, NULL)",
            )
            c.execSQL(
                "INSERT INTO stages (localId, serverId, projectLocalId, name, description, estimatedBudget, startDate, " +
                    "endDate, status, syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, lastSyncError) " +
                    "VALUES ('s-v3', 5, 'p-v3', 'Gros œuvre', NULL, NULL, NULL, NULL, 'IN_PROGRESS', " +
                    "'SYNCED', 'NONE', 1000, NULL, NULL, NULL)",
            )
        }

        val db = Room.databaseBuilder<AppDatabase>(name = v3Path).buildChantierDatabase()
        try {
            assertEquals("Chantier v3", db.projectDao().findByLocalId("p-v3")?.name, "v3 projects survive")
            assertEquals("Gros œuvre", db.stageDao().findByLocalId("s-v3")?.name, "v3 stages survive")
            assertEquals(emptyList(), db.dailyLogDao().observeLogsForStage("s-v3").first(), "the new daily_logs table is usable and empty")
        } finally {
            db.close()
        }

        // The migrated schema behaves exactly like a freshly built v4 one.
        val fresh = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
        try {
            verifyDailyLogDaoContract(fresh)
        } finally {
            fresh.close()
        }
    }

    /** Seeds a v4 database by hand, then opens [AppDatabase] (v5) and lets MIGRATION_4_5 run. */
    @Test
    fun migrating_from_v4_adds_materials_and_lines_and_keeps_existing_data() = runTest {
        val v4Path = dir.resolve("migration-v4.db").absolutePathString()
        BundledSQLiteDriver().open(v4Path).use { c ->
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
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `plan_usage` (`id` INTEGER NOT NULL, `plan` TEXT NOT NULL, " +
                    "`projectsLimit` INTEGER, `refreshedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_logs` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`stageLocalId` TEXT NOT NULL, `date` TEXT NOT NULL, `locallyCreatedAt` INTEGER NOT NULL, " +
                    "`lastSyncedAt` INTEGER, PRIMARY KEY(`localId`), " +
                    "FOREIGN KEY(`stageLocalId`) REFERENCES `stages`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_logs_stageLocalId` ON `daily_logs` (`stageLocalId`)")
            c.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_logs_stageLocalId_date` ON `daily_logs` (`stageLocalId`, `date`)",
            )
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_entries` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`dailyLogLocalId` TEXT NOT NULL, `type` TEXT NOT NULL, `summary` TEXT, `createdById` INTEGER, " +
                    "`createdAt` TEXT, `modifiedById` INTEGER, `modifiedAt` TEXT, `syncStatus` TEXT NOT NULL, " +
                    "`pendingOp` TEXT NOT NULL, `locallyModifiedAt` INTEGER NOT NULL, `lastSyncedAt` INTEGER, " +
                    "`remoteUpdatedAt` INTEGER, `lastSyncError` TEXT, PRIMARY KEY(`localId`), " +
                    "FOREIGN KEY(`dailyLogLocalId`) REFERENCES `daily_logs`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_entries_dailyLogLocalId` ON `daily_entries` (`dailyLogLocalId`)")
            c.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_entries_dailyLogLocalId_type` ON `daily_entries` (`dailyLogLocalId`, `type`)",
            )
            c.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            c.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '3dbcf5ee0ef8fc8cf90271225eb348a2')",
            )
            c.execSQL("PRAGMA user_version = 4")
            c.execSQL(
                "INSERT INTO projects (localId, serverId, name, description, location, currency, timezone, status, " +
                    "ownerId, createdAt, syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, lastSyncError) " +
                    "VALUES ('p-v4', 11, 'Chantier v4', NULL, NULL, 'EUR', 'Europe/Paris', 'IN_PROGRESS', 1, NULL, " +
                    "'SYNCED', 'NONE', 1000, NULL, NULL, NULL)",
            )
            c.execSQL(
                "INSERT INTO stages (localId, serverId, projectLocalId, name, description, estimatedBudget, startDate, " +
                    "endDate, status, syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, lastSyncError) " +
                    "VALUES ('s-v4', 6, 'p-v4', 'Gros œuvre', NULL, NULL, NULL, NULL, 'IN_PROGRESS', " +
                    "'SYNCED', 'NONE', 1000, NULL, NULL, NULL)",
            )
        }

        val db = Room.databaseBuilder<AppDatabase>(name = v4Path).buildChantierDatabase()
        try {
            assertEquals("Chantier v4", db.projectDao().findByLocalId("p-v4")?.name, "v4 projects survive")
            assertEquals("Gros œuvre", db.stageDao().findByLocalId("s-v4")?.name, "v4 stages survive")
            assertEquals(emptyList(), db.materialDao().observeMaterialsForProject("p-v4").first(), "the new materials table is usable and empty")
        } finally {
            db.close()
        }

        // The migrated schema behaves exactly like a freshly built v5 one.
        val fresh = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
        try {
            verifyMaterialAndLineDaoContract(fresh)
        } finally {
            fresh.close()
        }
    }

    /** Seeds a v5 database by hand, then opens [AppDatabase] (v6) and lets MIGRATION_5_6 run. */
    @Test
    fun migrating_from_v5_adds_attachments_and_keeps_existing_data() = runTest {
        val v5Path = dir.resolve("migration-v5.db").absolutePathString()
        BundledSQLiteDriver().open(v5Path).use { c ->
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
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `plan_usage` (`id` INTEGER NOT NULL, `plan` TEXT NOT NULL, " +
                    "`projectsLimit` INTEGER, `refreshedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_logs` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`stageLocalId` TEXT NOT NULL, `date` TEXT NOT NULL, `locallyCreatedAt` INTEGER NOT NULL, " +
                    "`lastSyncedAt` INTEGER, PRIMARY KEY(`localId`), " +
                    "FOREIGN KEY(`stageLocalId`) REFERENCES `stages`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_logs_stageLocalId` ON `daily_logs` (`stageLocalId`)")
            c.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_logs_stageLocalId_date` ON `daily_logs` (`stageLocalId`, `date`)",
            )
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_entries` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`dailyLogLocalId` TEXT NOT NULL, `type` TEXT NOT NULL, `summary` TEXT, `createdById` INTEGER, " +
                    "`createdAt` TEXT, `modifiedById` INTEGER, `modifiedAt` TEXT, `syncStatus` TEXT NOT NULL, " +
                    "`pendingOp` TEXT NOT NULL, `locallyModifiedAt` INTEGER NOT NULL, `lastSyncedAt` INTEGER, " +
                    "`remoteUpdatedAt` INTEGER, `lastSyncError` TEXT, PRIMARY KEY(`localId`), " +
                    "FOREIGN KEY(`dailyLogLocalId`) REFERENCES `daily_logs`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_entries_dailyLogLocalId` ON `daily_entries` (`dailyLogLocalId`)")
            c.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_entries_dailyLogLocalId_type` ON `daily_entries` (`dailyLogLocalId`, `type`)",
            )
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `materials` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`projectLocalId` TEXT NOT NULL, `name` TEXT NOT NULL, `unit` TEXT NOT NULL, `syncStatus` TEXT NOT NULL, " +
                    "`pendingOp` TEXT NOT NULL, `locallyModifiedAt` INTEGER NOT NULL, `lastSyncedAt` INTEGER, " +
                    "`remoteUpdatedAt` INTEGER, `lastSyncError` TEXT, PRIMARY KEY(`localId`), " +
                    "FOREIGN KEY(`projectLocalId`) REFERENCES `projects`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_materials_projectLocalId` ON `materials` (`projectLocalId`)")
            c.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_materials_projectLocalId_name` ON `materials` (`projectLocalId`, `name`)",
            )
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `purchase_lines` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`entryLocalId` TEXT NOT NULL, `materialLocalId` TEXT NOT NULL, `quantity` REAL NOT NULL, " +
                    "`unitPrice` REAL NOT NULL, `totalPrice` REAL NOT NULL, `supplier` TEXT, `createdAt` TEXT, " +
                    "`syncStatus` TEXT NOT NULL, `pendingOp` TEXT NOT NULL, `locallyModifiedAt` INTEGER NOT NULL, " +
                    "`lastSyncedAt` INTEGER, `remoteUpdatedAt` INTEGER, `lastSyncError` TEXT, PRIMARY KEY(`localId`), " +
                    "FOREIGN KEY(`entryLocalId`) REFERENCES `daily_entries`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`materialLocalId`) REFERENCES `materials`(`localId`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            )
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_lines_entryLocalId` ON `purchase_lines` (`entryLocalId`)")
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_lines_materialLocalId` ON `purchase_lines` (`materialLocalId`)")
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `consumption_lines` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`entryLocalId` TEXT NOT NULL, `materialLocalId` TEXT NOT NULL, `quantity` REAL NOT NULL, " +
                    "`createdAt` TEXT, `syncStatus` TEXT NOT NULL, `pendingOp` TEXT NOT NULL, `locallyModifiedAt` INTEGER NOT NULL, " +
                    "`lastSyncedAt` INTEGER, `remoteUpdatedAt` INTEGER, `lastSyncError` TEXT, PRIMARY KEY(`localId`), " +
                    "FOREIGN KEY(`entryLocalId`) REFERENCES `daily_entries`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`materialLocalId`) REFERENCES `materials`(`localId`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            )
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_consumption_lines_entryLocalId` ON `consumption_lines` (`entryLocalId`)")
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_consumption_lines_materialLocalId` ON `consumption_lines` (`materialLocalId`)")
            c.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            c.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '5ccb3180bf15e63d13712b57b965c9c8')",
            )
            c.execSQL("PRAGMA user_version = 5")
            c.execSQL(
                "INSERT INTO projects (localId, serverId, name, description, location, currency, timezone, status, " +
                    "ownerId, createdAt, syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, lastSyncError) " +
                    "VALUES ('p-v5', 11, 'Chantier v5', NULL, NULL, 'EUR', 'Europe/Paris', 'IN_PROGRESS', 1, NULL, " +
                    "'SYNCED', 'NONE', 1000, NULL, NULL, NULL)",
            )
            c.execSQL(
                "INSERT INTO materials (localId, serverId, projectLocalId, name, unit, syncStatus, pendingOp, " +
                    "locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, lastSyncError) " +
                    "VALUES ('m-v5', 7, 'p-v5', 'Ciment', 'sac', 'SYNCED', 'NONE', 1000, NULL, NULL, NULL)",
            )
        }

        val db = Room.databaseBuilder<AppDatabase>(name = v5Path).buildChantierDatabase()
        try {
            assertEquals("Chantier v5", db.projectDao().findByLocalId("p-v5")?.name, "v5 projects survive")
            assertEquals("Ciment", db.materialDao().findByLocalId("m-v5")?.name, "v5 materials survive")
            assertEquals(emptyList(), db.attachmentDao().observeForEntry("entry-v5").first(), "the new attachments table is usable and empty")
        } finally {
            db.close()
        }

        // The migrated schema behaves exactly like a freshly built v6 one.
        val fresh = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
        try {
            verifyAttachmentDaoContract(fresh)
        } finally {
            fresh.close()
        }
    }

    /** Seeds a v6 database by hand, then opens [AppDatabase] (v7) and lets MIGRATION_6_7 run. */
    @Test
    fun migrating_from_v6_adds_invitations_and_keeps_existing_data() = runTest {
        val v6Path = dir.resolve("migration-v6.db").absolutePathString()
        BundledSQLiteDriver().open(v6Path).use { c ->
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
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `plan_usage` (`id` INTEGER NOT NULL, `plan` TEXT NOT NULL, " +
                    "`projectsLimit` INTEGER, `refreshedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_logs` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`stageLocalId` TEXT NOT NULL, `date` TEXT NOT NULL, `locallyCreatedAt` INTEGER NOT NULL, " +
                    "`lastSyncedAt` INTEGER, PRIMARY KEY(`localId`), " +
                    "FOREIGN KEY(`stageLocalId`) REFERENCES `stages`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_logs_stageLocalId` ON `daily_logs` (`stageLocalId`)")
            c.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_logs_stageLocalId_date` ON `daily_logs` (`stageLocalId`, `date`)",
            )
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_entries` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`dailyLogLocalId` TEXT NOT NULL, `type` TEXT NOT NULL, `summary` TEXT, `createdById` INTEGER, " +
                    "`createdAt` TEXT, `modifiedById` INTEGER, `modifiedAt` TEXT, `syncStatus` TEXT NOT NULL, " +
                    "`pendingOp` TEXT NOT NULL, `locallyModifiedAt` INTEGER NOT NULL, `lastSyncedAt` INTEGER, " +
                    "`remoteUpdatedAt` INTEGER, `lastSyncError` TEXT, PRIMARY KEY(`localId`), " +
                    "FOREIGN KEY(`dailyLogLocalId`) REFERENCES `daily_logs`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_entries_dailyLogLocalId` ON `daily_entries` (`dailyLogLocalId`)")
            c.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_entries_dailyLogLocalId_type` ON `daily_entries` (`dailyLogLocalId`, `type`)",
            )
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `materials` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`projectLocalId` TEXT NOT NULL, `name` TEXT NOT NULL, `unit` TEXT NOT NULL, `syncStatus` TEXT NOT NULL, " +
                    "`pendingOp` TEXT NOT NULL, `locallyModifiedAt` INTEGER NOT NULL, `lastSyncedAt` INTEGER, " +
                    "`remoteUpdatedAt` INTEGER, `lastSyncError` TEXT, PRIMARY KEY(`localId`), " +
                    "FOREIGN KEY(`projectLocalId`) REFERENCES `projects`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_materials_projectLocalId` ON `materials` (`projectLocalId`)")
            c.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_materials_projectLocalId_name` ON `materials` (`projectLocalId`, `name`)",
            )
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `purchase_lines` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`entryLocalId` TEXT NOT NULL, `materialLocalId` TEXT NOT NULL, `quantity` REAL NOT NULL, " +
                    "`unitPrice` REAL NOT NULL, `totalPrice` REAL NOT NULL, `supplier` TEXT, `createdAt` TEXT, " +
                    "`syncStatus` TEXT NOT NULL, `pendingOp` TEXT NOT NULL, `locallyModifiedAt` INTEGER NOT NULL, " +
                    "`lastSyncedAt` INTEGER, `remoteUpdatedAt` INTEGER, `lastSyncError` TEXT, PRIMARY KEY(`localId`), " +
                    "FOREIGN KEY(`entryLocalId`) REFERENCES `daily_entries`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`materialLocalId`) REFERENCES `materials`(`localId`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            )
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_lines_entryLocalId` ON `purchase_lines` (`entryLocalId`)")
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_lines_materialLocalId` ON `purchase_lines` (`materialLocalId`)")
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `consumption_lines` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`entryLocalId` TEXT NOT NULL, `materialLocalId` TEXT NOT NULL, `quantity` REAL NOT NULL, " +
                    "`createdAt` TEXT, `syncStatus` TEXT NOT NULL, `pendingOp` TEXT NOT NULL, `locallyModifiedAt` INTEGER NOT NULL, " +
                    "`lastSyncedAt` INTEGER, `remoteUpdatedAt` INTEGER, `lastSyncError` TEXT, PRIMARY KEY(`localId`), " +
                    "FOREIGN KEY(`entryLocalId`) REFERENCES `daily_entries`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`materialLocalId`) REFERENCES `materials`(`localId`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            )
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_consumption_lines_entryLocalId` ON `consumption_lines` (`entryLocalId`)")
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_consumption_lines_materialLocalId` ON `consumption_lines` (`materialLocalId`)")
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `attachments` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`entryLocalId` TEXT NOT NULL, `localPath` TEXT NOT NULL, `originalName` TEXT NOT NULL, " +
                    "`mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `uploadedAt` INTEGER NOT NULL, " +
                    "`syncStatus` TEXT NOT NULL, `pendingOp` TEXT NOT NULL, `locallyModifiedAt` INTEGER NOT NULL, " +
                    "`lastSyncedAt` INTEGER, `remoteUpdatedAt` INTEGER, `lastSyncError` TEXT, PRIMARY KEY(`localId`), " +
                    "FOREIGN KEY(`entryLocalId`) REFERENCES `daily_entries`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_entryLocalId` ON `attachments` (`entryLocalId`)")
            c.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            c.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'f618e8181c82c36d1057432aac9e5ae5')",
            )
            c.execSQL("PRAGMA user_version = 6")
            c.execSQL(
                "INSERT INTO projects (localId, serverId, name, description, location, currency, timezone, status, " +
                    "ownerId, createdAt, syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, lastSyncError) " +
                    "VALUES ('p-v6', 13, 'Chantier v6', NULL, NULL, 'EUR', 'Europe/Paris', 'IN_PROGRESS', 1, NULL, " +
                    "'SYNCED', 'NONE', 1000, NULL, NULL, NULL)",
            )
        }

        val db = Room.databaseBuilder<AppDatabase>(name = v6Path).buildChantierDatabase()
        try {
            assertEquals("Chantier v6", db.projectDao().findByLocalId("p-v6")?.name, "v6 projects survive")
            assertEquals(emptyList(), db.invitationDao().findForProject("p-v6"), "the new invitations table is usable and empty")
        } finally {
            db.close()
        }

        // The migrated schema behaves exactly like a freshly built v7 one.
        val fresh = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
        try {
            verifyInvitationDaoContract(fresh)
        } finally {
            fresh.close()
        }
    }

    /**
     * Seeds a v7 database by hand, then opens [AppDatabase] (v8) and lets
     * MIGRATION_7_8 add the `projects.ownerPlan` column (ADR-33).
     */
    @Test
    fun migrating_from_v7_adds_owner_plan_column_and_keeps_existing_data() = runTest {
        val v7Path = dir.resolve("migration-v7.db").absolutePathString()
        BundledSQLiteDriver().open(v7Path).use { c ->
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
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `plan_usage` (`id` INTEGER NOT NULL, `plan` TEXT NOT NULL, " +
                    "`projectsLimit` INTEGER, `refreshedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_logs` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`stageLocalId` TEXT NOT NULL, `date` TEXT NOT NULL, `locallyCreatedAt` INTEGER NOT NULL, " +
                    "`lastSyncedAt` INTEGER, PRIMARY KEY(`localId`), " +
                    "FOREIGN KEY(`stageLocalId`) REFERENCES `stages`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_logs_stageLocalId` ON `daily_logs` (`stageLocalId`)")
            c.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_logs_stageLocalId_date` ON `daily_logs` (`stageLocalId`, `date`)",
            )
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_entries` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`dailyLogLocalId` TEXT NOT NULL, `type` TEXT NOT NULL, `summary` TEXT, `createdById` INTEGER, " +
                    "`createdAt` TEXT, `modifiedById` INTEGER, `modifiedAt` TEXT, `syncStatus` TEXT NOT NULL, " +
                    "`pendingOp` TEXT NOT NULL, `locallyModifiedAt` INTEGER NOT NULL, `lastSyncedAt` INTEGER, " +
                    "`remoteUpdatedAt` INTEGER, `lastSyncError` TEXT, PRIMARY KEY(`localId`), " +
                    "FOREIGN KEY(`dailyLogLocalId`) REFERENCES `daily_logs`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_entries_dailyLogLocalId` ON `daily_entries` (`dailyLogLocalId`)")
            c.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_entries_dailyLogLocalId_type` ON `daily_entries` (`dailyLogLocalId`, `type`)",
            )
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `materials` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`projectLocalId` TEXT NOT NULL, `name` TEXT NOT NULL, `unit` TEXT NOT NULL, `syncStatus` TEXT NOT NULL, " +
                    "`pendingOp` TEXT NOT NULL, `locallyModifiedAt` INTEGER NOT NULL, `lastSyncedAt` INTEGER, " +
                    "`remoteUpdatedAt` INTEGER, `lastSyncError` TEXT, PRIMARY KEY(`localId`), " +
                    "FOREIGN KEY(`projectLocalId`) REFERENCES `projects`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_materials_projectLocalId` ON `materials` (`projectLocalId`)")
            c.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_materials_projectLocalId_name` ON `materials` (`projectLocalId`, `name`)",
            )
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `purchase_lines` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`entryLocalId` TEXT NOT NULL, `materialLocalId` TEXT NOT NULL, `quantity` REAL NOT NULL, " +
                    "`unitPrice` REAL NOT NULL, `totalPrice` REAL NOT NULL, `supplier` TEXT, `createdAt` TEXT, " +
                    "`syncStatus` TEXT NOT NULL, `pendingOp` TEXT NOT NULL, `locallyModifiedAt` INTEGER NOT NULL, " +
                    "`lastSyncedAt` INTEGER, `remoteUpdatedAt` INTEGER, `lastSyncError` TEXT, PRIMARY KEY(`localId`), " +
                    "FOREIGN KEY(`entryLocalId`) REFERENCES `daily_entries`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`materialLocalId`) REFERENCES `materials`(`localId`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            )
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_lines_entryLocalId` ON `purchase_lines` (`entryLocalId`)")
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_lines_materialLocalId` ON `purchase_lines` (`materialLocalId`)")
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `consumption_lines` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`entryLocalId` TEXT NOT NULL, `materialLocalId` TEXT NOT NULL, `quantity` REAL NOT NULL, " +
                    "`createdAt` TEXT, `syncStatus` TEXT NOT NULL, `pendingOp` TEXT NOT NULL, `locallyModifiedAt` INTEGER NOT NULL, " +
                    "`lastSyncedAt` INTEGER, `remoteUpdatedAt` INTEGER, `lastSyncError` TEXT, PRIMARY KEY(`localId`), " +
                    "FOREIGN KEY(`entryLocalId`) REFERENCES `daily_entries`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`materialLocalId`) REFERENCES `materials`(`localId`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            )
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_consumption_lines_entryLocalId` ON `consumption_lines` (`entryLocalId`)")
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_consumption_lines_materialLocalId` ON `consumption_lines` (`materialLocalId`)")
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `attachments` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
                    "`entryLocalId` TEXT NOT NULL, `localPath` TEXT NOT NULL, `originalName` TEXT NOT NULL, " +
                    "`mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `uploadedAt` INTEGER NOT NULL, " +
                    "`syncStatus` TEXT NOT NULL, `pendingOp` TEXT NOT NULL, `locallyModifiedAt` INTEGER NOT NULL, " +
                    "`lastSyncedAt` INTEGER, `remoteUpdatedAt` INTEGER, `lastSyncError` TEXT, PRIMARY KEY(`localId`), " +
                    "FOREIGN KEY(`entryLocalId`) REFERENCES `daily_entries`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_entryLocalId` ON `attachments` (`entryLocalId`)")
            c.execSQL(
                "CREATE TABLE IF NOT EXISTS `invitations` (`id` INTEGER NOT NULL, `projectLocalId` TEXT NOT NULL, " +
                    "`email` TEXT NOT NULL, `role` TEXT NOT NULL, `invitedById` INTEGER, `createdAt` TEXT, " +
                    "`expiresAt` TEXT, `status` TEXT NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`projectLocalId`) REFERENCES `projects`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            c.execSQL("CREATE INDEX IF NOT EXISTS `index_invitations_projectLocalId` ON `invitations` (`projectLocalId`)")
            c.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            c.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'b3f733bf9d7e64e10f9352c36d020617')",
            )
            c.execSQL("PRAGMA user_version = 7")
            c.execSQL(
                "INSERT INTO projects (localId, serverId, name, description, location, currency, timezone, status, " +
                    "ownerId, createdAt, syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, lastSyncError) " +
                    "VALUES ('p-v7', 15, 'Chantier v7', NULL, NULL, 'EUR', 'Europe/Paris', 'IN_PROGRESS', 1, NULL, " +
                    "'SYNCED', 'NONE', 1000, NULL, NULL, NULL)",
            )
        }

        val db = Room.databaseBuilder<AppDatabase>(name = v7Path).buildChantierDatabase()
        try {
            val project = db.projectDao().findByLocalId("p-v7")
            assertEquals("Chantier v7", project?.name, "v7 projects survive")
            assertEquals(null, project?.ownerPlan, "the new column defaults to null on an existing row")
        } finally {
            db.close()
        }

        // The migrated schema behaves exactly like a freshly built v8 one.
        val fresh = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
        try {
            verifyProjectDaoContract(fresh)
            verifyInvitationDaoContract(fresh)
        } finally {
            fresh.close()
        }
    }
}
