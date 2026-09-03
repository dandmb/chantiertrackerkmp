package com.dmb.chantiertracker.data.local.db

import androidx.room.Room
import com.dmb.chantiertracker.support.verifyProjectDaoContract
import com.dmb.chantiertracker.support.verifyStageDaoContract
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AppDatabaseTest {

    @Test
    fun project_dao_contract_holds_on_ios() = runTest {
        val db = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
        try {
            verifyProjectDaoContract(db)
        } finally {
            db.close()
        }
    }

    @Test
    fun stage_dao_contract_holds_on_ios() = runTest {
        val db = Room.inMemoryDatabaseBuilder<AppDatabase>().buildChantierDatabase()
        try {
            verifyStageDaoContract(db)
        } finally {
            db.close()
        }
    }
}
