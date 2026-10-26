package com.dmb.chantiertracker.data.sync

import com.dmb.chantiertracker.data.remote.dto.PageDto
import com.dmb.chantiertracker.domain.model.DomainException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadAllPagesTest {

    private class Server(var ids: List<Long>) {
        val requestedPages = mutableListOf<Pair<Int, Int>>()
        var beforePage: (Int) -> Unit = {}

        fun page(page: Int, size: Int): PageDto<Long> {
            beforePage(page)
            requestedPages += page to size
            return PageDto(ids.sorted().drop(page * size).take(size), ids.size.toLong())
        }
    }

    private suspend fun read(server: Server, pageSize: Int = 100, maxPages: Int = 200) =
        readAllPages<Long>(idOf = { it }, pageSize = pageSize, maxPages = maxPages) { page, size -> server.page(page, size) }

    @Test
    fun an_empty_list_is_a_complete_read_in_one_request() = runTest {
        val server = Server(emptyList())

        val read = read(server)

        assertTrue(read.isComplete)
        assertEquals(emptyList(), read.items)
        assertEquals(listOf(0 to 100), server.requestedPages)
    }

    @Test
    fun a_partial_first_page_is_a_complete_read_in_one_request() = runTest {
        val server = Server((1L..37L).toList())

        val read = read(server)

        assertNull(read.incompleteReason)
        assertEquals((1L..37L).toList(), read.items)
        assertEquals(1, server.requestedPages.size)
    }

    @Test
    fun exactly_one_full_page_costs_one_more_empty_request() = runTest {
        val server = Server((1L..100L).toList())

        val read = read(server)

        assertTrue(read.isComplete)
        assertEquals(100, read.items.size)
        assertEquals(listOf(0 to 100, 1 to 100), server.requestedPages)
    }

    @Test
    fun every_page_is_read_until_a_partial_one() = runTest {
        val server = Server((1L..250L).toList())

        val read = read(server)

        assertTrue(read.isComplete)
        assertEquals((1L..250L).toList(), read.items)
        assertEquals(listOf(0, 1, 2), server.requestedPages.map { it.first })
    }

    @Test
    fun an_error_on_the_first_page_is_thrown() = runTest {
        assertFailsWith<DomainException.Network> {
            readAllPages<Long>(idOf = { it }) { _, _ -> throw DomainException.Network }
        }
    }

    @Test
    fun an_error_on_a_later_page_keeps_what_was_read_and_is_incomplete() = runTest {
        val server = Server((1L..250L).toList())
        server.beforePage = { if (it == 1) throw DomainException.Network }

        val read = read(server)

        assertEquals(IncompleteReadReason.ERROR, read.incompleteReason)
        assertEquals((1L..100L).toList(), read.items)
    }

    @Test
    fun a_total_that_differs_from_what_was_read_is_incomplete() = runTest {
        val read = readAllPages<Long>(idOf = { it }) { _, _ -> PageDto(listOf(1L, 2L), totalElements = 3) }

        assertEquals(IncompleteReadReason.TOTAL_MISMATCH, read.incompleteReason)
        assertEquals(listOf(1L, 2L), read.items)
    }

    @Test
    fun a_total_that_changes_between_two_pages_is_incomplete() = runTest {
        val server = Server((1L..150L).toList())
        server.beforePage = { if (it == 1) server.ids = server.ids - 1L }

        val read = read(server)

        assertEquals(IncompleteReadReason.TOTAL_MISMATCH, read.incompleteReason)
    }

    @Test
    fun a_page_without_a_total_is_incomplete() = runTest {
        val read = readAllPages<Long>(idOf = { it }) { _, _ -> PageDto(listOf(1L, 2L)) }

        assertEquals(IncompleteReadReason.TOTAL_MISMATCH, read.incompleteReason)
    }

    @Test
    fun the_same_id_read_twice_is_incomplete_and_kept_once() = runTest {
        val read = readAllPages<Long>(idOf = { it }, pageSize = 2) { page, _ ->
            if (page == 0) PageDto(listOf(1L, 2L), totalElements = 3) else PageDto(listOf(2L), totalElements = 3)
        }

        assertEquals(IncompleteReadReason.DUPLICATE, read.incompleteReason)
        assertEquals(listOf(1L, 2L), read.items)
    }

    @Test
    fun the_page_cap_stops_an_endless_list() = runTest {
        var requests = 0
        val read = readAllPages<Long>(idOf = { it }, pageSize = 2, maxPages = 5) { page, _ ->
            requests++
            PageDto(listOf(page * 2L, page * 2L + 1), totalElements = 1_000_000)
        }

        assertEquals(IncompleteReadReason.PAGE_CAP, read.incompleteReason)
        assertEquals(5, requests)
        assertEquals(10, read.items.size)
    }
}
