package com.dmb.chantiertracker.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncedRowWritesArchitectureTest {

    private val sourceRoot: File? = listOf(File("src"), File("shared/src")).firstOrNull { File(it, "commonMain").isDirectory }
    private val mainSources: List<File> = listOf("commonMain", "androidMain", "iosMain", "jvmMain")
        .mapNotNull { set -> sourceRoot?.let { File(it, set) } }.filter { it.isDirectory }
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

    private fun roomWritesOf(file: File): List<RoomWrite> = roomWritesIn(file.relative(), file.readText(), syncedDaos.values, syncedEntities)

    private val writesOfTheSyncedDaos: Map<String, Set<String>> = syncedDaos.keys.associateWith { dao ->
        val file = mainSources.singleOrNull { it.name == "$dao.kt" }
        (file?.let { roomWritesOf(it) } ?: emptyList()).filter { it.touchesSyncedRows }.map { it.function }.toSet() + setOf("changeLocally", "writeIfUnchanged", "deleteIfUnchanged", "keepLocalChange", "insertNew", "upsert", "deleteByLocalId")
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
        return Regex("""(?<!\w)(${receivers.keys.joinToString("|")})\s*\??\.\s*(\w+)\s*[({]""").findAll(code).mapNotNull { match ->
            val dao = receivers.getValue(Regex.escape(match.groupValues[1]))
            val function = match.groupValues[2]
            if (function !in writesOfTheSyncedDaos.getValue(dao)) return@mapNotNull null
            Call(file.relative(), code.substring(0, match.range.first).count { it == '\n' } + 1, dao, function)
        }.toList()
    }

    private val callsOutsideTheDatabaseLayer: List<Call> = mainSources.filter { dbPackage !in it.relative() }.flatMap { callsOnSyncedDaos(it) }

    private fun assertTheSourcesWereRead() {
        assertTrue(sourceRoot != null, "the sources are not where the guard looks for them (working directory: ${File("").absolutePath})")
        assertTrue(mainSources.size >= 100, "only ${mainSources.size} source files were read: a guard that reads nothing must not pass")
        val daoFiles = syncedDaos.keys.filter { dao -> mainSources.count { it.name == "$dao.kt" } == 1 }
        assertEquals(syncedDaos.keys.toList(), daoFiles, "every synced DAO is read, once")
        syncedDaos.keys.forEach { dao -> assertTrue("upsert" in writesOfTheSyncedDaos.getValue(dao), "$dao: its raw writes were not recognised") }
    }

    @Test
    fun the_guard_reads_the_real_sources_and_sees_the_writes_it_is_meant_to_judge() {
        assertTheSourcesWereRead()
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
        assertTheSourcesWereRead()
        val violations = callsOutsideTheDatabaseLayer.filter { it.file.startsWith("data/sync/") && it.function !in allowedFromTheSyncPass }
        assertEquals(emptyList(), violations.map { it.toString() }, "a row read before a network call may have changed: write it with $allowedFromTheSyncPass")
    }

    @Test
    fun everything_else_only_writes_a_synced_row_through_a_counted_local_change() {
        assertTheSourcesWereRead()
        val violations = callsOutsideTheDatabaseLayer.filter { !it.file.startsWith("data/sync/") && it.function !in allowedFromARepository }
        assertEquals(emptyList(), violations.map { it.toString() }, "a save of the user must be counted: write it with $allowedFromARepository")
    }

    @Test
    fun no_screen_or_view_model_holds_a_dao() {
        assertTheSourcesWereRead()
        val importsOfADao = mainSources.filter { it.relative().startsWith("presentation/") }.flatMap { file ->
            Regex("""import com\.dmb\.chantiertracker\.data\.local\.db\.(\w+)""").findAll(file.readText()).map { it.groupValues[1] }
                .filter { it.endsWith("Dao") || it == "AppDatabase" || it == "LocalSyncedWrites" }.map { "${file.relative()} imports $it" }.toList()
        }
        assertEquals(emptyList(), importsOfADao, "the presentation layer writes through its repositories")
    }

    @Test
    fun a_material_is_never_changed_locally_so_its_merge_has_no_local_change_to_lose() {
        assertTheSourcesWereRead()
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
        assertTheSourcesWereRead()
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
        assertTheSourcesWereRead()
        val actionWrites = mainSources.filter { it.relative() == "data/local/db/SyncIssueActionDao.kt" }.flatMap { roomWritesOf(it) }.filter { it.touchesSyncedRows }
        assertTrue(actionWrites.size >= 25, "the raw queries of SyncIssueActionDao are read: ${actionWrites.size}")
        val publicRawWrites = actionWrites.filter { "protected" !in it.modifiers }.map { it.function }
        assertEquals(emptyList(), publicRawWrites, "a raw query of SyncIssueActionDao is callable from outside its transactions")
    }

    private fun onASmallStack(block: () -> Unit) {
        var failure: Throwable? = null
        val thread = Thread(null, { try { block() } catch (e: Throwable) { failure = e } }, "guard-on-a-small-stack", 128 * 1024L)
        thread.start()
        thread.join()
        failure?.let { throw AssertionError("the analysis failed on a 128 KiB stack: $it", it) }
    }

    @Test
    fun a_dao_of_several_thousand_lines_is_analysed_on_a_small_stack_without_overflowing() {
        val longSql = (1..4_000).joinToString("\n") { "            OR (\"col$it\" = 'UPDATE not_a_table' AND note != ')' AND x = '(')" }
        val methods = (1..1_500).joinToString("\n\n") { index ->
            """
            |    @Query("UPDATE stages SET name = :name, note = '(' WHERE localId = :localId AND $index = $index")
            |    protected abstract suspend fun renameStage$index(localId: String, name: String)
            |
            |    @Query("SELECT * FROM stages WHERE localId = :localId AND note = 'DELETE FROM stages'")
            |    abstract suspend fun find$index(localId: String): StageEntity?
            """.trimMargin()
        }
        val tripleQuote = "\"\"\""
        val fakeDao = """
            |@Dao
            |abstract class HugeDao {
            |$methods
            |
            |    @Query(
            |        $tripleQuote
            |        DELETE FROM purchase_lines WHERE localId = :localId
            |$longSql
            |        $tripleQuote,
            |    )
            |    abstract suspend fun deleteWithAVeryLongQuery(localId: String)
            |
            |    @Upsert
            |    abstract suspend fun upsert(row: ConsumptionLineEntity)
            |
            |    @Upsert
            |    abstract suspend fun upsertMember(member: ProjectMemberEntity)
            |
            |    @Query("UPDATE plan_usage SET plan = :plan")
            |    abstract suspend fun setPlan(plan: String)
            |}
        """.trimMargin()
        assertTrue(fakeDao.lines().size > 10_000, "${fakeDao.lines().size} lines")

        onASmallStack {
            val writes = roomWritesIn("data/local/db/HugeDao.kt", fakeDao, syncedDaos.values, syncedEntities)

            assertEquals(1_504, writes.size, "1500 updates, the long delete, two upserts and one update of another table; no read is taken for a write")
            assertEquals(1_502, writes.count { it.touchesSyncedRows })
            assertEquals(setOf("upsertMember", "setPlan"), writes.filter { !it.touchesSyncedRows }.map { it.function }.toSet())
            assertEquals(listOf("protected", "abstract", "suspend"), writes.first { it.function == "renameStage1" }.modifiers)
            assertTrue(writes.any { it.function == "deleteWithAVeryLongQuery" && it.touchesSyncedRows }, "a query of several thousand lines is read to its end")
        }
    }

    @Test
    fun the_whole_analysis_of_the_real_sources_runs_on_a_small_stack() {
        assertTheSourcesWereRead()
        onASmallStack {
            val writes = mainSources.filter { dbPackage in it.relative() }.flatMap { roomWritesOf(it) }
            val calls = mainSources.filter { dbPackage !in it.relative() }.flatMap { callsOnSyncedDaos(it) }
            assertTrue(writes.count { it.touchesSyncedRows } >= 60 && calls.size >= 95, "${writes.size} raw writes, ${calls.size} calls")
        }
    }

    @Test
    fun the_reader_of_room_annotations_tells_writes_from_reads_whatever_their_layout() {
        val source = """
            |interface SampleDao {
            |    @Query("SELECT * FROM stages WHERE name = 'UPDATE stages'") // UPDATE stages in a comment
            |    suspend fun read(): List<StageEntity>
            |
            |    @Query(
            |        "UPDATE stages " +
            |            "SET name = :name"
            |    )
            |    @Suppress("unused")
            |    override suspend fun rename(name: String)
            |
            |    @Insert(onConflict = OnConflictStrategy.REPLACE)
            |    suspend fun insertAll(rows: List<PurchaseLineEntity>)
            |
            |    @Delete suspend fun remove(row: ProjectMemberEntity)
            |
            |    @Transaction
            |    suspend fun both() { read() }
            |
            |    @Query("delete from attachments") protected abstract suspend fun wipe()
            |}
        """.trimMargin()

        val writes = roomWritesIn("data/local/db/SampleDao.kt", source, syncedDaos.values, syncedEntities)

        assertEquals(listOf("rename" to true, "insertAll" to true, "remove" to false, "wipe" to true), writes.map { it.function to it.touchesSyncedRows })
        assertEquals(listOf("protected", "abstract", "suspend"), writes.last().modifiers)
    }
}

internal class RoomWrite(val file: String, val function: String, val touchesSyncedRows: Boolean, val modifiers: List<String>)

private val roomAnnotations = listOf("Upsert", "Insert", "Update", "Delete", "Query")
private val declarationModifiers = setOf("protected", "override", "abstract", "open", "suspend", "internal", "private", "public")

private fun String.isIdentifierPartAt(index: Int) = index in indices && (this[index].isLetterOrDigit() || this[index] == '_')

private fun String.endOfStringLiteral(start: Int): Int {
    if (startsWith("\"\"\"", start)) {
        val close = indexOf("\"\"\"", start + 3)
        var end = if (close < 0) length else close + 3
        while (end < length && this[end] == '"') end++
        return end
    }
    var index = start + 1
    while (index < length && this[index] != '"') index += if (this[index] == '\\') 2 else 1
    return minOf(index + 1, length)
}

private fun String.endOfParenthesised(open: Int): Int {
    var depth = 0
    var index = open
    while (index < length) {
        when (this[index]) {
            '"' -> { index = endOfStringLiteral(index); continue }
            '(' -> depth++
            ')' -> if (--depth == 0) return index + 1
        }
        index++
    }
    return length
}

private fun String.skipBlanksAndComments(start: Int): Int {
    var index = start
    while (index < length) {
        index = when {
            this[index].isWhitespace() -> index + 1
            startsWith("//", index) -> indexOf('\n', index).let { if (it < 0) length else it }
            startsWith("/*", index) -> indexOf("*/", index).let { if (it < 0) length else it + 2 }
            else -> return index
        }
    }
    return index
}

private fun String.wordAt(start: Int): String {
    var end = start
    while (isIdentifierPartAt(end)) end++
    return substring(start, end)
}

private fun String.stringLiteralsIn(from: Int, to: Int): String {
    val text = StringBuilder()
    var index = from
    while (index < to) {
        if (this[index] == '"') {
            val end = minOf(endOfStringLiteral(index), to)
            text.append(substring(index, end)).append(' ')
            index = end
        } else {
            index++
        }
    }
    return text.toString()
}

private fun writesInto(sql: String, tables: Collection<String>): Pair<Boolean, Boolean> {
    val words = sql.uppercase().split(Regex("[^A-Z0-9_]+")).filter { it.isNotEmpty() }
    val first = words.firstOrNull()
    val isWrite = first == "UPDATE" || first == "DELETE" || first == "INSERT" || first == "REPLACE"
    if (!isWrite) return false to false
    val target = when (first) {
        "UPDATE" -> words.drop(1).firstOrNull { it != "OR" && it !in setOf("ROLLBACK", "ABORT", "REPLACE", "FAIL", "IGNORE") }
        "DELETE" -> words.getOrNull(2)
        else -> words.dropWhile { it != "INTO" }.getOrNull(1)
    }
    return true to (target != null && tables.any { it.uppercase() == target })
}

internal fun roomWritesIn(file: String, source: String, syncedTables: Collection<String>, syncedEntities: Collection<String>): List<RoomWrite> {
    val writes = mutableListOf<RoomWrite>()
    var index = 0
    while (index < source.length) {
        val at = source.indexOf('@', index)
        if (at < 0) break
        val annotation = source.wordAt(at + 1)
        if (annotation !in roomAnnotations) {
            index = at + 1
            continue
        }
        var cursor = source.skipBlanksAndComments(at + 1 + annotation.length)
        var arguments = ""
        if (cursor < source.length && source[cursor] == '(') {
            val end = source.endOfParenthesised(cursor)
            arguments = source.stringLiteralsIn(cursor, end)
            cursor = end
        }
        val modifiers = mutableListOf<String>()
        var function: String? = null
        while (cursor < source.length) {
            cursor = source.skipBlanksAndComments(cursor)
            if (cursor >= source.length) break
            if (source[cursor] == '@') {
                val other = source.wordAt(cursor + 1)
                if (other in roomAnnotations) break
                cursor = source.skipBlanksAndComments(cursor + 1 + other.length)
                if (cursor < source.length && source[cursor] == '(') cursor = source.endOfParenthesised(cursor)
                continue
            }
            val word = source.wordAt(cursor)
            if (word == "fun") {
                val nameStart = source.skipBlanksAndComments(cursor + 3)
                function = source.wordAt(nameStart)
                cursor = nameStart + function.length
                break
            }
            if (word !in declarationModifiers) break
            modifiers += word
            cursor += word.length
        }
        if (function.isNullOrEmpty()) {
            index = maxOf(cursor, at + 1)
            continue
        }
        val open = source.indexOf('(', cursor)
        val close = if (open < 0) source.length else source.endOfParenthesised(open)
        val parameters = if (open < 0) "" else source.substring(open, close)
        val literals = arguments.replace("\"", " ")
        val (isWrite, touches) = if (annotation == "Query") writesInto(literals, syncedTables) else (true to syncedEntities.any { it in parameters })
        if (isWrite) writes += RoomWrite(file, function, touches, modifiers)
        index = close
    }
    return writes
}
