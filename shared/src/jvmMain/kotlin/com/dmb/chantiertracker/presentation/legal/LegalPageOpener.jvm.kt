package com.dmb.chantiertracker.presentation.legal

import androidx.compose.runtime.Composable
import java.awt.Desktop
import java.net.URI

@Composable
actual fun platformLegalPageOpener(): LegalPageOpener = LegalPageOpener { url -> Desktop.getDesktop().browse(URI(url)) }
