package com.assetmgmt.laptops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.assetmgmt.laptops.data.remote.Asset
import com.assetmgmt.laptops.data.remote.Replacement
import com.assetmgmt.laptops.data.remote.StockOverview
import com.assetmgmt.laptops.navigation.DrawerScaffold
import com.assetmgmt.laptops.navigation.Routes
import com.assetmgmt.laptops.ui.components.AssetCard
import com.assetmgmt.laptops.ui.components.AppCard
import com.assetmgmt.laptops.ui.components.Col
import com.assetmgmt.laptops.ui.components.DataTable
import com.assetmgmt.laptops.ui.components.FilterConfig
import com.assetmgmt.laptops.ui.components.QueryList
import com.assetmgmt.laptops.ui.components.SectionCard
import com.assetmgmt.laptops.ui.components.StatCard
import com.assetmgmt.laptops.ui.components.StatusBadge
import com.assetmgmt.laptops.ui.theme.Indigo500
import com.assetmgmt.laptops.ui.theme.StatusAssigned
import com.assetmgmt.laptops.ui.theme.StatusAvailable
import com.assetmgmt.laptops.ui.theme.StatusDead
import com.assetmgmt.laptops.ui.theme.StatusRepair
import com.assetmgmt.laptops.ui.theme.StatusRetired
import com.assetmgmt.laptops.util.fmtDate
import com.assetmgmt.laptops.util.fmtInt
import com.assetmgmt.laptops.util.fmtMoney
import com.assetmgmt.laptops.util.nz
import com.assetmgmt.laptops.util.prettyEnum
import com.assetmgmt.laptops.viewmodel.DetailViewModel
import com.assetmgmt.laptops.viewmodel.QueryListViewModel
import com.assetmgmt.laptops.viewmodel.appContainer
import com.assetmgmt.laptops.viewmodel.appViewModel

val AssetStatusOptions = listOf(
    "AVAILABLE" to "Available",
    "ASSIGNED" to "Assigned",
    "REPAIR" to "Repair",
    "DEAD" to "Dead",
    "SOLD" to "Sold",
    "RETIRED" to "Retired",
)

// ---------------------------------------------------------------- Assets (assigned laptops)

@Composable
fun AssetsScreen(nav: NavHostController) {
    val repo = appContainer().repo
    val admin = isAdmin()
    val vm = appViewModel<QueryListViewModel<Asset>> { r ->
        QueryListViewModel(r, mapOf("assigned" to "true")) { rp, q -> rp.assets(q) }
    }
    DrawerScaffold(
        nav, Routes.ASSETS, "Assets",
        actions = { if (admin) IconButton({ nav.navigate(Routes.assetForm()) }) { Icon(Icons.Default.Add, "Add laptop") } },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.assign()) },
                icon = { Icon(Icons.Default.AssignmentInd, null) },
                text = { Text("Assign laptop") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            QueryList(
                vm = vm, repo = repo,
                searchHint = "Search employee, code, asset ID, serial",
                emptyTitle = "No assigned laptops",
                emptyMessage = "Assign a laptop from stock to an employee.",
                filterConfig = FilterConfig(
                    branch = true, employee = true,
                    statuses = listOf("ASSIGNED" to "Assigned", "REPAIR" to "Repair"),
                    repairCount = true, cost = true, dateLabel = "Assigned",
                ),
                keepFilters = setOf("assigned"),
                key = { it.assetId },
            ) { i, a ->
                AssetCard(a, index = i, onClick = { nav.navigate(Routes.asset(a.assetId)) }) {
                    Text(
                        "Assigned on ${fmtDate(a.assignmentDate)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Stock

@Composable
fun StockScreen(nav: NavHostController) {
    val admin = isAdmin()
    val vm = appViewModel<DetailViewModel<StockOverview>> { DetailViewModel(it) { r -> r.stock() } }
    DrawerScaffold(
        nav, Routes.STOCK, "Stock",
        floatingActionButton = {
            if (admin) ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.assetForm()) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add laptop") },
            )
        },
    ) { pad ->
        DetailBody(vm, pad) { s -> StockContent(nav, s) }
    }
}

@Composable
private fun StockContent(nav: NavHostController, s: StockOverview) {
    val t = s.summary
    ScrollPage {
        val tiles = listOf(
            Triple("Total Laptops", fmtInt(t.total), Pair(Icons.Default.Laptop, Indigo500)) to "ALL",
            Triple("Available", fmtInt(t.available), Pair(Icons.Default.CheckCircle, StatusAvailable)) to "AVAILABLE",
            Triple("Assigned", fmtInt(t.assigned), Pair(Icons.Default.PersonPin, StatusAssigned)) to "ASSIGNED",
            Triple("Under Repair", fmtInt(t.repair), Pair(Icons.Default.Build, StatusRepair)) to "REPAIR",
            Triple("Dead", fmtInt(t.dead), Pair(Icons.Default.ReportProblem, StatusDead)) to "DEAD",
            Triple("Sold", fmtInt(t.sold), Pair(Icons.Default.Archive, StatusRetired)) to "SOLD",
            Triple("Retired", fmtInt(t.retired), Pair(Icons.Default.Archive, StatusRetired)) to "RETIRED",
        )
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (tile, status) ->
                    StatCard(tile.first, tile.second, tile.third.first, tile.third.second, Modifier.weight(1f)) {
                        nav.navigate(Routes.stockList(status))
                    }
                }
            }
        }
        SectionCard("Branch-wise stock") {
            if (s.branches.isEmpty()) {
                Text("No branches yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                DataTable(
                    cols = listOf(
                        Col("Branch", 140.dp), Col("Total", 62.dp, numeric = true), Col("Assigned", 80.dp, numeric = true),
                        Col("Available", 84.dp, numeric = true), Col("Repair", 70.dp, numeric = true),
                        Col("Dead", 62.dp, numeric = true), Col("Sold", 62.dp, numeric = true), Col("Retired", 72.dp, numeric = true),
                    ),
                    rows = s.branches.map { listOf(it.branchName, "${it.total}", "${it.assigned}", "${it.available}", "${it.repair}", "${it.dead}", "${it.sold}", "${it.retired}") },
                    footer = listOf("Total", "${t.total}", "${t.assigned}", "${t.available}", "${t.repair}", "${t.dead}", "${t.sold}", "${t.retired}"),
                    onRowClick = { nav.navigate(Routes.stockList("ALL", s.branches[it].branchId)) },
                )
            }
        }
    }
}

/** Laptops of one status (or all) optionally within one branch. */
@Composable
fun StockListScreen(nav: NavHostController, status: String, branchId: String?) {
    val repo = appContainer().repo
    val admin = isAdmin()
    val bId = branchId.nz()
    val fixedStatus = status.takeIf { it != "ALL" }
    val vm = appViewModel<QueryListViewModel<Asset>>(key = "stock-$status-$bId") { r ->
        val initial = buildMap {
            fixedStatus?.let { put("status", it) }
            bId?.let { put("branchId", it) }
        }
        QueryListViewModel(r, initial) { rp, q -> rp.stockAssets(q) }
    }
    val title = if (fixedStatus == null) "All Laptops" else "${prettyEnum(fixedStatus)} Laptops"
    ScreenScaffold(nav, title) { pad ->
        Column(Modifier.padding(pad)) {
            QueryList(
                vm = vm, repo = repo,
                searchHint = "Search asset ID, serial, model, employee",
                emptyTitle = "No laptops found",
                emptyMessage = "Nothing matches the current filters.",
                filterConfig = FilterConfig(branch = bId == null, statuses = if (fixedStatus == null) AssetStatusOptions else emptyList(), repairCount = true, cost = true),
                keepFilters = setOfNotNull(fixedStatus?.let { "status" }, bId?.let { "branchId" }),
                key = { it.assetId },
            ) { i, a ->
                AssetCard(a, index = i, onClick = { nav.navigate(Routes.asset(a.assetId)) }) {
                    if (a.status == "AVAILABLE") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${a.processor.orEmpty()} ${a.ram.orEmpty()}".trim(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton({ nav.navigate(Routes.assign(branchId = a.currentBranchId, assetId = a.assetId)) }) { Text("Assign") }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Dead laptops

@Composable
fun DeadScreen(nav: NavHostController) {
    val repo = appContainer().repo
    val vm = appViewModel<QueryListViewModel<Asset>>(key = "dead") { r ->
        QueryListViewModel(r, mapOf("status" to "DEAD")) { rp, q -> rp.assets(q) }
    }
    DrawerScaffold(nav, Routes.DEAD, "Dead Laptops") { pad ->
        Column(Modifier.padding(pad)) {
            QueryList(
                vm = vm, repo = repo,
                searchHint = "Search asset ID, serial, model, employee",
                emptyTitle = "No dead laptops",
                emptyMessage = "Laptops marked as dead will be listed here with their history.",
                filterConfig = FilterConfig(branch = true, employee = true, repairCount = true, cost = true),
                keepFilters = setOf("status"),
                key = { it.assetId },
            ) { i, a ->
                AssetCard(a, index = i, onClick = { nav.navigate(Routes.asset(a.assetId)) }) {
                    val d = a.deadInfo
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Reason: ${d?.reason ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Dead on ${fmtDate(d?.deadDate)}  ·  ${a.repairCount} repairs  ·  ${fmtMoney(a.totalRepairCost)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun HandoverScreen(nav: NavHostController) {
    val repo = appContainer().repo
    val admin = isAdmin()
    val vm = appViewModel<QueryListViewModel<Replacement>>(key = "handovers") { r ->
        QueryListViewModel(r) { rp, q -> rp.replacements(q) }
    }
    DrawerScaffold(
        nav, Routes.HANDOVER, "Laptop Handover",
        floatingActionButton = {
            if (admin) ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.assign()) },
                icon = { Icon(Icons.Default.AssignmentInd, null) },
                text = { Text("New handover") },
            )
        },
    ) { pad ->
        QueryList(
            vm = vm,
            repo = repo,
            modifier = Modifier.padding(pad),
            searchHint = "Search employee, branch, old or new serial",
            emptyTitle = "No handovers yet",
            emptyMessage = "Completed laptop replacements and handovers are retained here.",
            filterConfig = FilterConfig(
                branch = true,
                employee = true,
                reasons = listOf("HARDWARE_ISSUE" to "Hardware issue", "UPGRADE" to "Upgrade", "DEAD" to "Dead", "OTHER" to "Other"),
                dateLabel = "Handover",
            ),
            key = { it.replacementId },
        ) { _, handover ->
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${handover.employeeName.orEmpty()}  ·  ${handover.employeeCode.orEmpty()}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(handover.branchName, handover.designation).filter { it.isNotBlank() }.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Old: ${handover.oldAssetId} · ${handover.oldBrand.orEmpty()} ${handover.oldModel.orEmpty()} · ${handover.oldSerialNumber.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "New: ${handover.newAssetId} · ${handover.newBrand.orEmpty()} ${handover.newModel.orEmpty()} · ${handover.newSerialNumber.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${fmtDate(handover.handoverDate ?: handover.replacementDate)}  ·  ${prettyEnum(handover.handoverReason ?: handover.reason)}",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        StatusBadge(handover.handoverStatus ?: handover.status ?: "COMPLETED")
                    }
                    Text(
                        "Previous laptop: ${prettyEnum(handover.oldAssetStatus)}  ·  Current: ${handover.newAssetId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Previous assignment: ${fmtDate(handover.previousAssignmentDate)}  ·  Current assignment: ${fmtDate(handover.currentAssignmentDate)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    handover.handoverRemarks?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
