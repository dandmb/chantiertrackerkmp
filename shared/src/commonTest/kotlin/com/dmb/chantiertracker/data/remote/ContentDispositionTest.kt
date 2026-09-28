package com.dmb.chantiertracker.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContentDispositionTest {

    @Test
    fun extracts_the_plain_quoted_filename() {
        val header = "attachment; filename=\"chantier-villa-vidal-2026-09-08.pdf\"; " +
            "filename*=UTF-8''chantier-villa-vidal-2026-09-08.pdf"
        assertEquals("chantier-villa-vidal-2026-09-08.pdf", parseContentDispositionFilename(header))
    }

    @Test
    fun order_of_the_two_variants_does_not_matter() {
        assertEquals(
            "a.pdf",
            parseContentDispositionFilename("attachment; filename*=UTF-8''a.pdf; filename=\"a.pdf\""),
        )
    }

    @Test
    fun null_missing_or_filename_star_only_returns_null() {
        assertNull(parseContentDispositionFilename(null))
        assertNull(parseContentDispositionFilename("attachment"))
        assertNull(parseContentDispositionFilename("attachment; filename*=UTF-8''only-the-star.pdf"))
    }

    @Test
    fun a_blank_filename_returns_null() {
        assertNull(parseContentDispositionFilename("attachment; filename=\"\""))
        assertNull(parseContentDispositionFilename("attachment; filename=\"   \""))
    }
}
