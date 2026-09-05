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
