package com.dmb.chantiertracker

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform