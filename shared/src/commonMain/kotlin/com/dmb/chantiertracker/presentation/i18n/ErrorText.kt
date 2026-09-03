package com.dmb.chantiertracker.presentation.i18n

import androidx.compose.runtime.Composable
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.error_account_locked
import com.dmb.chantiertracker.resources.error_account_not_verified
import com.dmb.chantiertracker.resources.error_email_already_used
import com.dmb.chantiertracker.resources.error_forbidden
import com.dmb.chantiertracker.resources.error_invalid_code
import com.dmb.chantiertracker.resources.error_invalid_credentials
import com.dmb.chantiertracker.resources.error_network
import com.dmb.chantiertracker.resources.error_not_found
import com.dmb.chantiertracker.resources.error_plan_limit
import com.dmb.chantiertracker.resources.error_rate_limited
import com.dmb.chantiertracker.resources.error_unexpected
import com.dmb.chantiertracker.resources.error_validation
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

fun DomainException.textRes(): StringResource = when (this) {
    DomainException.InvalidCredentials -> Res.string.error_invalid_credentials
    DomainException.EmailAlreadyUsed -> Res.string.error_email_already_used
    DomainException.AccountNotVerified -> Res.string.error_account_not_verified
    DomainException.AccountLocked -> Res.string.error_account_locked
    DomainException.InvalidCode -> Res.string.error_invalid_code
    DomainException.Validation -> Res.string.error_validation
    DomainException.RateLimited -> Res.string.error_rate_limited
    DomainException.PlanLimitReached -> Res.string.error_plan_limit
    DomainException.Forbidden -> Res.string.error_forbidden
    DomainException.NotFound -> Res.string.error_not_found
    DomainException.Network -> Res.string.error_network
    DomainException.Unexpected -> Res.string.error_unexpected
}

@Composable
fun DomainException.localizedText(): String = stringResource(textRes())
