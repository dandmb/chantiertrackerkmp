package com.dmb.chantiertracker.presentation.i18n

import org.jetbrains.compose.resources.pluralStringResource
import com.dmb.chantiertracker.resources.wait_seconds
import com.dmb.chantiertracker.resources.error_wait_rate_limited
import com.dmb.chantiertracker.resources.error_wait_in_progress
import com.dmb.chantiertracker.resources.error_wait_locked
import com.dmb.chantiertracker.resources.error_wait_over
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import com.dmb.chantiertracker.domain.model.DeletionBlocker
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.presentation.billing.paidPlansAreOffered
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.error_account_locked
import com.dmb.chantiertracker.resources.error_billing_not_open
import com.dmb.chantiertracker.resources.error_creation_in_progress
import com.dmb.chantiertracker.resources.error_deletion_last_super_admin
import com.dmb.chantiertracker.resources.error_deletion_subscription_renewing
import com.dmb.chantiertracker.resources.error_deletion_subscription_renewing_outside_app
import com.dmb.chantiertracker.presentation.billing.LocalInAppPaymentsAllowed
import com.dmb.chantiertracker.resources.error_account_not_verified
import com.dmb.chantiertracker.resources.error_duplicate_material
import com.dmb.chantiertracker.resources.error_project_export_empty
import com.dmb.chantiertracker.resources.error_email_already_used
import com.dmb.chantiertracker.resources.error_file_too_large
import com.dmb.chantiertracker.resources.error_image_too_large
import com.dmb.chantiertracker.resources.error_forbidden
import com.dmb.chantiertracker.resources.error_last_admin_removal
import com.dmb.chantiertracker.resources.error_invalid_code
import com.dmb.chantiertracker.resources.error_invalid_current_password
import com.dmb.chantiertracker.resources.error_new_password_same_as_current
import com.dmb.chantiertracker.resources.error_invalid_credentials
import com.dmb.chantiertracker.resources.error_network
import com.dmb.chantiertracker.resources.error_not_found
import com.dmb.chantiertracker.resources.error_plan_limit
import com.dmb.chantiertracker.resources.error_plan_limit_no_upgrade
import com.dmb.chantiertracker.resources.error_rate_limited
import com.dmb.chantiertracker.resources.error_service_unavailable
import com.dmb.chantiertracker.resources.error_unexpected
import com.dmb.chantiertracker.resources.error_validation
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

fun DomainException.textRes(): StringResource = when (this) {
    DomainException.InvalidCredentials -> Res.string.error_invalid_credentials
    DomainException.EmailAlreadyUsed -> Res.string.error_email_already_used
    DomainException.AccountNotVerified -> Res.string.error_account_not_verified
    is DomainException.AccountLocked -> Res.string.error_account_locked
    // Never actually shown — caught internally by AuthRepositoryImpl to drive
    // AuthState.MustChangePassword before any screen could display it.
    DomainException.MustChangePassword -> Res.string.error_unexpected
    DomainException.InvalidCurrentPassword -> Res.string.error_invalid_current_password
    DomainException.NewPasswordSameAsCurrent -> Res.string.error_new_password_same_as_current
    DomainException.InvalidCode -> Res.string.error_invalid_code
    DomainException.Validation -> Res.string.error_validation
    is DomainException.InvalidProfile -> Res.string.error_validation
    is DomainException.RateLimited -> Res.string.error_rate_limited
    is DomainException.CreationInProgress -> Res.string.error_creation_in_progress
    is DomainException.AccountDeletionBlocked -> when (blocker) {
        DeletionBlocker.LAST_SUPER_ADMIN -> Res.string.error_deletion_last_super_admin
        DeletionBlocker.SUBSCRIPTION_STILL_RENEWING -> Res.string.error_deletion_subscription_renewing
    }
    DomainException.PlanLimitReached -> Res.string.error_plan_limit
    DomainException.DuplicateMaterial -> Res.string.error_duplicate_material
    DomainException.ProjectExportEmpty -> Res.string.error_project_export_empty
    DomainException.FileTooLarge -> Res.string.error_file_too_large
    DomainException.ImageTooLarge -> Res.string.error_image_too_large
    DomainException.BillingNotOpen -> Res.string.error_billing_not_open
    DomainException.Forbidden -> Res.string.error_forbidden
    DomainException.LastAdminRemoval -> Res.string.error_last_admin_removal
    DomainException.NotFound -> Res.string.error_not_found
    DomainException.Network -> Res.string.error_network
    DomainException.ServiceUnavailable -> Res.string.error_service_unavailable
    DomainException.Unexpected -> Res.string.error_unexpected
}

private const val SECONDS_PER_MINUTE = 60

fun DomainException.waitSeconds(): Int? = when (this) {
    is DomainException.RateLimited -> retryAfterSeconds
    is DomainException.CreationInProgress -> retryAfterSeconds
    is DomainException.AccountLocked -> retryAfterSeconds
    else -> null
}

@Composable
fun rememberWaitSecondsLeft(error: DomainException?): Int {
    var left by remember(error) { mutableIntStateOf(error?.waitSeconds() ?: 0) }
    LaunchedEffect(error) {
        while (left > 0) {
            delay(1_000L)
            left--
        }
    }
    return left
}

@Composable
private fun countdownText(seconds: Int): String =
    if (seconds < SECONDS_PER_MINUTE) {
        pluralStringResource(Res.plurals.wait_seconds, seconds, seconds)
    } else {
        "${seconds / SECONDS_PER_MINUTE}:${(seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
    }

@Composable
fun DomainException.localizedText(): String {
    if (this is DomainException.PlanLimitReached && !paidPlansAreOffered()) {
        return stringResource(Res.string.error_plan_limit_no_upgrade)
    }
    if (this is DomainException.AccountDeletionBlocked && blocker == DeletionBlocker.SUBSCRIPTION_STILL_RENEWING && !LocalInAppPaymentsAllowed.current) {
        return stringResource(Res.string.error_deletion_subscription_renewing_outside_app)
    }
    if (waitSeconds() == null) return stringResource(textRes())
    val left = rememberWaitSecondsLeft(this)
    if (left == 0) return stringResource(Res.string.error_wait_over)
    val waiting = when (this) {
        is DomainException.AccountLocked -> Res.string.error_wait_locked
        is DomainException.CreationInProgress -> Res.string.error_wait_in_progress
        else -> Res.string.error_wait_rate_limited
    }
    return stringResource(waiting, countdownText(left))
}
