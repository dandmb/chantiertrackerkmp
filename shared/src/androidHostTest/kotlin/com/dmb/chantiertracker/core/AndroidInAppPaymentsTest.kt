package com.dmb.chantiertracker.core

import kotlin.test.Test
import kotlin.test.assertFalse

class AndroidInAppPaymentsTest {

    @Test
    fun the_android_app_never_offers_a_payment() {
        assertFalse(inAppPaymentsAllowed, "Google Play payments policy: no Stripe checkout, portal, price or plan offer on Android")
    }
}
