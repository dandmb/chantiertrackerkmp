package com.dmb.chantiertracker.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncedRowWritesArchitectureTest {

    private val sourceRoot: File = listOf(File("src"), File("shared/src")).first { File(it, "commonMain").isDirectory }
    private val mainSources: List<File> = listOf("commonMain", "androidMain", "iosMain", "jvmMain")
        .map { File(sourceRoot, it) }.filter { it.isDirectory }
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
    private val dbPackage = "data/local/db/"

    private val syncedDaos = mapOf(
        "ProjectDao" to "projects", "StageDao" to "stages", "MaterialDao" to "materials", "DailyEntryDao" to "daily_entries",
        "PurchaseLineDao" to "purchase_lines", "ConsumptionLineDao" to "consumption_lines", "AttachmentDao" to "attachments",
    )
    private val syncedEntities = setOf("ProjectEntity", "StageEntity", "MaterialEntity", "DailyEntryEntity", "PurchaseLineEntity", "ConsumptionLineEntity", "AttachmentEntity")
    private val allowedFromTheSyncPass = setOf("writeIfUnchanged", "deleteIfUnchanged", "keepLocalChange", "insertNew")
    private val allowedFromARepository = setOf("changeLocally", "insertNew")

    private fun File.relative() = path.replace('\\', '/').substringAfter("/kotlin/com/dmb/chantiertracker/")

    private fun File.code(): String = readLines().joinToString("\n") { line -> line.substringBefore("//") }

    private class RoomWrite(val file: String, val function: String, val touchesSyncedRows: Boolean)

    private val roomWritePattern = Regex(
        """@(Upsert|Insert|Update|Delete|Query)\b\s*(\((?:[^"]|"(?:[^"\\]|\\.)*"|""${'"'}[\s\S]*?""${'"'})*?\))?\s*(?:(?:protected|override|abstract|open|suspend|internal|private)\s+)*fun\s+(\w+)\s*\(([^)]*)\)""",
    )

    private fun roomWritesOf(file: File): List<RoomWrite> = roomWritePattern.findAll(file.readText()).mapNotNull { match ->
        val (annotation, arguments, function, parameters) = match.destructured
        val sql = arguments.uppercase()
        val isWrite = annotation != "Query" || Regex("""\b(UPDATE|DELETE\s+FROM|INSERT\s+(OR\s+\w+\s+)?INTO|REPLACE\s+INTO)\b""").containsMatchIn(sql)
        if (!isWrite) return@mapNotNull null
        val touches = if (annotation == "Query") {
            syncedDaos.values.any { table -> Regex("""\b(UPDATE|DELETE\s+FROM|INTO)\s+`?$table\b""", RegexOption.IGNORE_CASE).containsMatchIn(arguments) }
        } else {
            syncedEntities.any { it in parameters }
        }
        RoomWrite(file.relative(), function, touches)
    }.toList()

    private val writesOfTheSyncedDaos: Map<String, Set<String>> = syncedDaos.keys.associateWith { dao ->
        val file = mainSources.single { it.name == "$dao.kt" }
        roomWritesOf(file).filter { it.touchesSyncedRows }.map { it.function }.toSet() + setOf("changeLocally", "writeIfUnchanged", "deleteIfUnchanged", "keepLocalChange", "insertNew", "upsert", "deleteByLocalId")
    }

    private class Call(val file: String, val line: Int, val dao: String, val function: String) {
        override fun toString() = "$file:$line $dao.$function"
    }

    private fun callsOnSyncedDaos(file: File): List<Call> {
        val code = file.code()
        val typedNames = Regex("""\b(\w+)\s*:\s*(${syncedDaos.keys.joinToString("|")})\b""").findAll(code).associate { it.groupValues[1] to it.groupValues[2] }
        val accessors = syncedDaos.keys.associateBy { it.replaceFirstChar(Char::lowercase) + "()" }
        val receivers = typedNames.mapKeys { Regex.escape(it.key) } + accessors.mapKeys { Regex.escape(it.key) }
        if (receivers.isEmpty()) return emptyList()
        return Regex("""(?<![\w.])(?:\w+\.)*?(${receivers.keys.joinToString("|")})\s*\??\.\s*(\w+)\s*[({]""").findAll(code).mapNotNull { match ->
            val dao = receivers.getValue(Regex.escape(match.groupValues[1]))
            val function = match.groupValues[2]
            if (function !in writesOfTheSyncedDaos.getValue(dao)) return@mapNotNull null
            Call(file.relative(), code.substring(0, match.range.first).count { it == '\n' } + 1, dao, function)
        }.toList()
    }

    private val callsOutsideTheDatabaseLayer: List<Call> = mainSources.filter { dbPackage !in it.relative() }.flatMap { callsOnSyncedDaos(it) }

    @Test
    fun the_guard_reads_the_real_sources_and_sees_the_writes_it_is_meant_to_judge() {
        assertEquals(7, writesOfTheSyncedDaos.size)
        writesOfTheSyncedDaos.forEach { (dao, writes) -> assertTrue("upsert" in writes && "deleteByLocalId" in writes, "$dao: $writes") }
        val bySource = callsOutsideTheDatabaseLayer.groupBy { it.file.substringBefore('/') + "/" + it.file.substringAfter('/').substringBefore('/') }
        assertTrue((bySource["data/sync"]?.size ?: 0) >= 80, "the sync pass writes are seen: ${bySource.mapValues { it.value.size }}")
        assertTrue((bySource["data/repository"]?.size ?: 0) >= 15, "the repository writes are seen: ${bySource.mapValues { it.value.size }}")
        assertEquals(
            syncedDaos.keys, callsOutsideTheDatabaseLayer.filter { it.file.startsWith("data/sync/") }.map { it.dao }.toSet(),
            "every synced table is written by the sync pass through a call this guard recognises",
        )
    }

    @Test
    fun the_sync_pass_only_writes_a_synced_row_through_the_conditional_writes() {
        val violations = callsOutsideTheDatabaseLayer.filter { it.file.startsWith("data/sync/") && it.function !in allowedFromTheSyncPass }
        assertEquals(emptyList(), violations.map { it.toString() }, "a row read before a network call may have changed: write it with $allowedFromTheSyncPass")
    }

    @Test
    fun everything_else_only_writes_a_synced_row_through_a_counted_local_change() {
        val violations = callsOutsideTheDatabaseLayer.filter { !it.file.startsWith("data/sync/") && it.function !in allowedFromARepository }
        assertEquals(emptyList(), violations.map { it.toString() }, "a save of the user must be counted: write it with $allowedFromARepository")
    }

    @Test
    fun no_screen_or_view_model_holds_a_dao() {
        val importsOfADao = mainSources.filter { it.relative().startsWith("presentation/") }.flatMap { file ->
            Regex("""import com\.dmb\.chantiertracker\.data\.local\.db\.(\w+)""").findAll(file.readText()).map { it.groupValues[1] }
                .filter { it.endsWith("Dao") || it == "AppDatabase" || it == "LocalSyncedWrites" }.map { "${file.relative()} imports $it" }.toList()
        }
        assertEquals(emptyList(), importsOfADao, "the presentation layer writes through its repositories")
    }

    @Test
    fun a_material_is_never_changed_locally_so_its_merge_has_no_local_change_to_lose() {
        val changes = callsOutsideTheDatabaseLayer.filter { it.dao == "MaterialDao" && it.function == "changeLocally" }
        assertEquals(emptyList(), changes.map { it.toString() }, "the day a material can be edited on the device, MaterialAdoptionDao.mergeInto must compare versions")
    }

    private val rawWritesAllowedInTheDatabaseLayer: Map<String, Pair<Set<String>, String>> = mapOf(
        "data/local/db/ProjectDao.kt" to (setOf("upsert", "upsertAll", "deleteByLocalId") to "raw writes behind LocalChangesDao"),
        "data/local/db/StageDao.kt" to (setOf("upsert", "upsertAll", "deleteByLocalId") to "raw writes behind LocalChangesDao"),
        "data/local/db/MaterialDao.kt" to (setOf("upsert", "deleteByLocalId") to "raw writes behind LocalChangesDao"),
        "data/local/db/DailyEntryDao.kt" to (setOf("upsert", "deleteByLocalId") to "raw writes behind LocalChangesDao"),
        "data/local/db/PurchaseLineDao.kt" to (setOf("upsert", "deleteByLocalId") to "raw writes behind LocalChangesDao"),
        "data/local/db/ConsumptionLineDao.kt" to (setOf("upsert", "deleteByLocalId") to "raw writes behind LocalChangesDao"),
        "data/local/db/AttachmentDao.kt" to (setOf("upsert", "deleteByLocalId") to "raw writes behind LocalChangesDao"),
        "data/local/db/StockDao.kt" to (
            setOf("upsertPurchaseLine", "upsertConsumptionLine", "deletePurchaseLine", "deleteConsumptionLine") to "protected, each used after a version comparison in the same transaction"
            ),
        "data/local/db/MaterialAdoptionDao.kt" to (
            setOf("movePurchaseLines", "moveConsumptionLines", "deleteMaterial", "upsertMaterial") to "protected, one transaction; a material has no local change"
            ),
        "data/local/db/LocalDataDao.kt" to (
            setOf("deleteAttachments", "deletePurchaseLines", "deleteConsumptionLines", "deleteEntries", "deleteMaterials", "deleteStages", "deleteProjects") to
                "erasing the account, under the sync lock"
            ),
    )
    private val wholeFilesOfUserActionsRunUnderTheSyncLock = setOf("data/local/db/SyncIssueActionDao.kt", "data/local/db/SyncIssueDao.kt")

    @Test
    fun every_raw_room_write_of_a_synced_table_is_a_known_one() {
        val found = mainSources.filter { dbPackage in it.relative() }.flatMap { roomWritesOf(it) }.filter { it.touchesSyncedRows }
        assertTrue(found.size >= 60, "the Room annotations are parsed: ${found.size}")
        val unknown = found.filter { it.file !in wholeFilesOfUserActionsRunUnderTheSyncLock }
            .filter { it.function !in (rawWritesAllowedInTheDatabaseLayer[it.file]?.first ?: emptySet()) }
        assertEquals(emptyList(), unknown.map { "${it.file} ${it.function}" }, "a new raw write of a synced table: make it conditional, or list it here with its reason")
        val gone = rawWritesAllowedInTheDatabaseLayer.flatMap { (file, allowed) -> allowed.first.map { file to it } }
            .filter { (file, function) -> found.none { it.file == file && it.function == function } }
        assertEquals(emptyList(), gone.map { "${it.first} ${it.second}" }, "the list of known raw writes names one that no longer exists")
    }

    @Test
    fun the_raw_writes_of_the_user_action_daos_are_only_reached_through_their_public_transactions() {
        val publicRawWrites = mainSources.filter { it.relative() == "data/local/db/SyncIssueActionDao.kt" }.flatMap { file ->
            Regex("""@Query\b[\s\S]*?\n\s*((?:\w+\s+)*)fun\s+(\w+)""").findAll(file.readText())
                .filter { "protected" !in it.groupValues[1] && Regex("""\b(UPDATE|DELETE FROM)\b""").containsMatchIn(it.value) }.map { it.groupValues[2] }.toList()
        }
        assertEquals(emptyList(), publicRawWrites, "a raw query of SyncIssueActionDao is callable from outside its transactions")
    }
}
