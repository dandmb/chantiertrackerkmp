package com.dmb.chantiertracker.data.local.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.dmb.chantiertracker.data.sync.syncIssue
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.SyncIssue
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.support.verifyAttachmentDaoContract
import com.dmb.chantiertracker.support.verifyDailyLogDaoContract
import com.dmb.chantiertracker.support.verifyEditorIdentityDaoContract
import com.dmb.chantiertracker.support.verifyInvitationDaoContract
import com.dmb.chantiertracker.support.verifyMaterialAndLineDaoContract
import com.dmb.chantiertracker.support.verifyPlanUsageDaoContract
import com.dmb.chantiertracker.support.verifyProjectDaoContract
import com.dmb.chantiertracker.support.verifyStageDaoContract
import com.dmb.chantiertracker.support.verifyStockDaoContract
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    /**
     * Seeds a v8 database by hand, then opens [AppDatabase] (v9) and lets
     * MIGRATION_8_9 add the `attachments.durationSeconds` column (ADR-35).
     */
    @Test
    fun migrating_from_v8_adds_attachment_duration_column_and_keeps_existing_data() = runTest {
        val v8Path = dir.resolve("migration-v8.db").absolutePathString()
        BundledSQLiteDriver().open(v8Path).use { c ->
            seedV7Schema(c)
            // v8 = v7 + projects.ownerPlan (MIGRATION_7_8).
            c.execSQL("ALTER TABLE `projects` ADD COLUMN `ownerPlan` TEXT")
            c.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            c.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '534a4cbb436aa7ed1b66e3c354a94ee5')",
            )
            c.execSQL("PRAGMA user_version = 8")
            c.execSQL(
                "INSERT INTO projects (localId, serverId, name, description, location, currency, timezone, status, " +
                    "ownerId, createdAt, syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, lastSyncError, ownerPlan) " +
                    "VALUES ('p-v8', 17, 'Chantier v8', NULL, NULL, 'EUR', 'Europe/Paris', 'IN_PROGRESS', 1, NULL, " +
                    "'SYNCED', 'NONE', 1000, NULL, NULL, NULL, 'SEMI_FLEX')",
            )
            c.execSQL(
                "INSERT INTO stages (localId, serverId, projectLocalId, name, description, estimatedBudget, startDate, endDate, " +
                    "status, syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, lastSyncError) " +
                    "VALUES ('s-v8', 8, 'p-v8', 'Gros œuvre', NULL, NULL, NULL, NULL, 'IN_PROGRESS', 'SYNCED', 'NONE', 1000, NULL, NULL, NULL)",
            )
            c.execSQL("INSERT INTO daily_logs (localId, serverId, stageLocalId, date, locallyCreatedAt, lastSyncedAt) VALUES ('l-v8', 80, 's-v8', '2026-09-05', 1000, NULL)")
            c.execSQL(
                "INSERT INTO daily_entries (localId, serverId, dailyLogLocalId, type, summary, createdById, createdAt, modifiedById, modifiedAt, " +
                    "syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, lastSyncError) " +
                    "VALUES ('e-v8', 90, 'l-v8', 'PURCHASE', NULL, 1, NULL, NULL, NULL, 'SYNCED', 'NONE', 1000, NULL, NULL, NULL)",
            )
            c.execSQL(
                "INSERT INTO attachments (localId, serverId, entryLocalId, localPath, originalName, mimeType, sizeBytes, uploadedAt, " +
                    "syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, lastSyncError) " +
                    "VALUES ('a-v8', 111, 'e-v8', '/x/a.jpg', 'facture.jpg', 'image/jpeg', 2048, 1000, 'SYNCED', 'NONE', 1000, NULL, NULL, NULL)",
            )
        }

        val db = Room.databaseBuilder<AppDatabase>(name = v8Path).buildChantierDatabase()
        try {
            val photo = db.attachmentDao().findByLocalId("a-v8")
            assertEquals("facture.jpg", photo?.originalName, "v8 attachments survive")
            assertEquals(null, photo?.durationSeconds, "the new column defaults to null on an existing photo row")
        } finally {
            db.close()
        }

        // The migrated schema behaves exactly like a freshly built v9 one.
        val fresh = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
        try {
            verifyAttachmentDaoContract(fresh)
        } finally {
            fresh.close()
        }
    }

    /**
     * Seeds a v9 database by hand, then opens [AppDatabase] (v10) and lets
     * MIGRATION_9_10 rewrite `attachments.localPath` from an absolute path (bare
     * path or `file://` URL) to the bare file name — the stable key (ADR-41).
     */
    @Test
    fun migrating_from_v9_rewrites_attachment_paths_to_bare_keys() = runTest {
        val v9Path = dir.resolve("migration-v9.db").absolutePathString()
        BundledSQLiteDriver().open(v9Path).use { c ->
            seedV7Schema(c)
            c.execSQL("ALTER TABLE `projects` ADD COLUMN `ownerPlan` TEXT")
            c.execSQL("ALTER TABLE `attachments` ADD COLUMN `durationSeconds` INTEGER")
            c.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            c.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'd02df2e8cdb59fb510f00b1c877527a8')",
            )
            c.execSQL("PRAGMA user_version = 9")
            c.execSQL(
                "INSERT INTO projects (localId, serverId, name, description, location, currency, timezone, status, " +
                    "ownerId, createdAt, syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, lastSyncError, ownerPlan) " +
                    "VALUES ('p-v9', 19, 'Chantier v9', NULL, NULL, 'EUR', 'Europe/Paris', 'IN_PROGRESS', 1, NULL, " +
                    "'SYNCED', 'NONE', 1000, NULL, NULL, NULL, NULL)",
            )
            c.execSQL("INSERT INTO stages (localId, serverId, projectLocalId, name, description, estimatedBudget, startDate, endDate, status, syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, lastSyncError) VALUES ('s-v9', 9, 'p-v9', 'Fondation', NULL, NULL, NULL, NULL, 'IN_PROGRESS', 'SYNCED', 'NONE', 1000, NULL, NULL, NULL)")
            c.execSQL("INSERT INTO daily_logs (localId, serverId, stageLocalId, date, locallyCreatedAt, lastSyncedAt) VALUES ('l-v9', 90, 's-v9', '2026-09-06', 1000, NULL)")
            c.execSQL(
                "INSERT INTO daily_entries (localId, serverId, dailyLogLocalId, type, summary, createdById, createdAt, modifiedById, modifiedAt, " +
                    "syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, lastSyncError) " +
                    "VALUES ('e-v9', 91, 'l-v9', 'PURCHASE', NULL, 1, NULL, NULL, NULL, 'SYNCED', 'NONE', 1000, NULL, NULL, NULL)",
            )
            fun seedAttachment(id: String, serverId: Long, path: String, mime: String) = c.execSQL(
                "INSERT INTO attachments (localId, serverId, entryLocalId, localPath, originalName, mimeType, sizeBytes, uploadedAt, " +
                    "syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, lastSyncError, durationSeconds) " +
                    "VALUES ('$id', $serverId, 'e-v9', '$path', 'name', '$mime', 2048, 1000, 'SYNCED', 'NONE', 1000, NULL, NULL, NULL, NULL)",
            )
            // iOS style: a "file://" URL into a (now stale) app container.
            seedAttachment("a-ios", 201, "file:///Users/x/Library/Developer/CoreSimulator/Devices/D/data/Containers/Data/Application/OLD/Documents/attachments/03d2f6db.jpeg", "image/jpeg")
            // Android style: a bare absolute path.
            seedAttachment("a-android", 202, "/data/user/0/com.dmb.chantiertracker/files/attachments/e7cab5cf.mp4", "video/mp4")
        }

        val db = Room.databaseBuilder<AppDatabase>(name = v9Path).buildChantierDatabase()
        try {
            assertEquals("03d2f6db.jpeg", db.attachmentDao().findByLocalId("a-ios")?.localPath, "the iOS file:// URL becomes a bare key")
            assertEquals("e7cab5cf.mp4", db.attachmentDao().findByLocalId("a-android")?.localPath, "the Android absolute path becomes a bare key")
        } finally {
            db.close()
        }
    }

    /**
     * Seeds a v10 database by hand, then opens [AppDatabase] (v11) and lets
     * MIGRATION_10_11 add the billing-screen usage/expiry/customer columns to
     * `plan_usage` (ADR-49) — all nullable, existing `plan`/`projectsLimit`
     * untouched.
     */
    @Test
    fun migrating_from_v10_adds_plan_usage_detail_columns_and_keeps_existing_data() = runTest {
        val v10Path = dir.resolve("migration-v10.db").absolutePathString()
        BundledSQLiteDriver().open(v10Path).use { c ->
            seedV7Schema(c)
            c.execSQL("ALTER TABLE `projects` ADD COLUMN `ownerPlan` TEXT")
            c.execSQL("ALTER TABLE `attachments` ADD COLUMN `durationSeconds` INTEGER")
            c.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            c.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'd02df2e8cdb59fb510f00b1c877527a8')",
            )
            c.execSQL("PRAGMA user_version = 10")
            c.execSQL(
                "INSERT INTO plan_usage (id, plan, projectsLimit, refreshedAt) VALUES (0, 'SEMI_FLEX', 3, 5000)",
            )
        }

        val db = Room.databaseBuilder<AppDatabase>(name = v10Path).buildChantierDatabase()
        try {
            val row = db.planUsageDao().observe().first()
            assertEquals("SEMI_FLEX", row?.plan, "the pre-existing plan/limit survive")
            assertEquals(3, row?.projectsLimit)
            assertEquals(null, row?.projectsUsed, "a row cached before this migration reads the new columns as unknown, not a fabricated 0")
            assertEquals(null, row?.hasStripeCustomer)
        } finally {
            db.close()
        }

        // The migrated schema behaves exactly like a freshly built v11 one.
        val fresh = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
        try {
            verifyPlanUsageDaoContract(fresh)
        } finally {
            fresh.close()
        }
    }

    /**
     * Seeds a v11 database by hand, then opens [AppDatabase] (v12) and lets
     * MIGRATION_11_12 add the founders-program columns (ADR-66): the owner's
     * combined entitlements on `projects`, `isFounder` on `plan_usage` — all
     * nullable, so pre-existing rows read "never received", not a fabricated
     * false/0 that would hide the export or shorten the history.
     */
    @Test
    fun migrating_from_v11_adds_founder_columns_and_keeps_existing_data() = runTest {
        val v11Path = dir.resolve("migration-v11.db").absolutePathString()
        BundledSQLiteDriver().open(v11Path).use { c ->
            seedV7Schema(c)
            c.execSQL("ALTER TABLE `projects` ADD COLUMN `ownerPlan` TEXT")
            c.execSQL("ALTER TABLE `attachments` ADD COLUMN `durationSeconds` INTEGER")
            listOf(
                "projectsUsed INTEGER", "photosUsed INTEGER", "photosLimit INTEGER", "videosUsed INTEGER",
                "videosLimit INTEGER", "videoDurationLimitSeconds INTEGER", "supervisorsUsed INTEGER",
                "supervisorsLimit INTEGER", "planExpiresAt TEXT", "hasStripeCustomer INTEGER",
            ).forEach { column -> c.execSQL("ALTER TABLE `plan_usage` ADD COLUMN $column") }
            c.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            c.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '14a77b9aa14615c8b69e2ad9f15c7642')",
            )
            c.execSQL("PRAGMA user_version = 11")
            c.execSQL(
                "INSERT INTO projects (localId, serverId, name, description, location, currency, timezone, status, " +
                    "ownerId, createdAt, syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, " +
                    "lastSyncError, ownerPlan) VALUES ('p-v11', 42, 'Chantier v11', NULL, NULL, 'EUR', 'Europe/Paris', " +
                    "'IN_PROGRESS', 1, NULL, 'SYNCED', 'NONE', 1000, NULL, NULL, NULL, 'SEMI_FLEX')",
            )
            c.execSQL(
                "INSERT INTO plan_usage (id, plan, projectsLimit, refreshedAt, photosLimit, hasStripeCustomer) " +
                    "VALUES (0, 'SEMI_FLEX', 3, 5000, 300, 1)",
            )
        }

        val db = Room.databaseBuilder<AppDatabase>(name = v11Path).buildChantierDatabase()
        try {
            val project = db.projectDao().findByLocalId("p-v11")
            assertEquals("Chantier v11", project?.name, "v11 data survives the migration")
            assertEquals("SEMI_FLEX", project?.ownerPlan, "the plan-based fallback is still there")
            assertEquals(null, project?.ownerIsFounder, "never received yet — not a fabricated false")
            assertEquals(null, project?.ownerCanExportPdf)
            assertEquals(null, project?.ownerMaxHistoryDays)
            assertEquals(null, project?.ownerMaxVideos)
            assertEquals(null, project?.ownerMaxVideoDurationSeconds)
            assertEquals(null, project?.ownerMaxSupervisorsPerProject)

            val usage = db.planUsageDao().observe().first()
            assertEquals("SEMI_FLEX", usage?.plan)
            assertEquals(300, usage?.photosLimit)
            assertEquals(true, usage?.hasStripeCustomer)
            assertEquals(null, usage?.isFounder)
            assertEquals(null, usage?.historyDaysLimitKnown, "not received yet — a NULL limit must not read as 'unlimited'")

            db.projectDao().upsert(
                project!!.copy(
                    ownerIsFounder = true, ownerCanExportPdf = true, ownerMaxHistoryDays = 180,
                    ownerMaxVideos = 5, ownerMaxVideoDurationSeconds = 120, ownerMaxSupervisorsPerProject = 3,
                ),
            )
            val updated = db.projectDao().findByLocalId("p-v11")
            assertEquals(true, updated?.ownerIsFounder, "the new columns are writable on a migrated database")
            assertEquals(true, updated?.ownerCanExportPdf)
            assertEquals(180, updated?.ownerMaxHistoryDays)
            assertEquals(5, updated?.ownerMaxVideos)
            assertEquals(120, updated?.ownerMaxVideoDurationSeconds)
            assertEquals(3, updated?.ownerMaxSupervisorsPerProject)

            db.planUsageDao().upsert(usage!!.copy(isFounder = true, historyDaysLimit = 180, historyDaysLimitKnown = true))
            val updatedUsage = db.planUsageDao().observe().first()
            assertEquals(true, updatedUsage?.isFounder)
            assertEquals(180, updatedUsage?.historyDaysLimit)
            assertEquals(true, updatedUsage?.historyDaysLimitKnown)
        } finally {
            db.close()
        }

        // The migrated schema behaves exactly like a freshly built v12 one.
        val fresh = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
        try {
            verifyProjectDaoContract(fresh)
            verifyPlanUsageDaoContract(fresh)
        } finally {
            fresh.close()
        }
    }

    /**
     * Seeds a v12 database through the real v11 → v12 step, then opens [AppDatabase] (v13) and
     * lets MIGRATION_12_13 create `editor_identity` (ADR-68) — empty, usable, nothing else touched.
     */
    @Test
    fun migrating_from_v12_adds_the_editor_identity_table_and_keeps_existing_data() = runTest {
        val v12Path = dir.resolve("migration-v12.db").absolutePathString()
        BundledSQLiteDriver().open(v12Path).use { c ->
            seedV7Schema(c)
            c.execSQL("ALTER TABLE `projects` ADD COLUMN `ownerPlan` TEXT")
            c.execSQL("ALTER TABLE `attachments` ADD COLUMN `durationSeconds` INTEGER")
            listOf(
                "projectsUsed INTEGER", "photosUsed INTEGER", "photosLimit INTEGER", "videosUsed INTEGER",
                "videosLimit INTEGER", "videoDurationLimitSeconds INTEGER", "supervisorsUsed INTEGER",
                "supervisorsLimit INTEGER", "planExpiresAt TEXT", "hasStripeCustomer INTEGER",
            ).forEach { column -> c.execSQL("ALTER TABLE `plan_usage` ADD COLUMN $column") }
            MIGRATION_11_12.migrate(c)
            c.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            c.execSQL("PRAGMA user_version = 12")
            c.execSQL(
                "INSERT INTO projects (localId, serverId, name, description, location, currency, timezone, status, " +
                    "ownerId, createdAt, syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, " +
                    "lastSyncError, ownerPlan, ownerIsFounder) VALUES ('p-v12', 42, 'Chantier v12', NULL, NULL, 'EUR', " +
                    "'Europe/Paris', 'IN_PROGRESS', 1, NULL, 'SYNCED', 'NONE', 1000, NULL, NULL, NULL, 'FREE', 1)",
            )
        }

        val db = Room.databaseBuilder<AppDatabase>(name = v12Path).buildChantierDatabase()
        try {
            val project = db.projectDao().findByLocalId("p-v12")
            assertEquals("Chantier v12", project?.name, "v12 data survives the migration")
            assertEquals(true, project?.ownerIsFounder)
            assertEquals(null, db.editorIdentityDao().observe().first(), "the new table starts empty")
            verifyEditorIdentityDaoContract(db)
        } finally {
            db.close()
        }
    }

    @Test
    fun migrating_from_v13_adds_the_server_stock_tables_and_backfills_what_the_server_holds() = runTest {
        val v13Path = dir.resolve("migration-v13.db").absolutePathString()
        BundledSQLiteDriver().open(v13Path).use { c ->
            seedV7Schema(c)
            c.execSQL("ALTER TABLE `projects` ADD COLUMN `ownerPlan` TEXT")
            c.execSQL("ALTER TABLE `attachments` ADD COLUMN `durationSeconds` INTEGER")
            listOf(
                "projectsUsed INTEGER", "photosUsed INTEGER", "photosLimit INTEGER", "videosUsed INTEGER",
                "videosLimit INTEGER", "videoDurationLimitSeconds INTEGER", "supervisorsUsed INTEGER",
                "supervisorsLimit INTEGER", "planExpiresAt TEXT", "hasStripeCustomer INTEGER",
            ).forEach { column -> c.execSQL("ALTER TABLE `plan_usage` ADD COLUMN $column") }
            MIGRATION_11_12.migrate(c)
            MIGRATION_12_13.migrate(c)
            c.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            c.execSQL("PRAGMA user_version = 13")
            c.execSQL(
                "INSERT INTO projects (localId, serverId, name, description, location, currency, timezone, status, " +
                    "ownerId, createdAt, syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, " +
                    "lastSyncError) VALUES ('p-v13', 42, 'Chantier v13', NULL, NULL, 'EUR', 'Europe/Paris', 'IN_PROGRESS', 1, NULL, " +
                    "'SYNCED', 'NONE', 1000, NULL, NULL, NULL)",
            )
            c.execSQL(
                "INSERT INTO stages (localId, serverId, projectLocalId, name, description, estimatedBudget, startDate, endDate, status, " +
                    "syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt) " +
                    "VALUES ('s-v13', 43, 'p-v13', 'Gros œuvre', NULL, NULL, NULL, NULL, 'IN_PROGRESS', 'SYNCED', 'NONE', 1000, NULL)",
            )
            c.execSQL("INSERT INTO daily_logs (localId, serverId, stageLocalId, date, locallyCreatedAt, lastSyncedAt) VALUES ('l-v13', 44, 's-v13', '2026-09-05', 1000, NULL)")
            c.execSQL(
                "INSERT INTO daily_entries (localId, serverId, dailyLogLocalId, type, summary, createdById, createdAt, modifiedById, modifiedAt, " +
                    "syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, lastSyncError) " +
                    "VALUES ('e-v13', 45, 'l-v13', 'PURCHASE', NULL, NULL, NULL, NULL, NULL, 'SYNCED', 'NONE', 1000, NULL, NULL, NULL)",
            )
            c.execSQL(
                "INSERT INTO materials (localId, serverId, projectLocalId, name, unit, syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, lastSyncError) " +
                    "VALUES ('m-v13', 7, 'p-v13', 'Ciment', 'sac', 'SYNCED', 'NONE', 1000, NULL, NULL, NULL)",
            )
            listOf("('pl-synced', 60, 10.0, 'SYNCED', 'NONE')", "('pl-pending', NULL, 4.0, 'PENDING', 'CREATE')").forEach { values ->
                val (id, serverId, quantity, status, op) = values.removeSurrounding("(", ")").split(", ")
                c.execSQL(
                    "INSERT INTO purchase_lines (localId, serverId, entryLocalId, materialLocalId, quantity, unitPrice, totalPrice, supplier, createdAt, " +
                        "syncStatus, pendingOp, locallyModifiedAt, lastSyncedAt, remoteUpdatedAt, lastSyncError) " +
                        "VALUES ($id, $serverId, 'e-v13', 'm-v13', $quantity, 1.0, $quantity, NULL, NULL, $status, $op, 1000, NULL, NULL, NULL)",
                )
            }
        }

        val db = Room.databaseBuilder<AppDatabase>(name = v13Path).buildChantierDatabase()
        try {
            assertEquals("Chantier v13", db.projectDao().findByLocalId("p-v13")?.name, "v13 data survives the migration")
            assertEquals(10.0, db.purchaseLineDao().findByLocalId("pl-synced")?.serverQuantity, "a line already on the server is backfilled")
            assertEquals(null, db.purchaseLineDao().findByLocalId("pl-pending")?.serverQuantity, "a line never sent holds nothing on the server")
            assertEquals(null, db.stockDao().findSnapshot("p-v13"), "the stock is not loaded until the next sync")
            verifyStockDaoContract(db)
        } finally {
            db.close()
        }
    }

    @Test
    fun migrating_a_real_v14_database_keeps_every_row_and_adds_an_empty_server_error_code() = runTest {
        val v14Path = dir.resolve("migration-v14.db").absolutePathString()
        BundledSQLiteDriver().open(v14Path).use { c -> seedARealV14Database(c) }

        val db = Room.databaseBuilder<AppDatabase>(name = v14Path).buildChantierDatabase()
        try {
            assertEquals(listOf("p-over-limit", "p-synced"), db.projectDao().findAll().map { it.localId }.sorted(), "every v14 project survives")
            assertEquals(3, db.stageDao().findForProject("p-synced").size)
            assertEquals(3, db.purchaseLineDao().findForEntry("e-synced").size)
            assertEquals(10.0, db.stockDao().findCounter("p-synced", 7)?.quantityIn)

            val rows = listOf(
                db.projectDao().findByLocalId("p-synced"), db.projectDao().findByLocalId("p-over-limit"),
                db.stageDao().findByLocalId("s-edit-refused"), db.stageDao().findByLocalId("s-delete-refused"),
                db.dailyEntryDao().findByLocalId("e-gone"), db.materialDao().findByLocalId("m-v14"),
                db.purchaseLineDao().findByLocalId("pl-rejected"), db.consumptionLineDao().findByLocalId("cl-rejected"),
                db.attachmentDao().findByLocalId("a-rejected"),
            )
            rows.forEach { row -> assertEquals(null, row!!.serverErrorCode, "no code is invented for a row refused before the migration") }

            val rejectedLine = db.purchaseLineDao().findByLocalId("pl-rejected")!!
            assertEquals(SyncStatus.CONFLICTED, rejectedLine.syncStatus)
            assertEquals("REJECTED", rejectedLine.lastSyncError)
            assertEquals(2.675, rejectedLine.quantity)
            assertEquals(
                SyncIssue(SyncIssueKind.REFUSED, RefusalReason.UNKNOWN, null),
                rejectedLine.syncIssue(),
                "a line refused before the migration reads as refused, with the generic reason",
            )
            assertEquals(SyncIssue(SyncIssueKind.REFUSED, RefusalReason.PLAN_LIMIT, null), db.projectDao().findByLocalId("p-over-limit")!!.syncIssue())
            assertEquals(SyncIssueKind.UPDATE_REFUSED, db.stageDao().findByLocalId("s-edit-refused")!!.syncIssue()?.kind)
            assertEquals(SyncIssueKind.DELETE_REFUSED, db.stageDao().findByLocalId("s-delete-refused")!!.syncIssue()?.kind)
            assertEquals(SyncIssueKind.DELETED_ON_SERVER, db.dailyEntryDao().findByLocalId("e-gone")!!.syncIssue()?.kind)
            assertEquals(null, db.purchaseLineDao().findByLocalId("pl-pending")!!.syncIssue())

            assertEquals(listOf("pl-pending", "pl-rejected"), db.purchaseLineDao().findPending().map { it.localId }.sorted(), "a creation refused before the migration is still sent again")
            assertEquals(listOf("s-edit-refused"), db.stageDao().findPending().map { it.localId }, "an edit refused before the migration is sent once more, then waits for the user")

            db.purchaseLineDao().upsert(rejectedLine.copy(serverErrorCode = "INVALID_AMOUNT"))
            assertEquals("INVALID_AMOUNT", db.purchaseLineDao().findByLocalId("pl-rejected")?.serverErrorCode, "the new column is writable")
        } finally {
            db.close()
        }
    }

    @Test
    fun the_exported_v15_schema_adds_only_a_nullable_server_error_code_to_the_seven_synced_tables() {
        fun columnsByTable(version: Int): Map<String, Map<String, Pair<String, Boolean>>> =
            exportedSchema(version)["entities"]!!.jsonArray.associate { entity ->
                entity.jsonObject["tableName"]!!.jsonPrimitive.content to entity.jsonObject["fields"]!!.jsonArray.associate { field ->
                    val f = field.jsonObject
                    f["columnName"]!!.jsonPrimitive.content to (f["affinity"]!!.jsonPrimitive.content to (f["notNull"]?.jsonPrimitive?.content == "true"))
                }
            }
        val v14 = columnsByTable(14)
        val v15 = columnsByTable(15)

        assertEquals(v14.keys, v15.keys, "no table added or removed")
        val added = v15.flatMap { (table, columns) -> (columns.keys - v14.getValue(table).keys).map { table to it } }.toSet()
        assertEquals(
            setOf("projects", "stages", "materials", "daily_entries", "purchase_lines", "consumption_lines", "attachments").map { it to "serverErrorCode" }.toSet(),
            added,
        )
        added.forEach { (table, column) -> assertEquals("TEXT" to false, v15.getValue(table).getValue(column), "$table.$column is a nullable TEXT") }
        v14.forEach { (table, columns) -> columns.forEach { (name, type) -> assertEquals(type, v15.getValue(table).getValue(name), "$table.$name unchanged") } }
    }

    @Test
    fun migrating_a_real_v15_database_keeps_every_row_starts_the_local_version_at_zero_and_moves_the_awaited_note_to_its_own_column() = runTest {
        val v15Path = dir.resolve("migration-v15.db").absolutePathString()
        val awaitedTables = listOf("projects", "stages", "materials", "daily_entries", "purchase_lines", "consumption_lines")
        BundledSQLiteDriver().open(v15Path).use { c ->
            createFromExportedSchema(c, version = 15)
            c.execSQL(
                "INSERT INTO projects (localId, serverId, name, currency, timezone, status, syncStatus, pendingOp, locallyModifiedAt, lastSyncError, serverErrorCode) VALUES " +
                    "('p-synced', 42, 'Chantier v15', 'EUR', 'Europe/Paris', 'IN_PROGRESS', 'SYNCED', 'NONE', 1000, NULL, NULL), " +
                    "('p-awaiting', 49, 'Relu bientôt', 'EUR', 'Europe/Paris', 'IN_PROGRESS', 'SYNCED', 'NONE', 1000, 'AWAITING_SERVER_VERSION', NULL), " +
                    "('p-over-limit', NULL, 'Au-delà de la limite', 'EUR', 'Europe/Paris', 'IN_PROGRESS', 'CONFLICTED', 'CREATE', 1000, 'PLAN_LIMIT', 'PLAN_LIMIT_EXCEEDED')",
            )
            c.execSQL(
                "INSERT INTO stages (localId, serverId, projectLocalId, name, status, syncStatus, pendingOp, locallyModifiedAt, lastSyncError, serverErrorCode) VALUES " +
                    "('s-synced', 43, 'p-synced', 'Gros œuvre', 'IN_PROGRESS', 'SYNCED', 'NONE', 1000, NULL, NULL), " +
                    "('s-pending', NULL, 'p-synced', 'Peinture', 'IN_PROGRESS', 'PENDING', 'CREATE', 1000, NULL, NULL), " +
                    "('s-edit-refused', 44, 'p-synced', 'Charpente', 'IN_PROGRESS', 'CONFLICTED', 'UPDATE', 1000, 'UPDATE_REFUSED', 'PROJECT_INSUFFICIENT_ROLE'), " +
                    "('s-delete-refused', 45, 'p-synced', 'Toiture', 'IN_PROGRESS', 'SYNCED', 'NONE', 1000, 'REJECTED', 'PROJECT_INSUFFICIENT_ROLE'), " +
                    "('s-awaiting', 46, 'p-synced', 'Isolation', 'IN_PROGRESS', 'SYNCED', 'NONE', 1000, 'AWAITING_SERVER_VERSION', NULL)",
            )
            c.execSQL("INSERT INTO daily_logs (localId, serverId, stageLocalId, date, locallyCreatedAt) VALUES ('l-v15', 47, 's-synced', '2026-10-01', 1000)")
            c.execSQL(
                "INSERT INTO daily_entries (localId, serverId, dailyLogLocalId, type, summary, syncStatus, pendingOp, locallyModifiedAt, lastSyncError, serverErrorCode) VALUES " +
                    "('e-awaiting', 48, 'l-v15', 'PURCHASE', 'Dernière version connue', 'SYNCED', 'NONE', 1000, 'AWAITING_SERVER_VERSION', NULL), " +
                    "('e-gone', 50, 'l-v15', 'WORK', NULL, 'CONFLICTED', 'UPDATE', 1000, 'DELETED_ON_SERVER', NULL)",
            )
            c.execSQL(
                "INSERT INTO materials (localId, serverId, projectLocalId, name, unit, syncStatus, pendingOp, locallyModifiedAt, lastSyncError, serverErrorCode) VALUES " +
                    "('m-synced', 7, 'p-synced', 'Ciment', 'sac', 'SYNCED', 'NONE', 1000, NULL, NULL), " +
                    "('m-awaiting', 8, 'p-synced', 'Sable', 'kg', 'SYNCED', 'NONE', 1000, 'AWAITING_SERVER_VERSION', NULL), " +
                    "('m-refused', NULL, 'p-synced', 'Gravier', 't', 'CONFLICTED', 'CREATE', 1000, 'REJECTED', 'DUPLICATE_MATERIAL')",
            )
            c.execSQL(
                "INSERT INTO purchase_lines (localId, serverId, entryLocalId, materialLocalId, quantity, unitPrice, totalPrice, syncStatus, pendingOp, " +
                    "locallyModifiedAt, lastSyncError, serverErrorCode, serverQuantity) VALUES " +
                    "('pl-synced', 60, 'e-awaiting', 'm-synced', 10.0, 2.0, 20.0, 'SYNCED', 'NONE', 1000, NULL, NULL, 10.0), " +
                    "('pl-pending', NULL, 'e-awaiting', 'm-synced', 4.0, 2.0, 8.0, 'PENDING', 'CREATE', 1000, NULL, NULL, NULL), " +
                    "('pl-rejected', NULL, 'e-awaiting', 'm-synced', 2.675, 2.0, 5.35, 'CONFLICTED', 'CREATE', 1000, 'REJECTED', 'INVALID_AMOUNT', NULL), " +
                    "('pl-awaiting', 61, 'e-awaiting', 'm-synced', 6.0, 2.0, 12.0, 'SYNCED', 'NONE', 1000, 'AWAITING_SERVER_VERSION', NULL, 6.0)",
            )
            c.execSQL(
                "INSERT INTO consumption_lines (localId, serverId, entryLocalId, materialLocalId, quantity, syncStatus, pendingOp, locallyModifiedAt, lastSyncError, serverErrorCode, serverQuantity) VALUES " +
                    "('cl-change-refused', 70, 'e-gone', 'm-synced', 99.0, 'CONFLICTED', 'UPDATE', 1000, 'UPDATE_REFUSED', 'INSUFFICIENT_STOCK', 2.0), " +
                    "('cl-awaiting', 71, 'e-gone', 'm-synced', 3.0, 'SYNCED', 'NONE', 1000, 'AWAITING_SERVER_VERSION', NULL, 3.0)",
            )
            c.execSQL(
                "INSERT INTO attachments (localId, serverId, entryLocalId, localPath, originalName, mimeType, sizeBytes, uploadedAt, syncStatus, pendingOp, " +
                    "locallyModifiedAt, lastSyncError, serverErrorCode) VALUES " +
                    "('a-refused', NULL, 'e-awaiting', 'ticket.jpg', 'ticket.jpg', 'image/jpeg', 1024, 1000, 'CONFLICTED', 'CREATE', 1000, 'FILE_REFUSED', 'ATTACHMENT_TOO_LARGE'), " +
                    "('a-pending', NULL, 'e-awaiting', 'bon.jpg', 'bon.jpg', 'image/jpeg', 2048, 1000, 'PENDING', 'CREATE', 1000, NULL, NULL)",
            )
        }

        val db = Room.databaseBuilder<AppDatabase>(name = v15Path).buildChantierDatabase()
        try {
            val projects = db.projectDao().findAll().associateBy { it.localId }
            val stages = db.stageDao().findForProject("p-synced").associateBy { it.localId }
            val materials = db.materialDao().findForProject("p-synced").associateBy { it.localId }
            val entries = db.dailyEntryDao().findForLog("l-v15").associateBy { it.localId }
            val purchaseLines = db.purchaseLineDao().findForEntry("e-awaiting").associateBy { it.localId }
            val consumptionLines = db.consumptionLineDao().findForEntry("e-gone").associateBy { it.localId }
            val attachments = db.attachmentDao().findForEntry("e-awaiting").associateBy { it.localId }
            assertEquals(
                listOf(3, 5, 3, 2, 4, 2, 2),
                listOf(projects.size, stages.size, materials.size, entries.size, purchaseLines.size, consumptionLines.size, attachments.size),
                "every v15 row survives",
            )
            val versioned = projects.values + stages.values + materials.values + entries.values + purchaseLines.values + consumptionLines.values + attachments.values
            assertTrue(versioned.all { it.localVersion == 0L }, "the local version starts at zero on every existing row")

            val awaited = listOf(
                projects.getValue("p-awaiting"), stages.getValue("s-awaiting"), materials.getValue("m-awaiting"),
                entries.getValue("e-awaiting"), purchaseLines.getValue("pl-awaiting"), consumptionLines.getValue("cl-awaiting"),
            )
            awaited.forEach { row ->
                assertEquals(
                    listOf<Any?>(true, null, SyncStatus.SYNCED, PendingOp.NONE), listOf(row.awaitsServerVersion, row.lastSyncError, row.syncStatus, row.pendingOp),
                    "the note moves to its own column and leaves the error column: $row",
                )
            }
            assertEquals("Dernière version connue", entries.getValue("e-awaiting").summary, "with the last known values")
            assertEquals(
                setOf("p-awaiting", "s-awaiting", "m-awaiting", "e-awaiting", "pl-awaiting", "cl-awaiting"),
                db.syncIssueActionDao().rowsAwaitingServerVersion().map { it.localId }.toSet(),
                "the engine still finds what it has to read again",
            )
            val others = (projects.values + stages.values + materials.values + entries.values + purchaseLines.values + consumptionLines.values).filter { row -> awaited.none { it === row } }
            assertTrue(others.none { it.awaitsServerVersion }, "nothing else is noted")

            assertEquals(listOf<Any?>(SyncStatus.CONFLICTED, "PLAN_LIMIT", "PLAN_LIMIT_EXCEEDED"), projects.getValue("p-over-limit").let { listOf(it.syncStatus, it.lastSyncError, it.serverErrorCode) })
            assertEquals(listOf<Any?>(SyncStatus.CONFLICTED, "UPDATE_REFUSED", "PROJECT_INSUFFICIENT_ROLE"), stages.getValue("s-edit-refused").let { listOf(it.syncStatus, it.lastSyncError, it.serverErrorCode) })
            assertEquals(SyncIssueKind.DELETE_REFUSED, stages.getValue("s-delete-refused").syncIssue()?.kind)
            assertEquals(SyncIssueKind.DELETED_ON_SERVER, entries.getValue("e-gone").syncIssue()?.kind)
            assertEquals(listOf<Any?>("REJECTED", "INVALID_AMOUNT", 2.675), purchaseLines.getValue("pl-rejected").let { listOf(it.lastSyncError, it.serverErrorCode, it.quantity) })
            assertEquals(listOf<Any?>("UPDATE_REFUSED", "INSUFFICIENT_STOCK", 99.0, 2.0), consumptionLines.getValue("cl-change-refused").let { listOf(it.lastSyncError, it.serverErrorCode, it.quantity, it.serverQuantity) })
            assertEquals(listOf<Any?>("FILE_REFUSED", "ATTACHMENT_TOO_LARGE"), attachments.getValue("a-refused").let { listOf(it.lastSyncError, it.serverErrorCode) })
            assertEquals(null, awaited.firstNotNullOfOrNull { it.syncIssue() }, "an awaited row is not an issue")

            assertEquals(listOf("pl-pending", "pl-rejected"), db.purchaseLineDao().findPending().map { it.localId }.sorted(), "what waited to be sent still does")
            assertEquals(listOf("s-pending"), db.stageDao().findPending().map { it.localId })
            assertEquals(listOf("a-pending"), db.attachmentDao().findPending().map { it.localId })
            assertEquals(
                setOf("p-over-limit", "s-edit-refused", "s-delete-refused", "e-gone", "m-refused", "pl-rejected", "cl-change-refused", "a-refused"),
                db.syncIssueDao().observeUnsettled().first().filter { it.syncStatus != SyncStatus.PENDING }.map { it.localId }.toSet(),
                "the entries to review are the same as before the migration",
            )

            db.materialAdoptionDao().mergeInto("m-refused", materials.getValue("m-synced"))
            assertEquals("m-synced", db.purchaseLineDao().survivorOfMergedMaterial("m-refused"), "the table of merged materials exists and is usable")
            assertTrue(db.purchaseLineDao().insertNew(purchaseLines.getValue("pl-pending").copy(localId = "pl-after-merge", materialLocalId = "m-refused")))
            assertEquals("m-synced", db.purchaseLineDao().findByLocalId("pl-after-merge")!!.materialLocalId)

            db.stageDao().changeLocally("s-pending") { it.copy(name = "Peinture intérieure") }
            assertEquals(listOf<Any?>("Peinture intérieure", 1L), db.stageDao().findByLocalId("s-pending")!!.let { listOf(it.name, it.localVersion) }, "the new column counts the next change")
        } finally {
            db.close()
        }
    }

    @Test
    fun migrating_a_real_v14_database_straight_to_v16_keeps_every_row_with_a_local_version_of_zero_and_nothing_awaited() = runTest {
        val v14Path = dir.resolve("migration-v14-to-v16.db").absolutePathString()
        BundledSQLiteDriver().open(v14Path).use { c -> seedARealV14Database(c) }

        val db = Room.databaseBuilder<AppDatabase>(name = v14Path).buildChantierDatabase()
        try {
            val rows = listOf(
                db.projectDao().findByLocalId("p-synced"), db.projectDao().findByLocalId("p-over-limit"),
                db.stageDao().findByLocalId("s-synced"), db.stageDao().findByLocalId("s-edit-refused"), db.stageDao().findByLocalId("s-delete-refused"),
                db.dailyEntryDao().findByLocalId("e-synced"), db.dailyEntryDao().findByLocalId("e-gone"), db.materialDao().findByLocalId("m-v14"),
                db.purchaseLineDao().findByLocalId("pl-synced"), db.purchaseLineDao().findByLocalId("pl-pending"), db.purchaseLineDao().findByLocalId("pl-rejected"),
                db.consumptionLineDao().findByLocalId("cl-rejected"),
            ).map { it!! }
            rows.forEach { row -> assertEquals(listOf<Any?>(false, null), listOf(row.awaitsServerVersion, row.serverErrorCode), "$row") }
            assertEquals(0L, db.attachmentDao().findByLocalId("a-rejected")!!.localVersion)
            assertEquals(
                List(12) { 0L },
                listOf(
                    db.projectDao().findByLocalId("p-synced")!!.localVersion, db.projectDao().findByLocalId("p-over-limit")!!.localVersion,
                    db.stageDao().findByLocalId("s-synced")!!.localVersion, db.stageDao().findByLocalId("s-edit-refused")!!.localVersion, db.stageDao().findByLocalId("s-delete-refused")!!.localVersion,
                    db.dailyEntryDao().findByLocalId("e-synced")!!.localVersion, db.dailyEntryDao().findByLocalId("e-gone")!!.localVersion, db.materialDao().findByLocalId("m-v14")!!.localVersion,
                    db.purchaseLineDao().findByLocalId("pl-synced")!!.localVersion, db.purchaseLineDao().findByLocalId("pl-pending")!!.localVersion, db.purchaseLineDao().findByLocalId("pl-rejected")!!.localVersion,
                    db.consumptionLineDao().findByLocalId("cl-rejected")!!.localVersion,
                ),
            )
            assertEquals(emptyList(), db.syncIssueActionDao().rowsAwaitingServerVersion())
            assertEquals(listOf<Any?>(SyncStatus.CONFLICTED, "REJECTED", 2.675), db.purchaseLineDao().findByLocalId("pl-rejected")!!.let { listOf(it.syncStatus, it.lastSyncError, it.quantity) })
            assertEquals(SyncIssueKind.DELETE_REFUSED, db.stageDao().findByLocalId("s-delete-refused")!!.syncIssue()?.kind)
            assertEquals(listOf("pl-pending", "pl-rejected"), db.purchaseLineDao().findPending().map { it.localId }.sorted())
            assertEquals(10.0, db.stockDao().findCounter("p-synced", 7)?.quantityIn)

            assertEquals(null, db.purchaseLineDao().survivorOfMergedMaterial("m-v14"), "the table of merged materials exists, empty")
            assertTrue(db.purchaseLineDao().writeIfUnchanged(db.purchaseLineDao().findByLocalId("pl-pending")!!.copy(quantity = 5.0)))
            db.purchaseLineDao().changeLocally("pl-pending") { it.copy(quantity = 6.0) }
            assertEquals(listOf<Any?>(6.0, 1L), db.purchaseLineDao().findByLocalId("pl-pending")!!.let { listOf(it.quantity, it.localVersion) })
        } finally {
            db.close()
        }
    }

    @Test
    fun the_exported_v16_schema_adds_only_the_local_version_the_awaited_note_and_the_table_of_merged_materials() {
        fun columnsByTable(version: Int): Map<String, Map<String, Pair<String, Boolean>>> =
            exportedSchema(version)["entities"]!!.jsonArray.associate { entity ->
                entity.jsonObject["tableName"]!!.jsonPrimitive.content to entity.jsonObject["fields"]!!.jsonArray.associate { field ->
                    val f = field.jsonObject
                    f["columnName"]!!.jsonPrimitive.content to (f["affinity"]!!.jsonPrimitive.content to (f["notNull"]?.jsonPrimitive?.content == "true"))
                }
            }
        val v15 = columnsByTable(15)
        val v16 = columnsByTable(16)
        val versioned = setOf("projects", "stages", "materials", "daily_entries", "purchase_lines", "consumption_lines", "attachments")

        assertEquals(v15.keys + "material_merges", v16.keys, "one table added, none removed")
        assertEquals(mapOf("mergedLocalId" to ("TEXT" to true), "keptLocalId" to ("TEXT" to true)), v16.getValue("material_merges"))
        val added = (v16 - "material_merges").flatMap { (table, columns) -> (columns.keys - v15.getValue(table).keys).map { table to it } }.toSet()
        assertEquals(versioned.map { it to "localVersion" }.toSet() + (versioned - "attachments").map { it to "awaitsServerVersion" }, added)
        added.forEach { (table, column) -> assertEquals("INTEGER" to true, v16.getValue(table).getValue(column), "$table.$column is a non-null INTEGER") }
        v15.forEach { (table, columns) -> columns.forEach { (name, type) -> assertEquals(type, v16.getValue(table).getValue(name), "$table.$name unchanged") } }
    }
}

private fun seedARealV14Database(c: androidx.sqlite.SQLiteConnection) {
    createFromExportedSchema(c, version = 14)
    c.execSQL(
        "INSERT INTO projects (localId, serverId, name, currency, timezone, status, syncStatus, pendingOp, locallyModifiedAt, lastSyncError) VALUES " +
            "('p-synced', 42, 'Chantier v14', 'EUR', 'Europe/Paris', 'IN_PROGRESS', 'SYNCED', 'NONE', 1000, NULL), " +
            "('p-over-limit', NULL, 'Au-delà de la limite', 'EUR', 'Europe/Paris', 'IN_PROGRESS', 'CONFLICTED', 'CREATE', 1000, 'PLAN_LIMIT')",
    )
    c.execSQL(
        "INSERT INTO stages (localId, serverId, projectLocalId, name, status, syncStatus, pendingOp, locallyModifiedAt, lastSyncError) VALUES " +
            "('s-synced', 43, 'p-synced', 'Gros œuvre', 'IN_PROGRESS', 'SYNCED', 'NONE', 1000, NULL), " +
            "('s-edit-refused', 44, 'p-synced', 'Charpente', 'IN_PROGRESS', 'CONFLICTED', 'UPDATE', 1000, 'REJECTED'), " +
            "('s-delete-refused', 45, 'p-synced', 'Toiture', 'IN_PROGRESS', 'SYNCED', 'NONE', 1000, 'REJECTED')",
    )
    c.execSQL("INSERT INTO daily_logs (localId, serverId, stageLocalId, date, locallyCreatedAt) VALUES ('l-v14', 46, 's-synced', '2026-10-01', 1000)")
    c.execSQL(
        "INSERT INTO daily_entries (localId, serverId, dailyLogLocalId, type, syncStatus, pendingOp, locallyModifiedAt, lastSyncError) VALUES " +
            "('e-synced', 47, 'l-v14', 'PURCHASE', 'SYNCED', 'NONE', 1000, NULL), " +
            "('e-gone', 48, 'l-v14', 'WORK', 'CONFLICTED', 'UPDATE', 1000, 'DELETED_ON_SERVER')",
    )
    c.execSQL(
        "INSERT INTO materials (localId, serverId, projectLocalId, name, unit, syncStatus, pendingOp, locallyModifiedAt) VALUES " +
            "('m-v14', 7, 'p-synced', 'Ciment', 'sac', 'SYNCED', 'NONE', 1000)",
    )
    c.execSQL(
        "INSERT INTO purchase_lines (localId, serverId, entryLocalId, materialLocalId, quantity, unitPrice, totalPrice, syncStatus, pendingOp, " +
            "locallyModifiedAt, lastSyncError, serverQuantity) VALUES " +
            "('pl-synced', 60, 'e-synced', 'm-v14', 10.0, 2.0, 20.0, 'SYNCED', 'NONE', 1000, NULL, 10.0), " +
            "('pl-pending', NULL, 'e-synced', 'm-v14', 4.0, 2.0, 8.0, 'PENDING', 'CREATE', 1000, NULL, NULL), " +
            "('pl-rejected', NULL, 'e-synced', 'm-v14', 2.675, 2.0, 5.35, 'CONFLICTED', 'CREATE', 1000, 'REJECTED', NULL)",
    )
    c.execSQL(
        "INSERT INTO consumption_lines (localId, serverId, entryLocalId, materialLocalId, quantity, syncStatus, pendingOp, locallyModifiedAt, lastSyncError, serverQuantity) VALUES " +
            "('cl-rejected', NULL, 'e-gone', 'm-v14', 99.0, 'CONFLICTED', 'CREATE', 1000, 'REJECTED', NULL)",
    )
    c.execSQL(
        "INSERT INTO attachments (localId, serverId, entryLocalId, localPath, originalName, mimeType, sizeBytes, uploadedAt, syncStatus, pendingOp, " +
            "locallyModifiedAt, lastSyncError) VALUES " +
            "('a-rejected', NULL, 'e-synced', 'ticket.jpg', 'ticket.jpg', 'image/jpeg', 1024, 1000, 'CONFLICTED', 'CREATE', 1000, 'REJECTED')",
    )
    c.execSQL("INSERT INTO material_stock (projectLocalId, materialServerId, quantityIn, quantityOut) VALUES ('p-synced', 7, 10.0, 0.0)")
    c.execSQL("INSERT INTO stock_snapshots (projectLocalId, refreshedAt, needsRefresh) VALUES ('p-synced', 2000, 0)")
}

private fun exportedSchema(version: Int): kotlinx.serialization.json.JsonObject {
    val relative = "schemas/com.dmb.chantiertracker.data.local.db.AppDatabase/$version.json"
    val file = listOf(java.io.File(relative), java.io.File("shared/$relative")).first { it.exists() }
    return Json.parseToJsonElement(file.readText()).jsonObject["database"]!!.jsonObject
}

private fun createFromExportedSchema(c: androidx.sqlite.SQLiteConnection, version: Int) {
    val schema = exportedSchema(version)
    schema["entities"]!!.jsonArray.forEach { entity ->
        val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
        c.execSQL(entity.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
        entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
            c.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
        }
    }
    schema["setupQueries"]!!.jsonArray.forEach { c.execSQL(it.jsonPrimitive.content) }
    c.execSQL("PRAGMA user_version = $version")
}

/** The full v7 table set — shared by the v7→v8 and v8→v9 migration tests. */
private fun seedV7Schema(c: androidx.sqlite.SQLiteConnection) {
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
    c.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_logs_stageLocalId_date` ON `daily_logs` (`stageLocalId`, `date`)")
    c.execSQL(
        "CREATE TABLE IF NOT EXISTS `daily_entries` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
            "`dailyLogLocalId` TEXT NOT NULL, `type` TEXT NOT NULL, `summary` TEXT, `createdById` INTEGER, " +
            "`createdAt` TEXT, `modifiedById` INTEGER, `modifiedAt` TEXT, `syncStatus` TEXT NOT NULL, " +
            "`pendingOp` TEXT NOT NULL, `locallyModifiedAt` INTEGER NOT NULL, `lastSyncedAt` INTEGER, " +
            "`remoteUpdatedAt` INTEGER, `lastSyncError` TEXT, PRIMARY KEY(`localId`), " +
            "FOREIGN KEY(`dailyLogLocalId`) REFERENCES `daily_logs`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    )
    c.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_entries_dailyLogLocalId` ON `daily_entries` (`dailyLogLocalId`)")
    c.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_entries_dailyLogLocalId_type` ON `daily_entries` (`dailyLogLocalId`, `type`)")
    c.execSQL(
        "CREATE TABLE IF NOT EXISTS `materials` (`localId` TEXT NOT NULL, `serverId` INTEGER, " +
            "`projectLocalId` TEXT NOT NULL, `name` TEXT NOT NULL, `unit` TEXT NOT NULL, `syncStatus` TEXT NOT NULL, " +
            "`pendingOp` TEXT NOT NULL, `locallyModifiedAt` INTEGER NOT NULL, `lastSyncedAt` INTEGER, " +
            "`remoteUpdatedAt` INTEGER, `lastSyncError` TEXT, PRIMARY KEY(`localId`), " +
            "FOREIGN KEY(`projectLocalId`) REFERENCES `projects`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    )
    c.execSQL("CREATE INDEX IF NOT EXISTS `index_materials_projectLocalId` ON `materials` (`projectLocalId`)")
    c.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_materials_projectLocalId_name` ON `materials` (`projectLocalId`, `name`)")
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
}
