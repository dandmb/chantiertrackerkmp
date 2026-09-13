package com.dmb.chantiertracker.presentation.admin

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.Granularity
import com.dmb.chantiertracker.presentation.main.CalendarIcon
import com.dmb.chantiertracker.presentation.main.CheckIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.admin_stats_granularity_day
import com.dmb.chantiertracker.resources.admin_stats_granularity_label
import com.dmb.chantiertracker.resources.admin_stats_granularity_month
import com.dmb.chantiertracker.resources.admin_stats_granularity_year
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Lives in the (tab-root) AppTopBar, same icon-button + dropdown pattern as
// HistorySortControl/ReportSortControl — a global filter that stays
// reachable while the stats screen scrolls.
@Composable
fun AdminStatsGranularityControl(current: Granularity, onSelect: (Granularity) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(CalendarIcon, contentDescription = stringResource(Res.string.admin_stats_granularity_label))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        Granularity.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(stringResource(granularityLabel(option))) },
                onClick = {
                    open = false
                    onSelect(option)
                },
                trailingIcon = {
                    if (option == current) {
                        Icon(
                            CheckIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
        }
    }
}

fun granularityLabel(granularity: Granularity): StringResource = when (granularity) {
    Granularity.DAY -> Res.string.admin_stats_granularity_day
    Granularity.MONTH -> Res.string.admin_stats_granularity_month
    Granularity.YEAR -> Res.string.admin_stats_granularity_year
}
