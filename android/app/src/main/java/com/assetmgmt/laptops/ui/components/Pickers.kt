package com.assetmgmt.laptops.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.assetmgmt.laptops.data.remote.Query
import com.assetmgmt.laptops.data.repository.AssetRepository
import com.assetmgmt.laptops.data.repository.query
import com.assetmgmt.laptops.util.fmtDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneOffset

data class Option(val id: String, val title: String, val subtitle: String? = null)

/** Simple dropdown for short, static option lists. */
@Composable
fun DropdownField(
    label: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    required: Boolean = false,
    allowClear: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(if (required) "$label *" else label) },
            trailingIcon = {
                if (allowClear && selected != null) IconButton({ onSelect(null) }) { Icon(Icons.Default.Clear, "Clear") }
                else Icon(Icons.Default.ArrowDropDown, null)
            },
            modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { expanded = true },
            shape = MaterialTheme.shapes.small,
        )
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (id, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}

/** Field that opens a searchable dialog backed by a (remote) loader. */
@Composable
fun RemotePickerField(
    label: String,
    selected: Option?,
    onSelect: (Option?) -> Unit,
    loader: suspend (String) -> List<Option>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    required: Boolean = false,
    allowClear: Boolean = false,
    loaderKey: Any? = null,
) {
    var show by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected?.title ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(if (required) "$label *" else label) },
            supportingText = selected?.subtitle?.let { { Text(it) } },
            trailingIcon = {
                if (allowClear && selected != null) IconButton({ onSelect(null) }) { Icon(Icons.Default.Clear, "Clear") }
                else Icon(Icons.Default.ArrowDropDown, null)
            },
            modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { show = true },
            shape = MaterialTheme.shapes.small,
        )
    }
    if (show) {
        var q by remember { mutableStateOf("") }
        var options by remember { mutableStateOf<List<Option>?>(null) }
        var loadError by remember { mutableStateOf<String?>(null) }
        var retry by remember { mutableStateOf(0) }
        LaunchedEffect(q, loaderKey, retry) {
            options = null
            loadError = null
            if (q.isNotEmpty()) delay(300)
            try {
                options = loader(q.trim())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadError = e.message ?: "Unable to load options. Check the server connection and try again."
                options = emptyList()
            }
        }
        Dialog(onDismissRequest = { show = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxWidth(0.94f).heightIn(max = 560.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp)) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    SearchField(q, { q = it }, placeholder = "Type to search")
                    Spacer(Modifier.height(8.dp))
                    val list = options
                    when {
                        list == null -> Box(Modifier.fillMaxWidth().height(120.dp)) { LoadingBox() }
                        loadError != null -> Column {
                            InlineError(loadError)
                            TextButton({ retry++ }, Modifier.align(androidx.compose.ui.Alignment.End)) { Text("Retry") }
                        }
                        list.isEmpty() -> EmptyState("No results", "Try a different search.")
                        else -> LazyColumn(Modifier.weight(1f, fill = false)) {
                            items(list, key = { it.id }) { o ->
                                Column(
                                    Modifier.fillMaxWidth().clickable { onSelect(o); show = false }.padding(vertical = 10.dp, horizontal = 4.dp),
                                ) {
                                    Text(o.title, style = MaterialTheme.typography.bodyLarge)
                                    o.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                                Divider()
                            }
                        }
                    }
                    TextButton({ show = false }, Modifier.align(androidx.compose.ui.Alignment.End)) { Text("Close") }
                }
            }
        }
    }
}

/** Date field (value = yyyy-MM-dd). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, required: Boolean = false, allowClear: Boolean = true) {
    var show by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = if (value.isBlank()) "" else fmtDate("${value}T00:00:00Z"),
            onValueChange = {},
            readOnly = true,
            label = { Text(if (required) "$label *" else label) },
            trailingIcon = {
                if (allowClear && value.isNotBlank() && !required) IconButton({ onValueChange("") }) { Icon(Icons.Default.Clear, "Clear") }
                else Icon(Icons.Default.CalendarMonth, null)
            },
            modifier = Modifier.fillMaxWidth().clickable { show = true },
            shape = MaterialTheme.shapes.small,
        )
    }
    if (show) {
        val initial = runCatching { Instant.parse("${value}T00:00:00Z").toEpochMilli() }.getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { onValueChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    show = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ show = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

// ---------- Filter sheet ----------

data class FilterConfig(
    val branch: Boolean = true,
    val employee: Boolean = false,
    val statuses: List<Pair<String, String>> = emptyList(),
    val repairCount: Boolean = false,
    val cost: Boolean = false,
    val dateLabel: String? = null,
    val reasons: List<Pair<String, String>> = emptyList(),
)

suspend fun branchOptions(repo: AssetRepository, q: String): List<Option> =
    repo.branches(query("q" to q, "status" to "ACTIVE", "limit" to 200, "sort" to "name:asc")).getOrThrow().items
        .map { Option(it.branchId, it.name, "${it.code} · ${it.city}") }

suspend fun employeeOptions(repo: AssetRepository, q: String, branchId: String? = null, onlyActive: Boolean = true): List<Option> =
    repo.employees(query("q" to q, "branchId" to branchId, "status" to if (onlyActive) "ACTIVE" else null, "limit" to 200)).getOrThrow().items
        .map { Option(it.employeeId, it.name, "${it.employeeCode} · ${it.branchName ?: ""}") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    config: FilterConfig,
    current: Query,
    repo: AssetRepository,
    onApply: (Map<String, String?>) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var branch by remember { mutableStateOf(current["branchId"]?.let { Option(it, it) }) }
    var employee by remember { mutableStateOf(current["employeeId"]?.let { Option(it, it) }) }
    var status by remember { mutableStateOf(current["status"]) }
    var repairCount by remember { mutableStateOf(current["repairCount"]) }
    var minCost by remember { mutableStateOf(current["minCost"] ?: "") }
    var maxCost by remember { mutableStateOf(current["maxCost"] ?: "") }
    var from by remember { mutableStateOf(current["dateFrom"] ?: "") }
    var to by remember { mutableStateOf(current["dateTo"] ?: "") }
    var reason by remember { mutableStateOf(current["reason"]) }

    // resolve human-readable names for IDs coming from the current filter state
    LaunchedEffect(Unit) {
        branch?.takeIf { it.title == it.id }?.let { b -> repo.branch(b.id).onSuccess { branch = Option(it.branchId, it.name, it.code) } }
        employee?.takeIf { it.title == it.id }?.let { e -> repo.employee(e.id).onSuccess { employee = Option(it.employeeId, it.name, it.employeeCode) } }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Filters", style = MaterialTheme.typography.titleLarge)
            if (config.branch) {
                RemotePickerField("Branch", branch, { branch = it; if (employee != null) employee = null }, { branchOptions(repo, it) }, allowClear = true)
            }
            if (config.employee) {
                RemotePickerField(
                    "Employee", employee, { employee = it },
                    { employeeOptions(repo, it, branch?.id, onlyActive = false) },
                    allowClear = true, loaderKey = branch?.id,
                )
            }
            if (config.statuses.isNotEmpty()) {
                DropdownField("Status", config.statuses, status, { status = it }, allowClear = true)
            }
            if (config.reasons.isNotEmpty()) {
                DropdownField("Reason", config.reasons, reason, { reason = it }, allowClear = true)
            }
            if (config.repairCount) {
                DropdownField("Repair count", listOf("0" to "0", "1" to "1", "2" to "2", "3+" to "3 or more"), repairCount, { repairCount = it }, allowClear = true)
            }
            if (config.cost) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(minCost, { minCost = it.filter { c -> c.isDigit() || c == '.' } }, "Min repair cost", Modifier.weight(1f), KeyboardType.Decimal, prefix = "₹")
                    AppTextField(maxCost, { maxCost = it.filter { c -> c.isDigit() || c == '.' } }, "Max repair cost", Modifier.weight(1f), KeyboardType.Decimal, prefix = "₹")
                }
            }
            if (config.dateLabel != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DateField("${config.dateLabel} from", from, { from = it }, Modifier.weight(1f))
                    DateField("${config.dateLabel} to", to, { to = it }, Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                SecondaryButton("Clear all", { onClear(); onDismiss() }, Modifier.weight(1f))
                PrimaryButton("Apply", {
                    onApply(
                        mapOf(
                            "branchId" to branch?.id, "employeeId" to employee?.id, "status" to status,
                            "repairCount" to repairCount, "minCost" to minCost, "maxCost" to maxCost,
                            "dateFrom" to from, "dateTo" to to, "reason" to reason,
                        ),
                    )
                    onDismiss()
                }, Modifier.weight(1f))
            }
        }
    }
}

fun activeFilterCount(f: Query, keys: List<String> = listOf("branchId", "employeeId", "status", "repairCount", "minCost", "maxCost", "dateFrom", "dateTo", "reason")) =
    keys.count { !f[it].isNullOrBlank() }
