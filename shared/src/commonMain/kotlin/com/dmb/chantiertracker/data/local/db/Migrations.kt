package com.dmb.chantiertracker.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v1 → v2 : ajout de la table `stages` (étapes de chantier). Le `createSql` doit
 * rester identique à celui généré par Room dans `shared/schemas/…/2.json` — Room
 * valide le schéma au démarrage et lève une erreur au moindre écart.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `stages` (" +
                "`localId` TEXT NOT NULL, " +
                "`serverId` INTEGER, " +
                "`projectLocalId` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`description` TEXT, " +
                "`estimatedBudget` REAL, " +
                "`startDate` TEXT, " +
                "`endDate` TEXT, " +
                "`status` TEXT NOT NULL, " +
                "`syncStatus` TEXT NOT NULL, " +
                "`pendingOp` TEXT NOT NULL, " +
                "`locallyModifiedAt` INTEGER NOT NULL, " +
                "`lastSyncedAt` INTEGER, " +
                "`remoteUpdatedAt` INTEGER, " +
                "`lastSyncError` TEXT, " +
                "PRIMARY KEY(`localId`), " +
                "FOREIGN KEY(`projectLocalId`) REFERENCES `projects`(`localId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stages_projectLocalId` ON `stages` (`projectLocalId`)",
        )
    }
}

/**
 * v2 → v3 : ajout de la table `plan_usage` (une ligne, `id = 0`) — plan et limite
 * de projets du compte, dernière valeur connue, pour bloquer la création hors ligne
 * (ADR-25). `createSql` à garder identique à `shared/schemas/…/3.json`.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `plan_usage` (" +
                "`id` INTEGER NOT NULL, " +
                "`plan` TEXT NOT NULL, " +
                "`projectsLimit` INTEGER, " +
                "`refreshedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
    }
}

/**
 * v3 → v4 : ajout de `daily_logs` (une journée = un couple étape/date) et
 * `daily_entries` (une entrée ACHAT ou TRAVAUX par journée, FK vers
 * `daily_logs`). `createSql` à garder identique à `shared/schemas/…/4.json`.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_logs` (" +
                "`localId` TEXT NOT NULL, " +
                "`serverId` INTEGER, " +
                "`stageLocalId` TEXT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`locallyCreatedAt` INTEGER NOT NULL, " +
                "`lastSyncedAt` INTEGER, " +
                "PRIMARY KEY(`localId`), " +
                "FOREIGN KEY(`stageLocalId`) REFERENCES `stages`(`localId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_daily_logs_stageLocalId` ON `daily_logs` (`stageLocalId`)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_logs_stageLocalId_date` " +
                "ON `daily_logs` (`stageLocalId`, `date`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_entries` (" +
                "`localId` TEXT NOT NULL, " +
                "`serverId` INTEGER, " +
                "`dailyLogLocalId` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`summary` TEXT, " +
                "`createdById` INTEGER, " +
                "`createdAt` TEXT, " +
                "`modifiedById` INTEGER, " +
                "`modifiedAt` TEXT, " +
                "`syncStatus` TEXT NOT NULL, " +
                "`pendingOp` TEXT NOT NULL, " +
                "`locallyModifiedAt` INTEGER NOT NULL, " +
                "`lastSyncedAt` INTEGER, " +
                "`remoteUpdatedAt` INTEGER, " +
                "`lastSyncError` TEXT, " +
                "PRIMARY KEY(`localId`), " +
                "FOREIGN KEY(`dailyLogLocalId`) REFERENCES `daily_logs`(`localId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_daily_entries_dailyLogLocalId` ON `daily_entries` (`dailyLogLocalId`)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_entries_dailyLogLocalId_type` " +
                "ON `daily_entries` (`dailyLogLocalId`, `type`)",
        )
    }
}

/**
 * v4 → v5 : référentiel `materials` (projet), `purchase_lines` et
 * `consumption_lines` (entrée). Le stock disponible n'est **pas** stocké — il
 * se calcule depuis ces deux dernières tables (voir `MaterialStock` / ADR-28).
 * `createSql` à garder identique à `shared/schemas/…/5.json`.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `materials` (" +
                "`localId` TEXT NOT NULL, " +
                "`serverId` INTEGER, " +
                "`projectLocalId` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`unit` TEXT NOT NULL, " +
                "`syncStatus` TEXT NOT NULL, " +
                "`pendingOp` TEXT NOT NULL, " +
                "`locallyModifiedAt` INTEGER NOT NULL, " +
                "`lastSyncedAt` INTEGER, " +
                "`remoteUpdatedAt` INTEGER, " +
                "`lastSyncError` TEXT, " +
                "PRIMARY KEY(`localId`), " +
                "FOREIGN KEY(`projectLocalId`) REFERENCES `projects`(`localId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_materials_projectLocalId` ON `materials` (`projectLocalId`)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_materials_projectLocalId_name` " +
                "ON `materials` (`projectLocalId`, `name`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `purchase_lines` (" +
                "`localId` TEXT NOT NULL, " +
                "`serverId` INTEGER, " +
                "`entryLocalId` TEXT NOT NULL, " +
                "`materialLocalId` TEXT NOT NULL, " +
                "`quantity` REAL NOT NULL, " +
                "`unitPrice` REAL NOT NULL, " +
                "`totalPrice` REAL NOT NULL, " +
                "`supplier` TEXT, " +
                "`createdAt` TEXT, " +
                "`syncStatus` TEXT NOT NULL, " +
                "`pendingOp` TEXT NOT NULL, " +
                "`locallyModifiedAt` INTEGER NOT NULL, " +
                "`lastSyncedAt` INTEGER, " +
                "`remoteUpdatedAt` INTEGER, " +
                "`lastSyncError` TEXT, " +
                "PRIMARY KEY(`localId`), " +
                "FOREIGN KEY(`entryLocalId`) REFERENCES `daily_entries`(`localId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`materialLocalId`) REFERENCES `materials`(`localId`) " +
                "ON UPDATE NO ACTION ON DELETE NO ACTION )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_purchase_lines_entryLocalId` ON `purchase_lines` (`entryLocalId`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_purchase_lines_materialLocalId` ON `purchase_lines` (`materialLocalId`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `consumption_lines` (" +
                "`localId` TEXT NOT NULL, " +
                "`serverId` INTEGER, " +
                "`entryLocalId` TEXT NOT NULL, " +
                "`materialLocalId` TEXT NOT NULL, " +
                "`quantity` REAL NOT NULL, " +
                "`createdAt` TEXT, " +
                "`syncStatus` TEXT NOT NULL, " +
                "`pendingOp` TEXT NOT NULL, " +
                "`locallyModifiedAt` INTEGER NOT NULL, " +
                "`lastSyncedAt` INTEGER, " +
                "`remoteUpdatedAt` INTEGER, " +
                "`lastSyncError` TEXT, " +
                "PRIMARY KEY(`localId`), " +
                "FOREIGN KEY(`entryLocalId`) REFERENCES `daily_entries`(`localId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`materialLocalId`) REFERENCES `materials`(`localId`) " +
                "ON UPDATE NO ACTION ON DELETE NO ACTION )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_consumption_lines_entryLocalId` ON `consumption_lines` (`entryLocalId`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_consumption_lines_materialLocalId` ON `consumption_lines` (`materialLocalId`)",
        )
    }
}

/**
 * v5 → v6 : ajout de `attachments` (photos justificatives d'une entrée ACHAT).
 * `localPath` pointe vers une copie du fichier sous `FileKit.filesDir` — les
 * octets ne sont jamais stockés en base (voir ADR-29). `createSql` à garder
 * identique à `shared/schemas/…/6.json`.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `attachments` (" +
                "`localId` TEXT NOT NULL, " +
                "`serverId` INTEGER, " +
                "`entryLocalId` TEXT NOT NULL, " +
                "`localPath` TEXT NOT NULL, " +
                "`originalName` TEXT NOT NULL, " +
                "`mimeType` TEXT NOT NULL, " +
                "`sizeBytes` INTEGER NOT NULL, " +
                "`uploadedAt` INTEGER NOT NULL, " +
                "`syncStatus` TEXT NOT NULL, " +
                "`pendingOp` TEXT NOT NULL, " +
                "`locallyModifiedAt` INTEGER NOT NULL, " +
                "`lastSyncedAt` INTEGER, " +
                "`remoteUpdatedAt` INTEGER, " +
                "`lastSyncError` TEXT, " +
                "PRIMARY KEY(`localId`), " +
                "FOREIGN KEY(`entryLocalId`) REFERENCES `daily_entries`(`localId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_attachments_entryLocalId` ON `attachments` (`entryLocalId`)",
        )
    }
}

/**
 * v6 → v7 : ajout de `invitations` (cache lecture seule des invitations d'un
 * projet — jamais créées localement, voir ADR-32). `createSql` à garder
 * identique à `shared/schemas/…/7.json`.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `invitations` (" +
                "`id` INTEGER NOT NULL, " +
                "`projectLocalId` TEXT NOT NULL, " +
                "`email` TEXT NOT NULL, " +
                "`role` TEXT NOT NULL, " +
                "`invitedById` INTEGER, " +
                "`createdAt` TEXT, " +
                "`expiresAt` TEXT, " +
                "`status` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`projectLocalId`) REFERENCES `projects`(`localId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_invitations_projectLocalId` ON `invitations` (`projectLocalId`)",
        )
    }
}
