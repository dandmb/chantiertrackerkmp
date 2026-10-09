package com.dmb.chantiertracker.data.sync

import com.dmb.chantiertracker.data.remote.dto.PageDto
import com.dmb.chantiertracker.domain.model.DomainException
import kotlinx.coroutines.CancellationException

enum class IncompleteReadReason { ERROR, DUPLICATE, TOTAL_MISMATCH, PAGE_CAP }

class PagedRead<T>(val items: List<T>, val incompleteReason: IncompleteReadReason?) {
    val isComplete: Boolean get() = incompleteReason == null
}

const val LIST_PAGE_SIZE = 100
const val LIST_MAX_PAGES = 200

suspend fun <T> readAllPages(
    idOf: (T) -> Long,
    pageSize: Int = LIST_PAGE_SIZE,
    maxPages: Int = LIST_MAX_PAGES,
    fetchPage: suspend (page: Int, size: Int) -> PageDto<T>,
): PagedRead<T> {
    val items = mutableListOf<T>()
    val totalsAnnounced = mutableSetOf<Long?>()
    var interruptedBy: IncompleteReadReason? = null
    var page = 0
    while (true) {
        if (page == maxPages) {
            interruptedBy = IncompleteReadReason.PAGE_CAP
            break
        }
        val fetched = try {
            fetchPage(page, pageSize)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DomainException) {
            if (page == 0) throw e
            interruptedBy = IncompleteReadReason.ERROR
            break
        }
        items += fetched.content
        totalsAnnounced += fetched.totalElements
        if (fetched.content.size < pageSize) break
        page++
    }
    val distinct = items.distinctBy(idOf)
    val incompleteReason = interruptedBy ?: when {
        distinct.size != items.size -> IncompleteReadReason.DUPLICATE
        totalsAnnounced.singleOrNull() != items.size.toLong() -> IncompleteReadReason.TOTAL_MISMATCH
        else -> null
    }
    return PagedRead(distinct, incompleteReason)
}
