package com.dmb.chantiertracker.support

import androidx.room.useReaderConnection
import com.dmb.chantiertracker.data.local.db.AppDatabase

suspend fun AppDatabase.everyRow(): String = useReaderConnection { connection ->
    val tables = connection.usePrepared(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name <> 'room_master_table' ORDER BY name",
    ) { statement -> buildList { while (statement.step()) add(statement.getText(0)) } }
    val dump = mutableListOf<String>()
    for (table in tables) {
        val rows = connection.usePrepared("SELECT * FROM `$table`") { statement ->
            buildList {
                while (statement.step()) {
                    add((0 until statement.getColumnCount()).joinToString("|") { if (statement.isNull(it)) "∅" else statement.getText(it) })
                }
            }.sorted()
        }
        dump += "$table: ${rows.joinToString(" ; ")}"
    }
    dump.joinToString("\n")
}
