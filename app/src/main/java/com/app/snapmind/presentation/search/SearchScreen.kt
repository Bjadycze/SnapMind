package com.app.snapmind.presentation.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.snapmind.R
import com.app.snapmind.presentation.components.CapturedItemCard
import com.app.snapmind.presentation.detail.ItemDetailDialog

/**
 * Search and retrospect ("ohlédnutí") are the same screen: a query field plus a list
 * (spec.md 11.10). This is what makes the quiet archive (§7.2) reachable at all -- items that
 * stopped asking are otherwise only findable by scrolling.
 *
 * Hard constraints from §7.3, enforced here rather than left for someone to relax later:
 * - No count, anywhere. Not "12 done", not a badge on the search icon, nothing that turns a
 *   list into a score.
 * - No weekly/monthly summary, no comparison to a previous period, no chart. Those are a
 *   streak wearing a different hat, same as a badge.
 * - Pull-only. This screen never opens itself and is never surfaced by a notification -- it
 *   exists only behind the search icon the user tapped.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val showRetrospect by viewModel.showRetrospect.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val customCategories by viewModel.customCategories.collectAsStateWithLifecycle()
    val retiredCategories by viewModel.retiredCategories.collectAsStateWithLifecycle()

    var openItemId by remember { mutableStateOf<Long?>(null) }
    val openIndex = items.indexOfFirst { it.id == openItemId }

    if (openItemId != null && openIndex >= 0) {
        ItemDetailDialog(
            items = items,
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
            onRestore = { id -> viewModel.restore(id) },
            customCategories = customCategories,
            retiredCategories = retiredCategories,
            onPickCategory = { id, value -> viewModel.setCategory(id, value) },
            onCreateCategory = { id, name -> viewModel.createCategory(id, name) }
        )
    }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.search_back)
                )
            }
            Text(
                text = stringResource(R.string.search_title),
                style = MaterialTheme.typography.headlineSmall
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )

        if (query.isBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.search_retrospect_toggle),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(checked = showRetrospect, onCheckedChange = { viewModel.toggleRetrospect() })
            }
        }

        when {
            query.isBlank() && !showRetrospect -> EmptyPrompt(stringResource(R.string.search_empty_prompt))
            items.isEmpty() -> EmptyPrompt(stringResource(R.string.search_no_results))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    // Named arguments: the card gained optional parameters after onOpen,
                    // so a trailing lambda would bind to the last one instead.
                    CapturedItemCard(
                        item = item,
                        onOpen = { openItemId = item.id }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPrompt(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}
