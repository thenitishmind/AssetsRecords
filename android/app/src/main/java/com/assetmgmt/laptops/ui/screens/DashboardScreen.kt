package com.assetmgmt.laptops.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.assetmgmt.laptops.data.remote.Dashboard
import com.assetmgmt.laptops.navigation.DrawerScaffold
import com.assetmgmt.laptops.navigation.Routes
import com.assetmgmt.laptops.ui.components.Bar
import com.assetmgmt.laptops.ui.components.BarChart
import com.assetmgmt.laptops.ui.components.Col
import com.assetmgmt.laptops.ui.components.DataTable
import com.assetmgmt.laptops.ui.components.DonutChart
import com.assetmgmt.laptops.ui.components.EmptyState
import com.assetmgmt.laptops.ui.components.ErrorBox
import com.assetmgmt.laptops.ui.components.InlineError
import com.assetmgmt.laptops.ui.components.Legend
import com.assetmgmt.laptops.ui.components.LoadingBox
import com.assetmgmt.laptops.ui.components.SectionCard
import com.assetmgmt.laptops.ui.components.Slice
import com.assetmgmt.laptops.ui.components.StackedStatusBar
import com.assetmgmt.laptops.ui.components.StatCard
import com.assetmgmt.laptops.ui.components.StatusSlices
import com.assetmgmt.laptops.ui.theme.Indigo500
import com.assetmgmt.laptops.ui.theme.StatusAssigned
import com.assetmgmt.laptops.ui.theme.StatusAvailable
import com.assetmgmt.laptops.ui.theme.StatusDead
import com.assetmgmt.laptops.ui.theme.StatusRepair
import com.assetmgmt.laptops.ui.theme.StatusRetired
import com.assetmgmt.laptops.util.fmtInt
import com.assetmgmt.laptops.util.fmtMoney
import com.assetmgmt.laptops.viewmodel.DetailUi
import com.assetmgmt.laptops.viewmodel.DetailViewModel
import com.assetmgmt.laptops.viewmodel.appViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(nav: NavHostController) {
    val vm = appViewModel<DetailViewModel<Dashboard>> { DetailViewModel(it) { r -> r.dashboard() } }
    val state by vm.ui.collectAsState()

    DrawerScaffold(nav, Routes.DASHBOARD, "Dashboard") { pad ->
        when (val s = state) {
            DetailUi.Loading -> LoadingBox(Modifier.padding(pad))
            is DetailUi.Error -> ErrorBox(s.message, { vm.load() }, Modifier.padding(pad))
            is DetailUi.Data -> PullToRefreshBox(isRefreshing = s.refreshing, onRefresh = { vm.load(silent = true) }, modifier = Modifier.padding(pad).fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    InlineError(s.error, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    DashboardContent(nav, s.value, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(nav: NavHostController, d: Dashboard, modifier: Modifier = Modifier) {
    val t = d.totals
    BoxWithConstraints(modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 720.dp) 4 else 2
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val cards = listOf(
                Triple("Total Assets", fmtInt(t.totalAssets), Icons.Default.Laptop to Indigo500),
                Triple("Assigned Laptops", fmtInt(t.assigned), Icons.Default.PersonPin to StatusAssigned),
                Triple("Available Stock", fmtInt(t.available), Icons.Default.CheckCircle to StatusAvailable),
                Triple("Repair Assets", fmtInt(t.repair), Icons.Default.Build to StatusRepair),
                Triple("Dead Laptops", fmtInt(t.dead), Icons.Default.ReportProblem to StatusDead),
                Triple("Sold Laptops", fmtInt(t.sold), Icons.Default.Archive to StatusRetired),
                Triple("Branches", fmtInt(t.branches), Icons.Default.Apartment to Indigo500),
                Triple("Employees with assets", fmtInt(t.employeesWithAssets), Icons.Default.People to StatusAssigned),
                Triple("Total Repair Cost", fmtMoney(t.totalRepairCost), Icons.Default.CurrencyRupee to StatusRepair),
            )
            val targets = listOf<() -> Unit>(
                { nav.navigate(Routes.stockList("ALL")) },
                { nav.navigate(Routes.ASSETS) },
                { nav.navigate(Routes.stockList("AVAILABLE")) },
                { nav.navigate(Routes.REPAIRS) },
                { nav.navigate(Routes.DEAD) },
                { nav.navigate(Routes.stockList("SOLD")) },
                { nav.navigate(Routes.BRANCHES) },
                { nav.navigate(Routes.EMPLOYEES) },
                { nav.navigate(Routes.REPORTS) },
            )
            cards.chunked(columns).forEachIndexed { row, chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    chunk.forEachIndexed { i, (title, value, ic) ->
                        StatCard(title, value, ic.first, ic.second, Modifier.weight(1f), onClick = targets[row * columns + i])
                    }
                    repeat(columns - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            StatusChartCard(d)
            BranchWiseCard(nav, d)
            RepairCostCard(d)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusChartCard(d: Dashboard) {
    val c = d.totals
    val slices = StatusSlices(c.assigned, c.available, c.repair, c.dead, c.sold, c.retired)
    SectionCard("Asset status") {
        if (c.totalAssets == 0) {
            EmptyState("No laptops yet", "Add laptops from the Assets or Stock sections.")
        } else {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                DonutChart(slices, fmtInt(c.totalAssets), "laptops")
                Spacer(Modifier.width(18.dp))
                Legend(slices, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BranchWiseCard(nav: NavHostController, d: Dashboard) {
    SectionCard("Branch-wise assets") {
        if (d.branchWise.isEmpty()) {
            EmptyState("No branches yet")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                d.branchWise.forEach { b -> StackedStatusBar(b) }
                DataTable(
                    cols = listOf(
                        Col("Branch Name", 140.dp),
                        Col("Total", 64.dp, numeric = true),
                        Col("Assigned", 80.dp, numeric = true),
                        Col("Available", 84.dp, numeric = true),
                        Col("Repair", 70.dp, numeric = true),
                        Col("Dead", 64.dp, numeric = true),
                        Col("Sold", 64.dp, numeric = true),
                        Col("Retired", 76.dp, numeric = true),
                    ),
                    rows = d.branchWise.map { listOf(it.branchName, "${it.total}", "${it.assigned}", "${it.available}", "${it.repair}", "${it.dead}", "${it.sold}", "${it.retired}") },
                    onRowClick = { nav.navigate(Routes.branch(d.branchWise[it].branchId)) },
                )
            }
        }
    }
}

@Composable
private fun RepairCostCard(d: Dashboard) {
    SectionCard("Repair cost") {
        if (d.repairCostByMonth.isEmpty()) {
            EmptyState("No repairs recorded", "Repair expenses will be charted here.")
            return@SectionCard
        }
        Text("Month-wise", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        BarChart(
            bars = d.repairCostByMonth.map { Bar(monthLabel(it.month), it.cost) },
            color = StatusRepair,
            valueFormat = { shortMoney(it) },
        )
        Spacer(Modifier.height(16.dp))
        Text("Branch-wise", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        val max = (d.repairCostByBranch.maxOfOrNull { it.cost } ?: 0.0).coerceAtLeast(1.0)
        d.repairCostByBranch.forEach { b ->
            Column(Modifier.padding(vertical = 5.dp)) {
                Row {
                    Text(b.branchName ?: "—", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(fmtMoney(b.cost), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(4.dp))
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth((b.cost / max).toFloat().coerceAtLeast(0.02f)).height(8.dp)
                        .background(StatusRepair, androidx.compose.foundation.shape.RoundedCornerShape(50)),
                )
            }
        }
    }
}

private fun monthLabel(ym: String): String = runCatching {
    val (y, m) = ym.split("-").map { it.toInt() }
    LocalDate.of(y, m, 1).month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + (y % 100).toString().padStart(2, '0')
}.getOrDefault(ym)

private fun shortMoney(v: Double): String = when {
    v >= 10_000_000 -> "₹%.1fCr".format(v / 10_000_000)
    v >= 100_000 -> "₹%.1fL".format(v / 100_000)
    v >= 1_000 -> "₹%.1fK".format(v / 1_000)
    else -> "₹%.0f".format(v)
}
