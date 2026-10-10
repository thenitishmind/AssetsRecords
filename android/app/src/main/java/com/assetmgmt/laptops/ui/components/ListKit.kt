package com.assetmgmt.laptops.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.assetmgmt.laptops.data.remote.Asset
import com.assetmgmt.laptops.data.repository.AssetRepository
import com.assetmgmt.laptops.viewmodel.QueryListViewModel

private val FILTER_KEYS = listOf("branchId", "employeeId", "status", "repairCount", "minCost", "maxCost", "dateFrom", "dateTo", "reason")

/**
 * Search box + optional filter sheet + paged list (infinite scroll, pull to refresh, loading /
 * empty / error states) bound to a [QueryListViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> QueryList(
    vm: QueryListViewModel<T>,
    repo: AssetRepository,
    searchHint: String,
    emptyTitle: String,
    modifier: Modifier = Modifier,
    emptyMessage: String = "",
    filterConfig: FilterConfig? = null,
    /** Filter keys that are fixed by the screen and must survive "Clear all". */
    keepFilters: Set<String> = emptySet(),
    topContent: (@Composable () -> Unit)? = null,
    key: ((T) -> Any)? = null,
    itemContent: @Composable (Int, T) -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val filters by vm.filters.collectAsState()
    var sheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    Column(modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            DebouncedSearch(filters["q"].orEmpty(), { vm.setFilter("q", it) }, searchHint, Modifier.weight(1f))
            if (filterConfig != null) {
                Spacer(Modifier.width(8.dp))
                val n = activeFilterCount(filters, FILTER_KEYS.filter { it !in keepFilters })
                BadgedBox(badge = { if (n > 0) Badge { Text("$n") } }) {
                    FilledTonalIconButton(onClick = { sheet = true }) { Icon(Icons.Default.FilterList, "Filters") }
                }
            }
        }
        topContent?.invoke()
        InlineError(ui.error, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        if (!ui.loading && ui.error == null) {
            Text(
                "${ui.total} record" + if (ui.total == 1) "" else "s",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }
        when {
            ui.loading -> LoadingBox()
            ui.error != null && ui.items.isEmpty() -> ErrorBox(ui.error ?: "", { vm.reload() })
            ui.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                EmptyState(emptyTitle, emptyMessage)
            }
            else -> PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { vm.reload(silent = true) }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(ui.items, key = key?.let { k -> { _: Int, item: T -> k(item) } }) { i, item -> itemContent(i, item) }
                    if (ui.loadingMore) {
                        item { Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } }
                    }
                }
            }
        }
    }
    LoadMoreEffect(listState, ui.hasMore && !ui.loading) { vm.loadMore() }

    if (sheet && filterConfig != null) {
        FilterSheet(
            config = filterConfig,
            current = filters,
            repo = repo,
            onApply = { vm.setFilters(it) },
            onClear = { vm.clearFilters(keepFilters + "q") },
            onDismiss = { sheet = false },
        )
    }
}

/** Compact card for one laptop; used by assets, stock, dead and search lists. */
@Composable
fun AssetCard(
    a: Asset,
    modifier: Modifier = Modifier,
    index: Int? = null,
    showEmployee: Boolean = true,
    onClick: () -> Unit,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AppCard(modifier.fillMaxWidth(), onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            if (index != null) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                val emp = a.employeeName ?: a.displayEmployeeName
                val code = a.employeeCode ?: a.displayEmployeeCode
                if (showEmployee && emp != null) {
                    Text(
                        emp + if (code != null) "  ·  $code" else "",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(a.title.ifBlank { a.assetId }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${a.assetId}  ·  ${a.serialNumber}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    listOfNotNull(
                        if (showEmployee && emp != null) a.title else null,
                        if (showEmployee && emp != null) a.employeeDesignation else null,
                        a.branchName,
                    ).filter { it.isNotBlank() }.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                extra?.invoke(this)
            }
            Spacer(Modifier.width(8.dp))
            StatusBadge(a.status)
        }
    }
}
