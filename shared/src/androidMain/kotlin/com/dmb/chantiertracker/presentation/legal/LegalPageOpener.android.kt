package com.dmb.chantiertracker.presentation.legal

import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.dmb.chantiertracker.presentation.theme.Ink

@Composable
actual fun platformLegalPageOpener(): LegalPageOpener {
    val context = LocalContext.current
    return remember(context) {
        LegalPageOpener { url ->
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(Ink.toArgb()).build())
                .build()
                .launchUrl(context, Uri.parse(url))
        }
    }
}
