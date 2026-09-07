package com.dmb.chantiertracker.presentation.projects.history

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
import com.dmb.chantiertracker.domain.model.HistorySort
import com.dmb.chantiertracker.presentation.main.CheckIcon
import com.dmb.chantiertracker.presentation.main.SortIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.history_sort_action
import com.dmb.chantiertracker.resources.history_sort_label
import com.dmb.chantiertracker.resources.history_sort_newest
import com.dmb.chantiertracker.resources.history_sort_oldest
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Lives in the detail TopAppBar (rendered by MainScreen), same icon-button +
// dropdown pattern as ProjectSortControl — a global filter that stays reachable
// while the history list scrolls.
@Composable
fun HistorySortControl(current: HistorySort, onSelect: (HistorySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(SortIcon, contentDescription = stringResource(Res.string.history_sort_label))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        HistorySort.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(stringResource(historySortLabel(option))) },
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

fun historySortLabel(sort: HistorySort): StringResource = when (sort) {
    HistorySort.NEWEST_FIRST -> Res.string.history_sort_newest
    HistorySort.OLDEST_FIRST -> Res.string.history_sort_oldest
    HistorySort.BY_ACTION -> Res.string.history_sort_action
}
