package com.assetmgmt.laptops.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.assetmgmt.laptops.data.remote.Query
import com.assetmgmt.laptops.data.remote.Report
import com.assetmgmt.laptops.data.remote.ReportColumn
import com.assetmgmt.laptops.data.repository.query
import com.assetmgmt.laptops.navigation.DrawerScaffold
import com.assetmgmt.laptops.navigation.Routes
import com.assetmgmt.laptops.ui.components.ChipRow
import com.assetmgmt.laptops.ui.components.Col
import com.assetmgmt.laptops.ui.components.DataTable
import com.assetmgmt.laptops.ui.components.DebouncedSearch
import com.assetmgmt.laptops.ui.components.EmptyState
import com.assetmgmt.laptops.ui.components.ErrorBox
import com.assetmgmt.laptops.ui.components.FilterConfig
import com.assetmgmt.laptops.ui.components.FilterSheet
import com.assetmgmt.laptops.ui.components.InlineError
import com.assetmgmt.laptops.ui.components.LoadingBox
import com.assetmgmt.laptops.ui.components.SecondaryButton
import com.assetmgmt.laptops.ui.components.activeFilterCount
import com.assetmgmt.laptops.util.fmtDate
import com.assetmgmt.laptops.util.fmtMoney
import com.assetmgmt.laptops.util.today
import com.assetmgmt.laptops.viewmodel.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

private val ReportTabs = listOf(
    "assets" to "Assets",
    "repairs" to "Repairs",
    "stock" to "Stock",
    "dead" to "Dead",
    "sold" to "Sold",
    "replacements" to "Replacements",
)

private val ReplacementReasons = listOf("HARDWARE_ISSUE" to "Hardware issue", "UPGRADE" to "Upgrade", "DEAD" to "Dead", "OTHER" to "Other")

private fun filterConfigFor(name: String) = when (name) {
    "assets" -> FilterConfig(branch = true, employee = true, statuses = listOf("ASSIGNED" to "Assigned", "REPAIR" to "Repair"), repairCount = true, cost = true, dateLabel = "Assigned")
    "repairs" -> FilterConfig(branch = true, employee = true, statuses = AssetStatusOptions, repairCount = true, cost = true, dateLabel = "Repair")
    "stock" -> FilterConfig(branch = true)
    "dead" -> FilterConfig(branch = true, employee = true, repairCount = true, cost = true, dateLabel = "Dead")
    "sold" -> FilterConfig(branch = true, dateLabel = "Sold")
    else -> FilterConfig(branch = true, employee = true, reasons = ReplacementReasons, dateLabel = "Replacement")
}

private data class ReportUi(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val report: Report? = null,
    val rows: List<JsonObject> = emptyList(),
    val page: Int = 1,
    val error: String? = null,
)

@Composable
fun ReportsScreen(nav: NavHostController) {
    val repo = appContainer().repo
    val admin = isAdmin()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf("assets") }
    var filters by remember { mutableStateOf<Query>(emptyMap()) }
    var ui by remember { mutableStateOf(ReportUi()) }
    var sheet by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var exporting by remember { mutableStateOf<String?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(name, filters, reload) {
        ui = ReportUi(loading = true)
        repo.report(name, filters + mapOf("page" to "1", "limit" to "50"))
            .onSuccess { ui = ReportUi(loading = false, report = it, rows = it.rows, page = 1) }
            .onFailure { ui = ReportUi(loading = false, error = it.message) }
    }

    fun loadMore() {
        val cur = ui
        if (cur.loading || cur.loadingMore || cur.report?.hasMore != true) return
        ui = cur.copy(loadingMore = true)
        scope.launch {
            repo.report(name, filters + mapOf("page" to "${cur.page + 1}", "limit" to "50"))
                .onSuccess { ui = ui.copy(loadingMore = false, report = it, rows = ui.rows + it.rows, page = cur.page + 1) }
                .onFailure { ui = ui.copy(loadingMore = false, error = it.message) }
        }
    }

    fun export(format: String) {
        exporting = format
        exportError = null
        scope.launch {
            repo.exportReport(name, filters + mapOf("format" to format))
                .onSuccess { body ->
                    runCatching {
                        val file = withContext(Dispatchers.IO) {
                            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                            val f = File(dir, "$name-report-${today()}.$format")
                            body.use { b -> b.byteStream().use { i -> f.outputStream().use { o -> i.copyTo(o) } } }
                            f
                        }
                        shareFile(context, file, if (format == "pdf") "application/pdf" else "text/csv")
                    }.onFailure { exportError = "Could not save the report. Please try again." }
                }
                .onFailure { exportError = it.message }
            exporting = null
        }
    }

    val config = filterConfigFor(name)
    DrawerScaffold(nav, Routes.REPORTS, "Reports") { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Spacer(Modifier.height(8.dp))
            ChipRow(ReportTabs, name, { it?.let { n -> name = n; filters = emptyMap() } }, allLabel = null)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                DebouncedSearch(filters["q"].orEmpty(), { q -> filters = if (q.isBlank()) filters - "q" else filters + ("q" to q) }, "Search this report", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                val n = activeFilterCount(filters)
                BadgedBox(badge = { if (n > 0) Badge { Text("$n") } }) {
                    FilledTonalIconButton(onClick = { sheet = true }) { Icon(Icons.Default.FilterList, "Filters") }
                }
            }
            if (admin) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportButton("Export PDF", Icons.Default.PictureAsPdf, exporting == "pdf", exporting == null, Modifier.weight(1f)) { export("pdf") }
                    ExportButton("Export Excel (CSV)", Icons.Default.TableChart, exporting == "csv", exporting == null, Modifier.weight(1f)) { export("csv") }
                }
                InlineError(exportError, Modifier.padding(16.dp, 8.dp))
            }
            val s = ui
            when {
                s.loading -> LoadingBox()
                s.error != null && s.report == null -> ErrorBox(s.error, { reload++ })
                s.report != null -> ReportTable(s, ::loadMore)
            }
        }
    }

    if (sheet) {
        FilterSheet(
            config = config, current = filters, repo = repo,
            onApply = { m -> filters = (filters.filterKeys { it == "q" } + m.filterValues { !it.isNullOrBlank() }.mapValues { it.value!! }) },
            onClear = { filters = filters.filterKeys { it == "q" } },
            onDismiss = { sheet = false },
        )
    }
}

@Composable
private fun ExportButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, busy: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        if (busy) CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp) else Icon(icon, null)
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1)
    }
}

@Composable
private fun ReportTable(s: ReportUi, onLoadMore: () -> Unit) {
    val r = s.report ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "${r.title.ifBlank { "Report" }}  ·  ${r.total} record${if (r.total == 1) "" else "s"}",
            style = MaterialTheme.typography.titleSmall,
        )
        if (s.rows.isEmpty()) {
            EmptyState("No data", "No records match the selected filters.")
        } else {
            val cols = listOf(Col("S.No", 60.dp, numeric = true)) + r.columns.map { c ->
                Col(
                    c.label,
                    when {
                        c.key.contains("serial", true) -> 140.dp
                        c.key.contains("employee", true) || c.key.contains("branch", true) -> 150.dp
                        else -> maxOf(96, c.label.length * 9 + 28).dp
                    },
                    numeric = c.type == "money" || c.type == "int",
                    badge = c.key == "status" || c.key == "oldAssetStatus",
                )
            }
            val rows = s.rows.mapIndexed { i, row ->
                listOf(row["sno"]?.let { (it as? JsonPrimitive)?.content } ?: "${i + 1}") + r.columns.map { c -> cellText(c, row) }
            }
            val footer = r.totals?.let { t -> listOf("") + r.columns.map { c -> cellText(c, t) } }
            DataTable(cols, rows, footer = footer)
            if (r.hasMore) {
                SecondaryButton(if (s.loadingMore) "Loading…" else "Load more", onLoadMore, Modifier.fillMaxWidth(), enabled = !s.loadingMore)
            }
        }
        Spacer(Modifier.height(48.dp))
    }
}

private fun cellText(col: ReportColumn, row: JsonObject): String {
    val v = row[col.key]
    if (v == null || v is JsonNull) return ""
    val s = (v as? JsonPrimitive)?.content ?: v.toString()
    return when (col.type) {
        "date" -> fmtDate(s)
        "money" -> fmtMoney(s.toDoubleOrNull())
        else -> s
    }
}

private fun shareFile(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
