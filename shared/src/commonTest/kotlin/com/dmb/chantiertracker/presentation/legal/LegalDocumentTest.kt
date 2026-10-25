package com.dmb.chantiertracker.presentation.legal

import kotlin.test.Test
import kotlin.test.assertEquals

class LegalDocumentTest {

    @Test
    fun each_link_points_to_the_page_of_the_web_site_in_the_footer_order_of_the_web() {
        assertEquals(
            listOf(
                "https://chantiertracker.com/mentions-legales",
                "https://chantiertracker.com/cgu",
                "https://chantiertracker.com/cgv",
                "https://chantiertracker.com/confidentialite",
                "https://chantiertracker.com/cookies",
                "https://chantiertracker.com/suppression-compte",
            ),
            LegalDocument.entries.map { it.url },
        )
    }
}
