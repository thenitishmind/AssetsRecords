package com.assetmgmt.laptops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.assetmgmt.laptops.data.remote.Asset
import com.assetmgmt.laptops.data.remote.Repair
import com.assetmgmt.laptops.data.remote.RepairDetails
import com.assetmgmt.laptops.data.repository.body
import com.assetmgmt.laptops.data.repository.query
import com.assetmgmt.laptops.navigation.DrawerScaffold
import com.assetmgmt.laptops.navigation.Routes
import com.assetmgmt.laptops.ui.components.AppCard
import com.assetmgmt.laptops.ui.components.AppTextField
import com.assetmgmt.laptops.ui.components.AssetCard
import com.assetmgmt.laptops.ui.components.DateField
import com.assetmgmt.laptops.ui.components.DropdownField
import com.assetmgmt.laptops.ui.components.FilterConfig
import com.assetmgmt.laptops.ui.components.InfoRow
import com.assetmgmt.laptops.ui.components.InlineError
import com.assetmgmt.laptops.ui.components.Option
import com.assetmgmt.laptops.ui.components.PrimaryButton
import com.assetmgmt.laptops.ui.components.QueryList
import com.assetmgmt.laptops.ui.components.RemotePickerField
import com.assetmgmt.laptops.ui.components.SectionCard
import com.assetmgmt.laptops.ui.components.StatusBadge
import com.assetmgmt.laptops.util.fmtDate
import com.assetmgmt.laptops.util.fmtMoney
import com.assetmgmt.laptops.util.isoToDay
import com.assetmgmt.laptops.util.nz
import com.assetmgmt.laptops.util.prettyEnum
import com.assetmgmt.laptops.util.rememberSubmit
import com.assetmgmt.laptops.util.toMoneyOrNull
import com.assetmgmt.laptops.util.today
import com.assetmgmt.laptops.viewmodel.DetailViewModel
import com.assetmgmt.laptops.viewmodel.QueryListViewModel
import com.assetmgmt.laptops.viewmodel.appContainer
import com.assetmgmt.laptops.viewmodel.appViewModel

val RepairStatusOptions = listOf(
    "SENT_FOR_REPAIR" to "Sent for repair",
    "UNDER_REPAIR" to "Under repair",
    "REPAIRED" to "Repaired",
    "NOT_REPAIRABLE" to "Not repairable",
    "DEAD" to "Dead",
    "RETURNED" to "Returned",
)

private val RepairTransitions = mapOf(
    "SENT_FOR_REPAIR" to listOf("UNDER_REPAIR", "REPAIRED", "NOT_REPAIRABLE", "DEAD"),
    "UNDER_REPAIR" to listOf("REPAIRED", "NOT_REPAIRABLE", "DEAD"),
    "NOT_REPAIRABLE" to listOf("UNDER_REPAIR", "DEAD"),
    "REPAIRED" to listOf("RETURNED"),
)

private val IssueTypes = listOf(
    "Battery", "Display / Screen", "Keyboard / Touchpad", "Motherboard", "Charger / Adapter",
    "Hard disk / SSD", "RAM", "Software / OS", "Physical damage", "Water damage", "Other",
).map { it to it }

private val WarrantyOptions = listOf("Under warranty" to "Under warranty", "Out of warranty" to "Out of warranty")

// ---------------------------------------------------------------- Repair Assets (tabs)

@Composable
fun RepairAssetsScreen(nav: NavHostController) {
    val repo = appContainer().repo
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val underRepair = appViewModel<QueryListViewModel<Asset>>(key = "repair-assets") { r ->
        QueryListViewModel(r, mapOf("status" to "REPAIR")) { rp, q -> rp.assets(q) }
    }
    val history = appViewModel<QueryListViewModel<Repair>>(key = "repair-history") { r ->
        QueryListViewModel(r) { rp, q -> rp.repairs(q) }
    }
    DrawerScaffold(
        nav, Routes.REPAIRS, "Repair Assets",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.repairForm()) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New repair") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Under Repair") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Repair History") })
            }
            if (tab == 0) {
                QueryList(
                    vm = underRepair, repo = repo,
                    searchHint = "Search asset ID, serial, model, employee",
                    emptyTitle = "Nothing under repair",
                    emptyMessage = "Laptops sent for repair will appear here.",
                    filterConfig = FilterConfig(branch = true, employee = true, repairCount = true, cost = true),
                    keepFilters = setOf("status"),
                    key = { it.assetId },
                ) { i, a ->
                    AssetCard(a, index = i, onClick = { nav.navigate(Routes.asset(a.assetId)) }) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${a.repairCount} repair${if (a.repairCount == 1) "" else "s"}  ·  Total ${fmtMoney(a.totalRepairCost)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                QueryList(
                    vm = history, repo = repo,
                    searchHint = "Search asset, serial, vendor, issue",
                    emptyTitle = "No repair records",
                    emptyMessage = "Create a repair record to start tracking costs.",
                    filterConfig = FilterConfig(branch = true, employee = true, statuses = RepairStatusOptions, cost = true, dateLabel = "Repair"),
                    key = { it.repairId },
                ) { _, r -> RepairCard(r) { nav.navigate(Routes.repair(r.repairId)) } }
            }
        }
    }
}

@Composable
fun RepairCard(r: Repair, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("${r.assetId}  ·  ${r.serialNumber.orEmpty()}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(r.issueType.ifBlank { "Repair" }, style = MaterialTheme.typography.bodyMedium)
                Text(
                    listOfNotNull(r.employeeName, r.employeeDesignation, r.branchName, r.vendor).filter { it.isNotBlank() }.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${fmtDate(r.repairDate)}  ·  ${fmtMoney(r.finalCost ?: r.estimatedCost)}" + if (r.finalCost == null && r.estimatedCost != null) " (est.)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(r.status)
        }
    }
}

// ---------------------------------------------------------------- Repair detail

@Composable
fun RepairDetailScreen(nav: NavHostController, id: String) {
    val vm = appViewModel<DetailViewModel<RepairDetails>>(key = "repair-$id") { DetailViewModel(it) { r -> r.repairDetails(id) } }
    ScreenScaffold(nav, "Repair $id") { pad ->
        DetailBody(vm, pad) { d -> RepairDetailContent(nav, d) }
    }
}

@Composable
private fun RepairDetailContent(nav: NavHostController, d: RepairDetails) {
    val r = d.repair
    var sheet by remember { mutableStateOf(false) }
    ScrollPage {
        AppCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.issueType.ifBlank { "Repair" }, style = MaterialTheme.typography.titleLarge)
                    Text(r.repairId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusBadge(r.status)
            }
        }
        SectionCard("Laptop") {
            InfoRow("Asset ID", r.assetId, onClick = { nav.navigate(Routes.asset(r.assetId)) })
            InfoRow("Serial number", d.asset?.serialNumber ?: r.serialNumber)
            InfoRow("Model", d.asset?.title ?: "${r.brand.orEmpty()} ${r.model.orEmpty()}".trim())
            InfoRow("Employee", r.employeeName?.let { "$it (${r.employeeCode.orEmpty()})" })
            InfoRow("Designation", r.employeeDesignation)
            InfoRow("Branch", r.branchName)
            InfoRow("Branch", r.branchName)
        }
        SectionCard("Repair details") {
            InfoRow("Repair date", fmtDate(r.repairDate))
            InfoRow("Start date", r.repairStartDate?.let { fmtDate(it) })
            InfoRow("Completion date", r.repairCompletionDate?.let { fmtDate(it) })
            InfoRow("Vendor", r.vendor)
            InfoRow("Problem", r.problemDescription)
            InfoRow("Estimated cost", r.estimatedCost?.let { fmtMoney(it) })
            InfoRow("Final cost", r.finalCost?.let { fmtMoney(it) })
            InfoRow("Warranty", r.warranty)
            InfoRow("Remarks", r.remarks)
        }
        if (RepairTransitions[r.status].orEmpty().isNotEmpty() || r.status != "RETURNED") {
            PrimaryButton("Update repair", { sheet = true })
        }
        SectionCard("Previous repairs of this laptop") {
            if (d.previousRepairs.isEmpty()) {
                Text("This is the first repair.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.previousRepairs.forEach { p -> RepairCard(p) { nav.navigate(Routes.repair(p.repairId)) } }
                }
            }
        }
    }
    if (sheet) UpdateRepairSheet(r) { sheet = false }
}

@Composable
private fun UpdateRepairSheet(r: Repair, onDismiss: () -> Unit) {
    val repo = appContainer().repo
    val submit = rememberSubmit()
    val statuses = listOf(r.status) + RepairTransitions[r.status].orEmpty()
    var status by remember { mutableStateOf<String?>(r.status) }
    var vendor by remember { mutableStateOf(r.vendor.orEmpty()) }
    var est by remember { mutableStateOf(r.estimatedCost?.toString().orEmpty()) }
    var final by remember { mutableStateOf(r.finalCost?.toString().orEmpty()) }
    var start by remember { mutableStateOf(isoToDay(r.repairStartDate)) }
    var done by remember { mutableStateOf(isoToDay(r.repairCompletionDate)) }
    var warranty by remember { mutableStateOf<String?>(r.warranty) }
    var remarks by remember { mutableStateOf(r.remarks.orEmpty()) }
    var cost by remember { mutableStateOf("") }
    val needsFinal = status == "REPAIRED"

    ActionSheet("Update repair", onDismiss) {
        DropdownField("Status", statuses.map { it to prettyEnum(it) }, status, { status = it })
        AppTextField(vendor, { vendor = it }, "Repair vendor")
        AppTextField(est, { est = it.filter { c -> c.isDigit() || c == '.' } }, "Estimated cost", keyboardType = KeyboardType.Decimal, prefix = "₹")
        AppTextField(final, { final = it.filter { c -> c.isDigit() || c == '.' } }, "Final repair cost", keyboardType = KeyboardType.Decimal, prefix = "₹", required = needsFinal)
        DateField("Repair start date", start, { start = it })
        DateField("Completion date", done, { done = it })
        DropdownField("Warranty", WarrantyOptions, warranty, { warranty = it }, allowClear = true)
        AppTextField(remarks, { remarks = it }, "Remarks", singleLine = false)
        InlineError(submit.error)
        if (needsFinal && final.toMoneyOrNull() == null) Text("Final cost is required to mark the repair as repaired.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        PrimaryButton("Save changes", {
            submit.run({
                repo.updateRepair(
                    r.repairId,
                    body(
                        "status" to status, "vendor" to vendor, "estimatedCost" to est.toMoneyOrNull(),
                        "finalCost" to final.toMoneyOrNull(), "repairStartDate" to start, "repairCompletionDate" to done,
                        "warranty" to warranty, "remarks" to remarks,
                    ),
                )
            }, { onDismiss() })
        }, enabled = !needsFinal || final.toMoneyOrNull() != null, busy = submit.busy)
    }
}

// ---------------------------------------------------------------- Repair form (send laptop for repair)

@Composable
fun RepairFormScreen(nav: NavHostController, assetIdArg: String?) {
    val repo = appContainer().repo
    val presetId = assetIdArg.nz()
    val submit = rememberSubmit()

    var asset by remember { mutableStateOf<Option?>(null) }
    var issue by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf("") }
    var vendor by remember { mutableStateOf("") }
    var est by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today()) }
    var warranty by remember { mutableStateOf<String?>(null) }
    var remarks by remember { mutableStateOf("") }

    LaunchedEffect(presetId) {
        if (presetId != null) repo.assetDetails(presetId).onSuccess {
            asset = Option(it.asset.assetId, "${it.asset.assetId} · ${it.asset.serialNumber}", it.asset.title)
        }
    }

    ScreenScaffold(nav, "Send for repair") { pad ->
        FormPage(pad) {
            RemotePickerField(
                label = "Laptop", selected = asset, onSelect = { asset = it }, required = true, enabled = presetId == null,
                loader = { q ->
                    repo.assets(query("q" to q, "status" to "ASSIGNED,AVAILABLE", "limit" to 50)).getOrThrow().items.map {
                        Option(it.assetId, "${it.assetId} · ${it.serialNumber}", listOfNotNull(it.title, it.employeeName).joinToString(" · "))
                    }
                },
            )
            DropdownField("Issue type", IssueTypes, issue, { issue = it }, required = true)
            AppTextField(description, { description = it }, "Problem description", singleLine = false)
            AppTextField(vendor, { vendor = it }, "Repair vendor")
            AppTextField(est, { est = it.filter { c -> c.isDigit() || c == '.' } }, "Estimated cost", keyboardType = KeyboardType.Decimal, prefix = "₹")
            DateField("Repair date", date, { date = it }, required = true)
            DropdownField("Warranty", WarrantyOptions, warranty, { warranty = it }, allowClear = true)
            AppTextField(remarks, { remarks = it }, "Remarks", singleLine = false)
            InlineError(submit.error)
            PrimaryButton("Send for repair", {
                submit.run({
                    repo.createRepair(
                        body(
                            "assetId" to asset?.id, "issueType" to issue, "problemDescription" to description, "vendor" to vendor,
                            "estimatedCost" to est.toMoneyOrNull(), "repairDate" to date, "warranty" to warranty, "remarks" to remarks,
                        ),
                    )
                }, { r -> nav.popBackStack(); nav.navigate(Routes.repair(r.repairId)) })
            }, enabled = asset != null && issue != null, busy = submit.busy)
        }
    }
}
