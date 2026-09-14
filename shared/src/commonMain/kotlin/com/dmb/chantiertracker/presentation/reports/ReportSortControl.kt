package com.dmb.chantiertracker.presentation.reports

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
import com.dmb.chantiertracker.domain.model.ReportSort
import com.dmb.chantiertracker.presentation.main.CheckIcon
import com.dmb.chantiertracker.presentation.main.SortIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.history_sort_label
import com.dmb.chantiertracker.resources.report_sort_newest
import com.dmb.chantiertracker.resources.report_sort_oldest
import com.dmb.chantiertracker.resources.report_sort_unprocessed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Lives in the detail TopAppBar (rendered by MainScreen), same icon-button +
// dropdown pattern as HistorySortControl — a global filter that stays reachable
// while the report list scrolls. Reuses `history_sort_label` (« Trier ») for the
// icon's content description: pure a11y chrome, and the two controls are never
// on screen at once.
@Composable
fun ReportSortControl(current: ReportSort, onSelect: (ReportSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(SortIcon, contentDescription = stringResource(Res.string.history_sort_label))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        ReportSort.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(stringResource(reportSortLabel(option))) },
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

fun reportSortLabel(sort: ReportSort): StringResource = when (sort) {
    ReportSort.NEWEST_FIRST -> Res.string.report_sort_newest
    ReportSort.OLDEST_FIRST -> Res.string.report_sort_oldest
    ReportSort.UNPROCESSED_FIRST -> Res.string.report_sort_unprocessed
}
