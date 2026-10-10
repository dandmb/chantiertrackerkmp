package com.dmb.chantiertracker.data.local.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@Database(
    entities = [
        ProjectEntity::class,
        ProjectMemberEntity::class,
        StageEntity::class,
        PlanUsageEntity::class,
        DailyLogEntity::class,
        DailyEntryEntity::class,
        MaterialEntity::class,
        PurchaseLineEntity::class,
        ConsumptionLineEntity::class,
        AttachmentEntity::class,
        InvitationEntity::class,
        EditorIdentityEntity::class,
        MaterialStockEntity::class,
        StockSnapshotEntity::class,
    ],
    version = 16,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun stageDao(): StageDao
    abstract fun planUsageDao(): PlanUsageDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun dailyEntryDao(): DailyEntryDao
    abstract fun materialDao(): MaterialDao
    abstract fun purchaseLineDao(): PurchaseLineDao
    abstract fun consumptionLineDao(): ConsumptionLineDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun invitationDao(): InvitationDao
    abstract fun editorIdentityDao(): EditorIdentityDao
    abstract fun localDataDao(): LocalDataDao
    abstract fun stockDao(): StockDao
    abstract fun materialAdoptionDao(): MaterialAdoptionDao
    abstract fun syncIssueDao(): SyncIssueDao
    abstract fun syncIssueActionDao(): SyncIssueActionDao
}

@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

fun RoomDatabase.Builder<AppDatabase>.buildChantierDatabase(): AppDatabase =
    setDriver(BundledSQLiteDriver())
        .addMigrations(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
            MIGRATION_14_15, MIGRATION_15_16,
        )
        .build()
