package com.dmb.chantiertracker.core

interface BuildInfo {
    val isDebug: Boolean
    val appVersion: String

    /**
     * True for the pre-production (staging) distribution build — same code and
     * same backend as production, distributed to testers via Firebase App
     * Distribution. Drives the permanent in-app "PRÉPROD" banner. Only the
     * Android `.staging` package sets this; every other build is production.
     */
    val isStaging: Boolean
}
