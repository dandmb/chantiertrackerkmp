package com.dmb.chantiertracker.presentation.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.branding.AppGlyph
import com.dmb.chantiertracker.presentation.theme.Ink
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.app_name
import com.dmb.chantiertracker.resources.splash_tagline
import org.jetbrains.compose.resources.stringResource

@Composable
fun SplashScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                imageVector = AppGlyph,
                contentDescription = null,
                modifier = Modifier.size(240.dp),
            )
            Text(
                text = stringResource(Res.string.app_name).uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Text(
                text = stringResource(Res.string.splash_tagline),
                modifier = Modifier.padding(top = 8.dp).widthIn(max = 320.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
