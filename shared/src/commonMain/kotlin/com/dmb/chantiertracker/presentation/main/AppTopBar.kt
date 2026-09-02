package com.dmb.chantiertracker.presentation.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    userName: String,
    email: String,
    plan: Plan?,
    onSubscription: () -> Unit,
    onLogout: () -> Unit,
    leadingActions: @Composable RowScope.() -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(text = stringResource(Res.string.app_name), fontWeight = FontWeight.SemiBold)
        },
        actions = {
            leadingActions()
            IconButton(onClick = { menuOpen = true }) {
                Icon(AccountIcon, contentDescription = stringResource(Res.string.menu_open))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                AccountMenuBody(
                    userName = userName,
                    email = email,
                    plan = plan,
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
fun AccountMenuBody(
    userName: String,
    email: String,
    plan: Plan?,
    onSubscription: () -> Unit,
    onLogout: () -> Unit,
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
                    text = stringResource(plan.labelRes()),
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

private fun Plan?.labelRes() = when (this) {
    Plan.FREE -> Res.string.plan_free
    Plan.SEMI_FLEX -> Res.string.plan_semi_flex
    Plan.LIBERTE -> Res.string.plan_liberte
    else -> Res.string.plan_unknown
}
