package com.dmb.chantiertracker.data.local

import android.content.Context

class AndroidAppPreferences(context: Context) : AppPreferences {

    private val prefs =
        context.applicationContext.getSharedPreferences("chantiertracker_prefs", Context.MODE_PRIVATE)

    override fun read(key: String): String? = prefs.getString(key, null)

    override fun write(key: String, value: String?) {
        prefs.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }
}
