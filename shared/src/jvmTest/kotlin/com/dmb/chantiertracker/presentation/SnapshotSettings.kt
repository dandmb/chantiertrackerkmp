package com.dmb.chantiertracker.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import java.io.File

// Where the snapshot tests write, and at which font scale they render: by default build/ at
// 100 %; `-Dchantiertracker.snapshotDir=… -Dchantiertracker.snapshotFontScale=1.5` captures a set
// outside the repository, e.g. before and after a change of the charte.
object SnapshotSettings {
    fun outDir(default: String): File =
        (System.getProperty("chantiertracker.snapshotDir")?.let(::File) ?: File(default)).apply { mkdirs() }

    val fontScale: Float get() = System.getProperty("chantiertracker.snapshotFontScale")?.toFloatOrNull() ?: 1f
}

@Composable
fun AtTheSnapshotFontScale(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, SnapshotSettings.fontScale), content = content)
