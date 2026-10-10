package com.assetmgmt.laptops.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.assetmgmt.laptops.data.remote.AssetDetails
import com.assetmgmt.laptops.data.remote.HistoryEvent
import com.assetmgmt.laptops.navigation.Routes
import com.assetmgmt.laptops.ui.components.AppCard
import com.assetmgmt.laptops.ui.components.AppTextField
import com.assetmgmt.laptops.ui.components.ConfirmDialog
import com.assetmgmt.laptops.ui.components.DropdownField
import com.assetmgmt.laptops.ui.components.InfoRow
import com.assetmgmt.laptops.ui.components.InlineError
import com.assetmgmt.laptops.ui.components.PrimaryButton
import com.assetmgmt.laptops.ui.components.SecondaryButton
import com.assetmgmt.laptops.ui.components.SectionCard
import com.assetmgmt.laptops.ui.components.StatusBadge
import com.assetmgmt.laptops.ui.theme.statusColor
import com.assetmgmt.laptops.util.fmtDate
import com.assetmgmt.laptops.util.fmtDateTime
import com.assetmgmt.laptops.util.fmtMoney
import com.assetmgmt.laptops.util.prettyEnum
import com.assetmgmt.laptops.util.rememberSubmit
import com.assetmgmt.laptops.viewmodel.DetailViewModel
import com.assetmgmt.laptops.viewmodel.appContainer
import com.assetmgmt.laptops.viewmodel.appViewModel

private val DeadReasons = listOf("Motherboard failure", "Physical damage", "Water damage", "Beyond economical repair", "Other")

@Composable
fun AssetDetailScreen(nav: NavHostController, id: String) {
    val admin = isAdmin()
    val vm = appViewModel<DetailViewModel<AssetDetails>>(key = "asset-$id") { DetailViewModel(it) { r -> r.assetDetails(id) } }
    ScreenScaffold(
        nav, "Laptop $id",
        actions = { if (admin) IconButton({ nav.navigate(Routes.assetForm(id)) }) { Icon(Icons.Default.Edit, "Edit laptop") } },
    ) { pad ->
        DetailBody(vm, pad) { d -> AssetDetailContent(nav, d, admin) }
    }
}

@Composable
private fun AssetDetailContent(nav: NavHostController, d: AssetDetails, admin: Boolean) {
    val a = d.asset
    val asg = d.currentAssignment
    var showDead by remember { mutableStateOf(false) }
    var showRetire by remember { mutableStateOf(false) }
    var showSold by remember { mutableStateOf(false) }
    var showReturn by remember { mutableStateOf(false) }

    ScrollPage {
        AppCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.title.ifBlank { a.assetId }, style = MaterialTheme.typography.titleLarge)
                    Text("${a.assetId}  ·  ${a.serialNumber}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(a.branchName.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusBadge(a.status)
            }
        }

        // ---- actions (by status and role)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (a.status) {
                "AVAILABLE" -> {
                    PrimaryButton("Assign to employee", { nav.navigate(Routes.assign(branchId = a.currentBranchId, assetId = a.assetId)) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("Send for repair", { nav.navigate(Routes.repairForm(a.assetId)) }, Modifier.weight(1f))
                        if (admin) SecondaryButton("Mark dead", { showDead = true }, Modifier.weight(1f))
                    }
                    if (admin) SecondaryButton("Retire laptop", { showRetire = true }, Modifier.fillMaxWidth())
                }
                "ASSIGNED" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("Send for repair", { nav.navigate(Routes.repairForm(a.assetId)) }, Modifier.weight(1f))
                        if (asg != null) SecondaryButton("Return to stock", { showReturn = true }, Modifier.weight(1f))
                    }
                    if (admin) SecondaryButton("Mark dead", { showDead = true }, Modifier.fillMaxWidth())
                }
                "REPAIR" -> {
                    val open = d.repairs.firstOrNull { it.status in listOf("SENT_FOR_REPAIR", "UNDER_REPAIR", "NOT_REPAIRABLE", "REPAIRED") }
                    if (open != null) PrimaryButton("Open current repair", { nav.navigate(Routes.repair(open.repairId)) })
                    if (admin) SecondaryButton("Mark dead", { showDead = true }, Modifier.fillMaxWidth())
                }
                "DEAD" -> if (admin) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("Mark sold", { showSold = true }, Modifier.weight(1f))
                        SecondaryButton("Retire laptop", { showRetire = true }, Modifier.weight(1f))
                    }
                }
            }
        }

        if (a.status == "DEAD" && a.deadInfo != null) {
            SectionCard("Dead laptop") {
                InfoRow("Reason", a.deadInfo.reason)
                InfoRow("Dead date", fmtDate(a.deadInfo.deadDate))
                InfoRow("Remarks", a.deadInfo.remarks)
                InfoRow("Previous employee", a.displayEmployeeName)
            }
        }
        if (a.status == "SOLD" && a.soldInfo != null) {
            SectionCard("Sold laptop") {
                InfoRow("Sold date", fmtDate(a.soldInfo.soldDate))
                InfoRow("Remarks", a.soldInfo.remarks)
                InfoRow("Previous employee", a.displayEmployeeName)
            }
        }

        if (asg != null) {
            SectionCard("Current assignment") {
                InfoRow("Employee", asg.employeeName, onClick = { nav.navigate(Routes.employee(asg.employeeId)) })
                InfoRow("Employee code", asg.employeeCode)
                InfoRow("Designation", asg.employeeDesignation)
                InfoRow("Branch", asg.branchName ?: a.branchName)
                InfoRow("Assigned on", fmtDate(asg.assignmentDate))
                InfoRow("Remarks", asg.remarks)
            }
        }

        SectionCard("Laptop information") {
            InfoRow("Asset tag", a.assetTag)
            InfoRow("Serial number", a.serialNumber)
            InfoRow("Brand / model", a.title)
            InfoRow("Processor", a.processor)
            InfoRow("RAM", a.ram)
            InfoRow("Storage", a.storage)
            InfoRow("Operating system", a.operatingSystem)
            InfoRow("Branch", a.branchName, onClick = { nav.navigate(Routes.branch(a.currentBranchId)) })
            InfoRow("Purchase date", a.purchaseDate?.let { fmtDate(it) })
            InfoRow("Purchase price", a.purchasePrice?.let { fmtMoney(it) })
            InfoRow("Warranty", if (a.warrantyStartDate == null && a.warrantyEndDate == null) null else "${fmtDate(a.warrantyStartDate)} → ${fmtDate(a.warrantyEndDate)}")
        }

        val rs = d.repairSummary
        SectionCard("Repair summary") {
            InfoRow("Repair count", "${rs.repairCount}")
            InfoRow("Total repair cost", fmtMoney(rs.totalRepairCost))
            InfoRow("Last repair date", rs.lastRepairDate?.let { fmtDate(it) })
            InfoRow("Latest repair cost", if (rs.repairCount == 0) null else fmtMoney(rs.latestRepairCost))
            InfoRow("Average repair cost", if (rs.repairCount == 0) null else fmtMoney(rs.averageRepairCost))
            InfoRow("Highest repair cost", if (rs.repairCount == 0) null else fmtMoney(rs.highestRepairCost))
            if (d.repairs.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.repairs.forEach { r -> RepairCard(r) { nav.navigate(Routes.repair(r.repairId)) } }
                }
            }
        }

        d.replacementSummary?.let { rp ->
            SectionCard("Replacement") {
                InfoRow("Replaced laptop", rp.previousAssetId, onClick = { nav.navigate(Routes.asset(rp.previousAssetId)) })
                InfoRow("Replacement date", fmtDate(rp.replacementDate))
                InfoRow("Reason", prettyEnum(rp.reason))
                InfoRow("Employee", rp.employeeName)
            }
        }
        if (d.replacements.isNotEmpty()) {
            SectionCard("Replacement history") {
                d.replacements.forEachIndexed { i, r ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    InfoRow("${r.oldAssetId} → ${r.newAssetId}", "${fmtDate(r.replacementDate)}\n${prettyEnum(r.reason)}")
                }
            }
        }
        if (d.assignments.isNotEmpty()) {
            SectionCard("Assignment history") {
                d.assignments.forEachIndexed { i, s ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    InfoRow(s.employeeName ?: s.employeeId, "${fmtDate(s.assignmentDate)} → ${if (s.releaseDate == null) "Present" else fmtDate(s.releaseDate)}", onClick = { nav.navigate(Routes.employee(s.employeeId)) })
                }
            }
        }
        SectionCard("Lifecycle history") { Timeline(d.lifecycle) }
    }

    if (showDead) MarkDeadSheet(d) { showDead = false }
    if (showRetire) RetireDialog(d) { showRetire = false }
    if (showSold) SoldDialog(d) { showSold = false }
    if (showReturn && asg != null) ReturnSheet(asg.assignmentId, a.assetId) { showReturn = false }
}

@Composable
private fun SoldDialog(d: AssetDetails, onDismiss: () -> Unit) {
    val repo = appContainer().repo
    val submit = rememberSubmit()
    ConfirmDialog(
        title = "Mark laptop as sold?",
        message = "${d.asset.assetId} will move from Dead to Sold. It will remain in the history and cannot be assigned.",
        confirmText = "Mark sold",
        destructive = true,
        busy = submit.busy,
        error = submit.error,
        onConfirm = { submit.run({ repo.markSold(d.asset.assetId) }, { onDismiss() }) },
        onDismiss = onDismiss,
    )
}

@Composable
private fun Timeline(events: List<HistoryEvent>) {
    if (events.isEmpty()) {
        Text("No lifecycle events yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column {
        events.forEachIndexed { i, e ->
            val color = statusColor(e.newStatus)
            Row(Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(20.dp)) {
                    Box(Modifier.padding(top = 4.dp).size(12.dp).clip(CircleShape).background(color))
                    if (i < events.lastIndex) Box(Modifier.width(2.dp).height(54.dp).background(MaterialTheme.colorScheme.outlineVariant))
                }
                Column(Modifier.padding(start = 8.dp, bottom = 12.dp)) {
                    Text(prettyEnum(e.eventType), style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(e.employeeName, e.branchName).joinToString("  ·  ").ifBlank { "—" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (e.previousStatus != null || e.newStatus != null) {
                        Text("${prettyEnum(e.previousStatus)} → ${prettyEnum(e.newStatus)}", style = MaterialTheme.typography.bodySmall, color = color)
                    }
                    Text(
                        fmtDateTime(e.date) + (e.user?.let { "  ·  $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    e.remarks?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun MarkDeadSheet(d: AssetDetails, onDismiss: () -> Unit) {
    val repo = appContainer().repo
    val submit = rememberSubmit()
    var reason by remember { mutableStateOf<String?>(null) }
    var other by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    val a = d.asset
    ActionSheet("Mark laptop as dead", onDismiss) {
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                InfoRow("Laptop", "${a.assetId} · ${a.serialNumber}")
                InfoRow("Employee", d.currentAssignment?.employeeName ?: a.employeeName)
                InfoRow("Branch", a.branchName)
                InfoRow("Repair total", "${d.repairSummary.repairCount} repairs · ${fmtMoney(d.repairSummary.totalRepairCost)}")
            }
        }
        DropdownField("Reason", DeadReasons.map { it to it }, reason, { reason = it }, required = true)
        if (reason == "Other") AppTextField(other, { other = it }, "Describe the reason", required = true)
        AppTextField(remarks, { remarks = it }, "Remarks", singleLine = false)
        InlineError(submit.error)
        val finalReason = if (reason == "Other") other.trim() else reason.orEmpty()
        PrimaryButton("Mark as dead", {
            submit.run({ repo.markDead(a.assetId, finalReason, remarks) }, { onDismiss() })
        }, enabled = finalReason.isNotBlank(), busy = submit.busy)
    }
}

@Composable
private fun RetireDialog(d: AssetDetails, onDismiss: () -> Unit) {
    val repo = appContainer().repo
    val submit = rememberSubmit()
    ConfirmDialog(
        title = "Retire laptop?",
        message = "${d.asset.assetId} will be marked as retired and removed from active stock. Its full history is kept.",
        confirmText = "Retire",
        destructive = true,
        busy = submit.busy,
        error = submit.error,
        onConfirm = { submit.run({ repo.retireAsset(d.asset.assetId, null) }, { onDismiss() }) },
        onDismiss = onDismiss,
    )
}

@Composable
private fun ReturnSheet(assignmentId: String, assetId: String, onDismiss: () -> Unit) {
    val repo = appContainer().repo
    val submit = rememberSubmit()
    var remarks by remember { mutableStateOf("") }
    ActionSheet("Return $assetId to stock", onDismiss) {
        Text("The laptop becomes available again and the employee is left without a laptop.", style = MaterialTheme.typography.bodyMedium)
        AppTextField(remarks, { remarks = it }, "Remarks", singleLine = false)
        InlineError(submit.error)
        PrimaryButton("Return to stock", { submit.run({ repo.returnLaptop(assignmentId, remarks) }, { onDismiss() }) }, busy = submit.busy)
    }
}
