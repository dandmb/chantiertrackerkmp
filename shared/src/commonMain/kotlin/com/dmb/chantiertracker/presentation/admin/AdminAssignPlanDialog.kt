package com.dmb.chantiertracker.presentation.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.AdminUser
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.PlanSource
import com.dmb.chantiertracker.presentation.DateField
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.auth.plans.tabLabelRes
import com.dmb.chantiertracker.presentation.todayInSystemZone
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.action_cancel
import com.dmb.chantiertracker.resources.admin_plan_confirm
import com.dmb.chantiertracker.resources.admin_plan_dialog_title
import com.dmb.chantiertracker.resources.admin_plan_expires_help
import com.dmb.chantiertracker.resources.admin_plan_expires_help_free
import com.dmb.chantiertracker.resources.admin_plan_expires_label
import com.dmb.chantiertracker.resources.admin_plan_stripe_guard
import com.dmb.chantiertracker.resources.date_field_placeholder
import org.jetbrains.compose.resources.stringResource

@Composable
fun AssignPlanDialog(user: AdminUser, onDismiss: () -> Unit, onConfirm: (Plan, String?) -> Unit) {
    var plan by remember { mutableStateOf(user.plan) }
    var expiresAt by remember { mutableStateOf(user.planExpiresAt?.substringBefore('T').orEmpty()) }
    // Proactive guard (validated with the user, ADR-52 sous-étape 3/4): the
    // web only ever refuses this after the fact (a generic error once
    // submitted) — disabling the form up front is strictly better UX,
    // backed by the real 409 (StripeSubscriptionActiveException) as a
    // safety net for the race where planSource went stale after this row
    // loaded (remapped locally in AdminRepositoryImpl, see that file).
    val stripeActive = user.planSource == PlanSource.STRIPE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.admin_plan_dialog_title, user.email)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (stripeActive) {
                    ErrorBanner(stringResource(Res.string.admin_plan_stripe_guard))
                }
                PlanChoiceRow(
                    selected = plan,
                    enabled = !stripeActive,
                    onSelect = { selected ->
                        plan = selected
                        if (selected == Plan.FREE) expiresAt = ""
                    },
                )
                if (plan != Plan.FREE) {
                    DateField(
                        label = stringResource(Res.string.admin_plan_expires_label),
                        value = expiresAt,
                        onValueChange = { expiresAt = it },
                        minDate = todayInSystemZone(),
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = stringResource(Res.string.date_field_placeholder),
                        enabled = !stripeActive,
                        supportingText = { Text(stringResource(Res.string.admin_plan_expires_help)) },
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.admin_plan_expires_help_free),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = describePlanChange(user.email, plan, expiresAt).resolveText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(plan, resolvePlanExpiresAtArg(plan, expiresAt)) },
                enabled = !stripeActive,
            ) {
                Text(stringResource(Res.string.admin_plan_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

// tabLabelRes(), not the dialog's own Plan.labelRes(): three segments in the
// width of a dialog (narrower than PlanSelectionScreen's full-screen row)
// wrapped "Formule Semi-Flex" onto 3 uneven lines on a real phone — found by
// re-rendering this dialog at phone width (412dp) instead of the desktop-width
// snapshot harness, which had missed it (ADR-54 point 4/5).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanChoiceRow(selected: Plan, enabled: Boolean, onSelect: (Plan) -> Unit) {
    val options = listOf(Plan.FREE, Plan.SEMI_FLEX, Plan.LIBERTE)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(stringResource(option.tabLabelRes()), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
