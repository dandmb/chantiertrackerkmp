package com.dmb.chantiertracker.presentation.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.dmb.chantiertracker.presentation.navigation.AdminStatsRoute
import com.dmb.chantiertracker.presentation.navigation.ProjectsRoute
import com.dmb.chantiertracker.presentation.navigation.SettingsRoute
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.nav_administration
import com.dmb.chantiertracker.resources.nav_projects
import com.dmb.chantiertracker.resources.nav_settings
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class MainTab(val icon: ImageVector, val label: StringResource) {
    Projects(ProjectsIcon, Res.string.nav_projects),
    // ADR-52 — never shown alongside Projects: a SUPER_ADMIN account
    // replaces it (see MainScreen.tabsFor), never adds a 3rd tab next to a
    // Projects tab it could never use.
    Administration(AdminIcon, Res.string.nav_administration),
    Settings(SettingsIcon, Res.string.nav_settings),
}

fun MainTab.route(): Any = when (this) {
    MainTab.Projects -> ProjectsRoute
    MainTab.Administration -> AdminStatsRoute
    MainTab.Settings -> SettingsRoute
}

@Composable
fun AppBottomBar(current: MainTab, tabs: List<MainTab>, onSelect: (MainTab) -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = tab == current,
                    onClick = { onSelect(tab) },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = { Text(stringResource(tab.label)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

// ADR-56 sous-étape 5/5 — replaces AppBottomBar at EXPANDED width (840dp+):
// a bottom bar stretched across a wide Desktop window leaves its tabs
// absurdly far apart (confirmed in the sub-step 3 audit screenshots), while
// a side rail is the standard Material 3 pattern at this width. Same
// MainTab/onSelect contract as AppBottomBar — MainScreen picks one or the
// other, never both. `RowScope` receiver: meant to sit directly beside the
// screen content as two children of the same outer Row, not wrapped in its
// own Row (so the content next to it can carry `Modifier.weight(1f)`).
@Composable
fun RowScope.AppNavigationRail(current: MainTab, tabs: List<MainTab>, onSelect: (MainTab) -> Unit) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
        Spacer(Modifier.weight(1f))
        tabs.forEach { tab ->
            NavigationRailItem(
                selected = tab == current,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
        Spacer(Modifier.weight(1f))
    }
    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
