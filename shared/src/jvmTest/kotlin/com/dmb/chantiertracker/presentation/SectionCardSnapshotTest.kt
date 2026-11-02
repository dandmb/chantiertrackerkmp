package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.main.ConstructionIcon
import com.dmb.chantiertracker.presentation.main.EditIcon
import com.dmb.chantiertracker.presentation.main.ShoppingCartIcon
import com.dmb.chantiertracker.presentation.theme.AppTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SectionCardSnapshotTest {

    private val outDir = SnapshotSettings.outDir("build/auth-snapshots")

    private fun capture(dark: Boolean) = runComposeUiTest {
        setContent {
            AppTheme(darkTheme = dark) {
                Column(
                    Modifier.width(412.dp).background(MaterialTheme.colorScheme.background).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    AtTheSnapshotFontScale {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            SectionCard(
                                title = "Achats",
                                icon = ShoppingCartIcon,
                                tone = SectionTone.Yellow,
                                subtitle = "Aucun résumé.",
                                action = { SectionHeaderAction("Modifier", {}, icon = EditIcon) },
                            ) {
                                Row(Modifier.fillMaxWidth()) { SectionSubheading("Articles achetés", Modifier.weight(1f)) }
                                DetailEmptyHint("Aucun article pour l'instant.")
                                SectionSubheading("Justificatifs")
                                DashedEmptyHint("Aucune photo pour l'instant.")
                            }
                            SectionCard(
                                title = "Travaux",
                                icon = ConstructionIcon,
                                action = { SectionHeaderAction("Modifier", {}, icon = EditIcon) },
                            ) {
                                SectionPrimaryButton("Ajouter une entrée", {}, Modifier.fillMaxWidth())
                                AppOutlinedButton(onClick = {}) { androidx.compose.material3.Text("Ajouter une photo") }
                            }
                            SectionCard(title = "Invitations en attente", action = { SectionHeaderAction("Inviter un membre", {}) }) {
                                DetailInfoRow("Devise", "EUR")
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(outDir, "60-section-card-${if (dark) "dark" else "light"}.png"))
    }

    @Test
    fun capture_the_section_card_in_light_mode() = capture(dark = false)

    @Test
    fun capture_the_section_card_in_dark_mode() = capture(dark = true)
}
