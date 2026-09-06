package com.app.snapmind.presentation.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.snapmind.R
import androidx.compose.runtime.LaunchedEffect
import com.app.snapmind.presentation.components.CapturedItemCard
import com.app.snapmind.presentation.components.categoryFilterKey
import com.app.snapmind.presentation.components.matchesCategoryFilter
import com.app.snapmind.presentation.detail.ItemDetailDialog
import com.app.snapmind.presentation.main.components.FilterTabs
import com.app.snapmind.presentation.main.components.ItemFilter
import com.app.snapmind.presentation.theme.LocalSnapMindPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box

@Composable
fun MainScreen(
    onFixPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val state by viewModel.items.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf(ItemFilter.ACTIVE) }

    // Category filter from the strip on the card (spec.md 11.15). Held as the stored enum name,
    // the same string the row carries, so nothing has to be parsed to compare.
    var categoryFilter by remember { mutableStateOf<String?>(null) }

    val customCategories by viewModel.customCategories.collectAsStateWithLifecycle()
    val retiredCategories by viewModel.retiredCategories.collectAsStateWithLifecycle()

    // Held by id, not by value: the item must re-read from the flow after an edit so the
    // dialog shows what was actually saved.
    var openItemId by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.search_action)
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.main_settings)
                    )
                }
            }
        }

        // Dismissible in spirit but always re-rendered while something is actually broken:
        // a silently non-working capture pipeline is the failure mode this prevents.
        if (permissions.missingCritical.isNotEmpty()) {
            PermissionBanner(onFix = onFixPermissions)
        }

        val filteredActive = remember(state.active, selectedFilter, categoryFilter) {
            when (selectedFilter) {
                ItemFilter.ACTIVE -> state.active.filter { it.resolvedAt == null }
                ItemFilter.DONE -> state.active.filter { it.resolvedAt != null }
                ItemFilter.ALL -> state.active
            }.filter { matchesCategoryFilter(it.effectiveCategory, categoryFilter) }
        }
        val filteredArchived = remember(state.archived, selectedFilter, categoryFilter) {
            when (selectedFilter) {
                ItemFilter.ACTIVE -> state.archived.filter { it.resolvedAt == null }
                ItemFilter.DONE -> state.archived.filter { it.resolvedAt != null }
                ItemFilter.ALL -> state.archived
            }.filter { matchesCategoryFilter(it.effectiveCategory, categoryFilter) }
        }

        // The trap this closes: filter by one category, settle the last item in it, and the list
        // is empty with no strip left to tap the filter off. Switching tabs also clears it, but
        // that is a rule you have to know; this one nobody has to notice.
        LaunchedEffect(filteredActive, filteredArchived) {
            if (categoryFilter != null && filteredActive.isEmpty() && filteredArchived.isEmpty()) {
                categoryFilter = null
            }
        }

        // Swipe stays inside the active filter: the pager gets exactly what the list shows.
        val visibleItems = filteredActive + filteredArchived
        val openIndex = visibleItems.indexOfFirst { it.id == openItemId }

        if (openItemId != null && openIndex >= 0) {
            ItemDetailDialog(
                items = visibleItems,
                initialIndex = openIndex,
                onDismiss = { openItemId = null },
                onSaveNote = { id, text -> viewModel.updateNote(id, text) },
                onDone = { id ->
                    viewModel.markDone(id)
                    openItemId = null
                },
                onDiscard = { id ->
                    viewModel.discard(id)
                    openItemId = null
                },
                // Deliberately keeps the dialog open: the card fades behind it, which is the
                // confirmation that something changed.
                onRestore = { id -> viewModel.restore(id) },
                customCategories = customCategories,
                retiredCategories = retiredCategories,
                onPickCategory = { id, value -> viewModel.setCategory(id, value) },
                onCreateCategory = { id, name -> viewModel.createCategory(id, name) }
            )
        }

        FilterTabs(
            selected = selectedFilter,
            // Switching tab drops the category filter too: two filters stacked silently would
            // leave you looking at a short list with no visible reason for it.
            onSelect = {
                selectedFilter = it
                categoryFilter = null
            }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(
                    RoundedCornerShape(
                        topStart = if (selectedFilter == ItemFilter.ACTIVE) 0.dp else 14.dp,
                        topEnd = 14.dp,
                        bottomStart = 14.dp,
                        bottomEnd = 14.dp
                    )
                )
                .background(LocalSnapMindPalette.current.panel)
                .padding(horizontal = 8.dp)
        ) {
            if (filteredActive.isEmpty() && filteredArchived.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
            ) {
                items(filteredActive, key = { it.id }) { item ->
                    CapturedItemCard(
                        item = item,
                        onOpen = { openItemId = item.id },
                        // Vyřízené is a read-only look back: the strip stays, the gesture does
                        // not. An already-settled item is refused inside the card as well.
                        onSettle = if (selectedFilter == ItemFilter.DONE) {
                            null
                        } else {
                            { viewModel.markDone(item.id) }
                        },
                        // Tap the strip to filter by that category, tap the same one again to
                        // clear. An unclassified item has nothing to filter by, so its strip
                        // stays inert.
                        onFilterCategory = {
                            val key = categoryFilterKey(item.effectiveCategory)
                            categoryFilter = if (categoryFilter == key) null else key
                        },
                        categorySelected = categoryFilter != null &&
                            matchesCategoryFilter(item.effectiveCategory, categoryFilter)
                    )
                }

                if (filteredArchived.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.section_archive),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }
                    items(filteredArchived, key = { it.id }) { item ->
                        CapturedItemCard(
                        item = item,
                        onOpen = { openItemId = item.id },
                        // Vyřízené is a read-only look back: the strip stays, the gesture does
                        // not. An already-settled item is refused inside the card as well.
                        onSettle = if (selectedFilter == ItemFilter.DONE) {
                            null
                        } else {
                            { viewModel.markDone(item.id) }
                        },
                        // Tap the strip to filter by that category, tap the same one again to
                        // clear. An unclassified item has nothing to filter by, so its strip
                        // stays inert.
                        onFilterCategory = {
                            val key = categoryFilterKey(item.effectiveCategory)
                            categoryFilter = if (categoryFilter == key) null else key
                        },
                        categorySelected = categoryFilter != null &&
                            matchesCategoryFilter(item.effectiveCategory, categoryFilter)
                    )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun PermissionBanner(onFix: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.banner_permissions_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.banner_permissions_body),
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = onFix) {
                Text(stringResource(R.string.banner_permissions_action))
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
