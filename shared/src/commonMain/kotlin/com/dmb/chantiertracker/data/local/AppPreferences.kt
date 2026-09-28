package com.dmb.chantiertracker.data.local

// Small, synchronous key/value store for user preferences that must be
// available before the first frame (language, theme). Same per-platform
// backends as [OnboardingStore] (SharedPreferences / NSUserDefaults / a config
// file) — reads are cheap local lookups, writes are fire-and-forget.
interface AppPreferences {
    fun read(key: String): String?
    fun write(key: String, value: String?)
}
