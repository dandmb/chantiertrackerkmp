package com.dmb.chantiertracker.presentation.admin

import androidx.compose.runtime.Composable
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.presentation.formatIsoDate
import com.dmb.chantiertracker.presentation.main.labelRes
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.admin_plan_change_free
import com.dmb.chantiertracker.resources.admin_plan_change_indefinite
import com.dmb.chantiertracker.resources.admin_plan_change_with_date
import org.jetbrains.compose.resources.stringResource

// Ported from the web's describePlanChange (EditAdminUserPlanDialog) — pure
// and unit-testable in isolation, same rationale the web comment gives.
// expiresAt is the raw ISO date (yyyy-MM-dd) from DateField, '' or null when
// left empty; ignored entirely once plan is FREE (mirrors the backend, which
// nulls planExpiresAt for FREE regardless of what's sent).
sealed class PlanChangeDescription {
    data class ToFree(val email: String) : PlanChangeDescription()
    data class WithExpiration(val email: String, val plan: Plan, val expiresAt: String) : PlanChangeDescription()
    data class Indefinite(val email: String, val plan: Plan) : PlanChangeDescription()
}

fun describePlanChange(email: String, plan: Plan, expiresAt: String?): PlanChangeDescription = when {
    plan == Plan.FREE -> PlanChangeDescription.ToFree(email)
    !expiresAt.isNullOrBlank() -> PlanChangeDescription.WithExpiration(email, plan, expiresAt)
    else -> PlanChangeDescription.Indefinite(email, plan)
}

@Composable
fun PlanChangeDescription.resolveText(): String = when (this) {
    is PlanChangeDescription.ToFree -> stringResource(Res.string.admin_plan_change_free, email)
    is PlanChangeDescription.WithExpiration ->
        stringResource(Res.string.admin_plan_change_with_date, email, stringResource(plan.labelRes()), formatIsoDate(expiresAt))
    is PlanChangeDescription.Indefinite ->
        stringResource(Res.string.admin_plan_change_indefinite, email, stringResource(plan.labelRes()))
}

// The backend field is a LocalDateTime (@Future-validated), not a bare date —
// end-of-day mirrors the web's `${date}T23:59:59` exactly (same rationale:
// a date picked "today" should still validate as in the future for almost
// the entire day). null when the plan is FREE or no date was picked —
// omitting the field entirely rather than sending a value the server ignores
// anyway (verified in UserService.grantPlanByAdmin).
fun resolvePlanExpiresAtArg(plan: Plan, date: String): String? =
    if (plan == Plan.FREE || date.isBlank()) null else "${date}T23:59:59"
