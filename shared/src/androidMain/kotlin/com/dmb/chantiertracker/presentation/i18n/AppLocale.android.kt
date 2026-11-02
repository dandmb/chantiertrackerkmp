package com.dmb.chantiertracker.presentation.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

actual object LocalAppLocale {

    actual val current: String
        @Composable
        get() = Locale.getDefault().toString()

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val new = if (value == null) systemOrPerAppLocale(context) else Locale(value)
        Locale.setDefault(new)
        configuration.setLocale(new)
        val resources = context.resources
        resources.updateConfiguration(configuration, resources.displayMetrics)
        return LocalConfiguration.provides(configuration)
    }

    private fun systemOrPerAppLocale(context: Context): Locale {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perApp = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (perApp != null && !perApp.isEmpty) return perApp[0]
        }
        return Resources.getSystem().configuration.locales[0]
    }
}
