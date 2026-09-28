package com.dmb.chantiertracker.presentation.branding

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.app_icon
import org.jetbrains.compose.resources.painterResource

@Composable
fun appIconPainter(): Painter = painterResource(Res.drawable.app_icon)
