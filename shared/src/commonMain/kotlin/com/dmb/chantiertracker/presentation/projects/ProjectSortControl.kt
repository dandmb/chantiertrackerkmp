package com.dmb.chantiertracker.presentation.projects

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
import com.dmb.chantiertracker.domain.model.ProjectSort
import com.dmb.chantiertracker.presentation.main.CheckIcon
import com.dmb.chantiertracker.presentation.main.SortIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.sort_button
import com.dmb.chantiertracker.resources.sort_newest_first
import com.dmb.chantiertracker.resources.sort_oldest_first
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProjectSortControl(current: ProjectSort, onSelect: (ProjectSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(SortIcon, contentDescription = stringResource(Res.string.sort_button))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        ProjectSortMenuItems(
            current = current,
            onSelect = {
                open = false
                onSelect(it)
            },
        )
    }
}

@Composable
fun ProjectSortMenuItems(current: ProjectSort, onSelect: (ProjectSort) -> Unit) {
    SortRow(ProjectSort.NEWEST_FIRST, Res.string.sort_newest_first, current, onSelect)
    SortRow(ProjectSort.OLDEST_FIRST, Res.string.sort_oldest_first, current, onSelect)
}

@Composable
private fun SortRow(
    option: ProjectSort,
    label: StringResource,
    current: ProjectSort,
    onSelect: (ProjectSort) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = { onSelect(option) },
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
