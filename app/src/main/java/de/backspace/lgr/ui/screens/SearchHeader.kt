// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Shared search/filter header used by the Barcodes, Items and Persons tabs so they all look and
// behave the same: a rounded search field, then a meta row with a "Filters" toggle (left, showing
// the active-filter count) and the result count (right), an optional row of removable active-filter
// chips, and a collapsible panel holding the tab's specific filter controls.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    searchTrailing: (@Composable () -> Unit)? = null,
    resultCount: Int? = null,
    hasFilters: Boolean = false,
    filtersExpanded: Boolean = false,
    onToggleFilters: () -> Unit = {},
    activeFilterCount: Int = 0,
    activeFilterChips: (@Composable FlowRowScope.() -> Unit)? = null,
    filterPanel: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                    searchTrailing?.invoke()
                }
            },
            supportingText = supportingText?.let { { Text(it) } },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = lgrTextFieldColors(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        )

        if (hasFilters || resultCount != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasFilters) {
                    FilterChip(
                        selected = activeFilterCount > 0 || filtersExpanded,
                        onClick = onToggleFilters,
                        label = {
                            Text(if (activeFilterCount > 0) "Filters · $activeFilterCount" else "Filters")
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Tune, contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize))
                        },
                        trailingIcon = {
                            Icon(
                                if (filtersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                resultCount?.let { count ->
                    Text(
                        text = "$count ${if (count == 1) "result" else "results"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (activeFilterChips != null && activeFilterCount > 0) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activeFilterChips()
            }
        }

        if (filterPanel != null) {
            AnimatedVisibility(visible = filtersExpanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    filterPanel()
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
    }
}
