package com.dmb.chantiertracker.core

import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopInAppPaymentsTest {

    @Test
    fun the_desktop_app_keeps_its_payments() {
        assertTrue(inAppPaymentsAllowed)
    }
}
