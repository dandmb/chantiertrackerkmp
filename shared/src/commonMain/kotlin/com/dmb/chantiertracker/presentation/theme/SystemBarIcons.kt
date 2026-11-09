package com.dmb.chantiertracker.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

fun systemBarIconsAreLight(showingSplash: Boolean, background: Color): Boolean =
    showingSplash || background.luminance() < 0.5f

@Composable
expect fun SystemBarIcons(light: Boolean)
