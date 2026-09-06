package com.app.snapmind.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.snapmind.domain.model.CapturedItem
import com.app.snapmind.domain.model.Resolution
import com.app.snapmind.domain.repository.CapturedItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// SearchDebounceMs – how long to wait after the last keystroke before querying.
// SearchResultLimit – hard cap on rows returned; a screen only ever shows a handful at once.
private const val SearchDebounceMs = 250L
private const val SearchResultLimit = 200

/**
 * Search and retrospect (spec.md 11.10) share one list: `results` when the query is non-blank,
 * `retrospect` items (resolution == DONE, newest first) when it is blank and the toggle is on,
 * otherwise nothing.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: CapturedItemRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _showRetrospect = MutableStateFlow(false)
    val showRetrospect: StateFlow<Boolean> = _showRetrospect.asStateFlow()

    private val debouncedQuery = _query
        .debounce(SearchDebounceMs)
        .map { it.trim() }
        .distinctUntilChanged()

    // An empty query must never reach the DAO as a `LIKE '%%'` match-everything -- guarded
    // here, not in SQL (spec.md 11.10).
    private val searchResults: Flow<List<CapturedItem>> =
        debouncedQuery.flatMapLatest { trimmed ->
            if (trimmed.isEmpty()) flowOf(emptyList()) else repository.search(trimmed, SearchResultLimit)
        }

    // Archived items (spec.md 7.2) are included on purpose: this is what makes the quiet
    // archive reachable at all, instead of only findable by scrolling.
    private val retrospectItems: Flow<List<CapturedItem>> =
        repository.observeAll().map { all -> all.filter { it.resolution == Resolution.DONE } }

    val items: StateFlow<List<CapturedItem>> = combine(
        debouncedQuery,
        searchResults,
        _showRetrospect,
        retrospectItems
    ) { trimmed, results, retrospectOn, retrospect ->
        when {
            trimmed.isNotEmpty() -> results
            retrospectOn -> retrospect
            else -> emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun toggleRetrospect() {
        _showRetrospect.value = !_showRetrospect.value
    }

    fun updateNote(itemId: Long, note: String) {
        viewModelScope.launch {
            repository.updateNote(itemId, note.trim())
            repository.markDone(itemId)
        }
    }

    fun markDone(itemId: Long) {
        viewModelScope.launch { repository.markDone(itemId) }
    }

    fun discard(itemId: Long) {
        viewModelScope.launch { repository.markDiscarded(itemId) }
    }

    fun restore(itemId: Long) {
        viewModelScope.launch { repository.markUnresolved(itemId) }
    }
}
