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
