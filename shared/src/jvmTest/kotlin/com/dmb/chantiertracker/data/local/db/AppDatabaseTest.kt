package com.dmb.chantiertracker.data.local.db

import androidx.room.Room
import com.dmb.chantiertracker.support.verifyAttachmentDaoContract
import com.dmb.chantiertracker.support.verifyDailyLogDaoContract
import com.dmb.chantiertracker.support.verifyInvitationDaoContract
import com.dmb.chantiertracker.support.verifyMaterialAndLineDaoContract
import com.dmb.chantiertracker.support.verifyPlanUsageDaoContract
import com.dmb.chantiertracker.support.verifyProjectDaoContract
import com.dmb.chantiertracker.support.verifyStageDaoContract
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AppDatabaseTest {

    @Test
    fun project_dao_contract_holds_on_jvm() = runTest {
        val db = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
        try {
            verifyProjectDaoContract(db)
        } finally {
            db.close()
        }
    }

    @Test
    fun stage_dao_contract_holds_on_jvm() = runTest {
        val db = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
        try {
            verifyStageDaoContract(db)
        } finally {
            db.close()
        }
    }

    @Test
    fun plan_usage_dao_contract_holds_on_jvm() = runTest {
        val db = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
        try {
            verifyPlanUsageDaoContract(db)
        } finally {
            db.close()
        }
    }

    @Test
    fun daily_log_dao_contract_holds_on_jvm() = runTest {
        val db = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
        try {
            verifyDailyLogDaoContract(db)
        } finally {
            db.close()
        }
    }

    @Test
    fun material_and_line_dao_contract_holds_on_jvm() = runTest {
        val db = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
        try {
            verifyMaterialAndLineDaoContract(db)
        } finally {
            db.close()
        }
    }

    @Test
    fun attachment_dao_contract_holds_on_jvm() = runTest {
        val db = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
        try {
            verifyAttachmentDaoContract(db)
        } finally {
            db.close()
        }
    }

    @Test
    fun invitation_dao_contract_holds_on_jvm() = runTest {
        val db = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
        try {
            verifyInvitationDaoContract(db)
        } finally {
            db.close()
        }
    }
}
