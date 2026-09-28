package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.AppPreferences

class FakeAppPreferences(initial: Map<String, String> = emptyMap()) : AppPreferences {
    val store = initial.toMutableMap()

    override fun read(key: String): String? = store[key]

    override fun write(key: String, value: String?) {
        if (value == null) store.remove(key) else store[key] = value
    }
}
