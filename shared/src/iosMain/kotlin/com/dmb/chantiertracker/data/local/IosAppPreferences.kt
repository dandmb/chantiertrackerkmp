package com.dmb.chantiertracker.data.local

import platform.Foundation.NSUserDefaults

class IosAppPreferences : AppPreferences {

    private val defaults = NSUserDefaults.standardUserDefaults

    override fun read(key: String): String? = defaults.stringForKey(prefixed(key))

    override fun write(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(prefixed(key)) else defaults.setObject(value, prefixed(key))
    }

    private fun prefixed(key: String) = "chantiertracker.$key"
}
