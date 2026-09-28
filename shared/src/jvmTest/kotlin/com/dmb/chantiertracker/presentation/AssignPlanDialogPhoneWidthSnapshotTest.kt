package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.AdminUser
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.presentation.admin.AssignPlanDialog
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.theme.AppTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

// ADR-54 point 4/5: the shared dialogSnapshot() harness (MainScreensSnapshotTest)
// renders every dialog at the JVM test host's default 1024x768 scene — wide
// enough that AssignPlanDialog's 3-way plan selector never wrapped there,
// even though it did on a real phone (Plan.labelRes() — "Formule Semi-Flex" —
// crowded 3 segments in a dialog narrower than a full screen). This is a
// dedicated capture at an actual phone width (412dp, same as every other
// phone-shaped snapshot in this suite) so that regression can't hide behind
// the wide harness again.
@OptIn(ExperimentalTestApi::class)
class AssignPlanDialogPhoneWidthSnapshotTest {

    private val outDir = File("build/auth-snapshots").apply { mkdirs() }

    private fun user(plan: Plan) = AdminUser(
        id = 2, email = "amelie@chantier.dev", name = "Amélie Roy", active = true,
        globalRole = GlobalRole.USER, projectCount = 1, createdAt = "2026-08-15T09:00:00",
        plan = plan, planSource = null, planExpiresAt = null,
    )

    private fun phoneSnapshot(name: String, locale: String, plan: Plan) = runDesktopComposeUiTest(width = 412, height = 892) {
        setContent {
            customAppLocale = locale
            AppEnvironment {
                AppTheme {
                    Box(Modifier.size(412.dp, 892.dp)) {
                        AssignPlanDialog(user = user(plan), onDismiss = {}, onConfirm = { _, _ -> })
                    }
                }
            }
        }
        waitForIdle()
        ImageIO.write(
            onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(),
            "png",
            File(outDir, "$name-$locale.png"),
        )
    }

    @Test
    fun capture_plan_choice_row_at_phone_width_fr() = phoneSnapshot("54-assign-plan-dialog-phone-width", "fr", Plan.FREE)

    @Test
    fun capture_plan_choice_row_at_phone_width_en() = phoneSnapshot("54-assign-plan-dialog-phone-width", "en", Plan.FREE)

    // The checkmark icon only draws on the *selected* segment — Semi-Flex
    // selected is the tightest fit (its own text competing with the icon for
    // the same narrow segment), worth its own capture.
    @Test
    fun capture_plan_choice_row_at_phone_width_semi_flex_selected_fr() =
        phoneSnapshot("54b-assign-plan-dialog-phone-width-semi-flex-selected", "fr", Plan.SEMI_FLEX)
}
