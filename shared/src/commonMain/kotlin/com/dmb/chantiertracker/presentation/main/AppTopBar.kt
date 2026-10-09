package com.dmb.chantiertracker.presentation.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.app_name
import com.dmb.chantiertracker.resources.menu_logout
import com.dmb.chantiertracker.resources.menu_open
import com.dmb.chantiertracker.resources.menu_subscription
import com.dmb.chantiertracker.resources.plan_free
import com.dmb.chantiertracker.resources.plan_liberte
import com.dmb.chantiertracker.resources.plan_semi_flex
import com.dmb.chantiertracker.resources.plan_unknown
import com.dmb.chantiertracker.resources.plan_with_founder
import com.dmb.chantiertracker.resources.sync_issues_badge
import com.dmb.chantiertracker.resources.sync_issues_badge_overflow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    userName: String,
    email: String,
    plan: Plan?,
    onSubscription: () -> Unit,
    onLogout: () -> Unit,
    isFounder: Boolean = false,
    syncIssueCount: Int = 0,
    onOpenSyncIssues: () -> Unit = {},
    leadingActions: @Composable RowScope.() -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(text = stringResource(Res.string.app_name), fontWeight = FontWeight.SemiBold)
        },
        actions = {
            leadingActions()
            if (syncIssueCount > 0) {
                SyncIssuesBadge(count = syncIssueCount, onClick = onOpenSyncIssues)
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(AccountIcon, contentDescription = stringResource(Res.string.menu_open))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                AccountMenuBody(
                    userName = userName,
                    email = email,
                    plan = plan,
                    isFounder = isFounder,
                    onSubscription = {
                        menuOpen = false
                        onSubscription()
                    },
                    onLogout = {
                        menuOpen = false
                        onLogout()
                    },
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun SyncIssuesBadge(count: Int, onClick: () -> Unit) {
    val description = pluralStringResource(Res.plurals.sync_issues_badge, count, count)
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).semantics { contentDescription = description }) {
        BadgedBox(
            badge = {
                Badge(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Text(
                        text = if (count > 99) stringResource(Res.string.sync_issues_badge_overflow) else count.toString(),
                        modifier = Modifier.semantics { hideFromAccessibility() },
                    )
                }
            },
        ) {
            Icon(SyncIssuesIcon, contentDescription = null)
        }
    }
}

@Composable
fun AccountMenuBody(
    userName: String,
    email: String,
    plan: Plan?,
    onSubscription: () -> Unit,
    onLogout: () -> Unit,
    isFounder: Boolean = false,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = userName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (email.isNotBlank()) {
            Text(
                text = email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider()
    DropdownMenuItem(
        text = {
            Column {
                Text(stringResource(Res.string.menu_subscription))
                Text(
                    text = if (isFounder) {
                        stringResource(Res.string.plan_with_founder, stringResource(plan.labelRes()))
                    } else {
                        stringResource(plan.labelRes())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onClick = onSubscription,
    )
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.menu_logout)) },
        onClick = onLogout,
    )
}

internal fun Plan?.labelRes() = when (this) {
    Plan.FREE -> Res.string.plan_free
    Plan.SEMI_FLEX -> Res.string.plan_semi_flex
    Plan.LIBERTE -> Res.string.plan_liberte
    else -> Res.string.plan_unknown
}
