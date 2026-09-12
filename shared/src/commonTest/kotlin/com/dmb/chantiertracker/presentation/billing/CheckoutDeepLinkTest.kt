package com.dmb.chantiertracker.presentation.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CheckoutDeepLinkTest {

    @Test
    fun resolves_the_three_known_destinations() {
        assertEquals(CheckoutDeepLink.CheckoutSuccess, parseCheckoutDeepLink("chantiertracker://checkout-success"))
        assertEquals(CheckoutDeepLink.CheckoutCancelled, parseCheckoutDeepLink("chantiertracker://checkout-cancel"))
        assertEquals(CheckoutDeepLink.PortalReturn, parseCheckoutDeepLink("chantiertracker://billing-return"))
    }

    @Test
    fun is_case_insensitive_on_scheme_and_host() {
        assertEquals(CheckoutDeepLink.CheckoutSuccess, parseCheckoutDeepLink("ChantierTracker://Checkout-Success"))
    }

    @Test
    fun tolerates_a_trailing_slash_or_query_string_from_the_os_url_layer() {
        assertEquals(CheckoutDeepLink.CheckoutSuccess, parseCheckoutDeepLink("chantiertracker://checkout-success/"))
        assertEquals(
            CheckoutDeepLink.CheckoutSuccess,
            parseCheckoutDeepLink("chantiertracker://checkout-success?session_id=cs_test_abc"),
        )
    }

    @Test
    fun an_unrecognized_scheme_or_host_resolves_to_nothing_rather_than_throwing() {
        assertNull(parseCheckoutDeepLink("https://checkout-success"))
        assertNull(parseCheckoutDeepLink("chantiertracker://not-a-known-path"))
        assertNull(parseCheckoutDeepLink(""))
        assertNull(parseCheckoutDeepLink("not a url at all"))
    }
}
