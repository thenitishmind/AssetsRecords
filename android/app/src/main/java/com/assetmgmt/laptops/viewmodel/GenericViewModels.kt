package com.assetmgmt.laptops.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.assetmgmt.laptops.AssetApp
import com.assetmgmt.laptops.AppContainer
import com.assetmgmt.laptops.data.remote.Page
import com.assetmgmt.laptops.data.remote.Query
import com.assetmgmt.laptops.data.repository.AssetRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as AssetApp).container

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AssetRepository) -> VM): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container.repo) } })
}

data class ListUi<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
)

/**
 * Paged, filterable list backed by the REST API. Reloads automatically when any
 * write succeeds anywhere in the app (repository data version).
 */
class QueryListViewModel<T>(
    private val repo: AssetRepository,
    initial: Query = emptyMap(),
    private val pageSize: Int = 25,
    private val fetch: suspend (AssetRepository, Query) -> Result<Page<T>>,
) : ViewModel() {

    private val _ui = MutableStateFlow(ListUi<T>())
    val ui: StateFlow<ListUi<T>> = _ui.asStateFlow()

    private val _filters = MutableStateFlow(initial)
    val filters: StateFlow<Query> = _filters.asStateFlow()

    private var page = 1
    private var job: Job? = null

    init {
        reload()
        viewModelScope.launch { repo.dataVersion.drop(1).collect { reload(silent = true) } }
    }

    fun setFilter(key: String, value: String?) {
        val current = _filters.value[key]
        if ((value ?: "") == (current ?: "")) return
        _filters.update { if (value.isNullOrBlank()) it - key else it + (key to value) }
        reload()
    }

    fun setFilters(map: Map<String, String?>) {
        _filters.update { f ->
            var r = f
            map.forEach { (k, v) -> r = if (v.isNullOrBlank()) r - k else r + (k to v) }
            r
        }
        reload()
    }

    fun clearFilters(keep: Set<String> = emptySet()) {
        _filters.update { f -> f.filterKeys { it in keep } }
        reload()
    }

    fun reload(silent: Boolean = false) {
        job?.cancel()
        page = 1
        job = viewModelScope.launch {
            _ui.update { if (silent && it.items.isNotEmpty()) it.copy(refreshing = true) else it.copy(loading = true, error = null) }
            repo.let { fetch(it, _filters.value + mapOf("page" to "1", "limit" to pageSize.toString())) }
                .onSuccess { p ->
                    _ui.value = ListUi(items = p.items, total = p.total, loading = false, hasMore = p.hasMore)
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, refreshing = false, error = e.message) } }
        }
    }

    fun loadMore() {
        val s = _ui.value
        if (s.loading || s.loadingMore || !s.hasMore) return
        job = viewModelScope.launch {
            _ui.update { it.copy(loadingMore = true) }
            fetch(repo, _filters.value + mapOf("page" to (page + 1).toString(), "limit" to pageSize.toString()))
                .onSuccess { p ->
                    page += 1
                    _ui.update { it.copy(items = it.items + p.items, total = p.total, hasMore = p.hasMore, loadingMore = false) }
                }
                .onFailure { e -> _ui.update { it.copy(loadingMore = false, error = e.message) } }
        }
    }
}

sealed interface DetailUi<out T> {
    data object Loading : DetailUi<Nothing>
    data class Error(val message: String) : DetailUi<Nothing>
    data class Data<T>(val value: T, val refreshing: Boolean = false, val error: String? = null) : DetailUi<T>
}

/** Loads one object and refreshes it whenever data changes anywhere in the app. */
class DetailViewModel<T>(
    private val repo: AssetRepository,
    private val fetch: suspend (AssetRepository) -> Result<T>,
) : ViewModel() {
    private val _ui = MutableStateFlow<DetailUi<T>>(DetailUi.Loading)
    val ui: StateFlow<DetailUi<T>> = _ui.asStateFlow()
    private var job: Job? = null

    init {
        load()
        viewModelScope.launch { repo.dataVersion.drop(1).collect { load(silent = true) } }
    }

    fun load(silent: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            val current = _ui.value
            if (silent && current is DetailUi.Data) {
                _ui.value = current.copy(refreshing = true, error = null)
            } else {
                _ui.value = DetailUi.Loading
            }
            fetch(repo)
                .onSuccess { _ui.value = DetailUi.Data(it) }
                .onFailure { e ->
                    val latest = _ui.value
                    if (latest is DetailUi.Data) {
                        _ui.value = latest.copy(refreshing = false, error = e.message ?: "Could not refresh data.")
                    } else {
                        _ui.value = DetailUi.Error(e.message ?: "Could not load data.")
                    }
                }
        }
    }
}

/** Generic one-shot action state (save / confirm buttons). */
class ActionState {
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
}
