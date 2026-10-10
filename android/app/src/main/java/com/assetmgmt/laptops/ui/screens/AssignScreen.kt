package com.assetmgmt.laptops.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.assetmgmt.laptops.data.remote.Asset
import com.assetmgmt.laptops.data.repository.body
import com.assetmgmt.laptops.data.repository.query
import com.assetmgmt.laptops.navigation.Routes
import com.assetmgmt.laptops.ui.components.AppCard
import com.assetmgmt.laptops.ui.components.AppTextField
import com.assetmgmt.laptops.ui.components.DateField
import com.assetmgmt.laptops.ui.components.DropdownField
import com.assetmgmt.laptops.ui.components.InfoRow
import com.assetmgmt.laptops.ui.components.InlineError
import com.assetmgmt.laptops.ui.components.Option
import com.assetmgmt.laptops.ui.components.PrimaryButton
import com.assetmgmt.laptops.ui.components.RemotePickerField
import com.assetmgmt.laptops.ui.components.StatusBadge
import com.assetmgmt.laptops.ui.components.WarningCard
import com.assetmgmt.laptops.ui.components.branchOptions
import com.assetmgmt.laptops.ui.components.employeeOptions
import com.assetmgmt.laptops.util.fmtDate
import com.assetmgmt.laptops.util.nz
import com.assetmgmt.laptops.util.rememberSubmit
import com.assetmgmt.laptops.util.today
import com.assetmgmt.laptops.viewmodel.appContainer

private val ReplaceReasons = listOf(
    "HARDWARE_ISSUE" to "Hardware issue",
    "UPGRADE" to "Upgrade",
    "DEAD" to "Laptop is dead",
    "OTHER" to "Other",
)
private val OldLaptopOutcomes = listOf("AVAILABLE" to "Available", "REPAIR" to "Repair", "DEAD" to "Dead", "RETIRED" to "Retired")
private val DefaultOutcome = mapOf("HARDWARE_ISSUE" to "REPAIR", "UPGRADE" to "AVAILABLE", "DEAD" to "DEAD")
private val DeadReasonList = listOf("Motherboard failure", "Physical damage", "Water damage", "Beyond economical repair", "Other")

/**
 * Assign an available laptop to an employee. If the employee already holds a laptop the
 * screen switches to the replacement flow (old -> new) which only administrators may run.
 */
@Composable
fun AssignScreen(nav: NavHostController, branchIdArg: String?, employeeIdArg: String?, assetIdArg: String?) {
    val repo = appContainer().repo
    val admin = isAdmin()
    val submit = rememberSubmit()
    val presetBranch = branchIdArg.nz()
    val presetEmployee = employeeIdArg.nz()
    val presetAsset = assetIdArg.nz()

    var branch by remember { mutableStateOf<Option?>(null) }
    var employee by remember { mutableStateOf<Option?>(null) }
    var asset by remember { mutableStateOf<Option?>(null) }
    var date by remember { mutableStateOf(today()) }
    var remarks by remember { mutableStateOf("") }
    val assetCache = remember { mutableMapOf<String, Asset>() }

    var current by remember { mutableStateOf<Asset?>(null) }      // laptop the employee already holds
    var checking by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf<String?>(null) }
    var oldStatus by remember { mutableStateOf<String?>(null) }
    var deadReason by remember { mutableStateOf<String?>(null) }

    // Pre-fill from navigation arguments.
    LaunchedEffect(presetBranch, presetEmployee, presetAsset) {
        var branchId = presetBranch
        if (presetAsset != null) {
            repo.assetDetails(presetAsset).onSuccess {
                val a = it.asset
                assetCache[a.assetId] = a
                asset = Option(a.assetId, "${a.assetId} · ${a.serialNumber}", a.title)
                branchId = a.currentBranchId
            }
        }
        if (presetEmployee != null) {
            repo.employee(presetEmployee).onSuccess {
                employee = Option(it.employeeId, it.name, "${it.employeeCode} · ${it.branchName.orEmpty()}")
                branchId = it.branchId
            }
        }
        branchId?.let { id -> repo.branch(id).onSuccess { branch = Option(it.branchId, it.name, "${it.code} · ${it.city}") } }
    }

    // Does the selected employee already hold a laptop?
    LaunchedEffect(employee?.id) {
        current = null
        reason = null; oldStatus = null; deadReason = null
        val id = employee?.id ?: return@LaunchedEffect
        checking = true
        repo.employeeAssets(id).onSuccess { current = it.current }
        checking = false
    }

    val replacing = current != null
    val selectedAsset = asset?.let { assetCache[it.id] }

    ScreenScaffold(nav, if (replacing) "Replace laptop" else "Assign laptop") { pad ->
        FormPage(pad) {
            RemotePickerField(
                "Branch", branch,
                { branch = it; employee = null; asset = null },
                { branchOptions(repo, it) },
                required = true, enabled = presetBranch == null && presetAsset == null && presetEmployee == null,
            )
            RemotePickerField(
                "Employee", employee, { employee = it },
                { employeeOptions(repo, it, branch?.id) },
                required = true, enabled = branch != null && presetEmployee == null, loaderKey = branch?.id,
            )
            if (replacing) {
                val c = current!!
                WarningCard("Employee already has an assigned laptop.", "Assigning a new laptop replaces the current one.") {
                    InfoRow("Current laptop", "${c.assetId} · ${c.serialNumber}")
                    InfoRow("Model", c.title)
                    InfoRow("Assigned since", fmtDate(c.assignmentDate))
                    InfoRow("Repairs", "${c.repairCount}")
                    Spacer(Modifier.height(4.dp))
                    StatusBadge(c.status)
                }
            }
            RemotePickerField(
                "Available laptop", asset, { asset = it },
                { q ->
                    repo.stockAssets(query("q" to q, "status" to "AVAILABLE", "branchId" to branch?.id, "limit" to 200)).getOrThrow().items.map {
                        assetCache[it.assetId] = it
                        Option(it.assetId, "${it.assetId} · ${it.serialNumber}", "${it.title} ${it.ram.orEmpty()} ${it.storage.orEmpty()}".trim())
                    }
                },
                required = true, enabled = branch != null && presetAsset == null, loaderKey = branch?.id,
            )
            selectedAsset?.let { a ->
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Laptop details", style = MaterialTheme.typography.titleSmall)
                        InfoRow("Model", a.title)
                        InfoRow("Serial number", a.serialNumber)
                        InfoRow("Processor", a.processor)
                        InfoRow("RAM / Storage", listOfNotNull(a.ram, a.storage).joinToString(" / "))
                        InfoRow("Operating system", a.operatingSystem)
                    }
                }
            }
            DateField(if (replacing) "Replacement date" else "Assignment date", date, { date = it }, required = true)

            if (replacing) {
                DropdownField(
                    "Replacement reason", ReplaceReasons, reason,
                    { reason = it; oldStatus = it?.let { r -> DefaultOutcome[r] } },
                    required = true,
                )
                DropdownField("Old laptop status after replacement", OldLaptopOutcomes, oldStatus, { oldStatus = it }, required = reason == "OTHER")
                if (oldStatus == "DEAD" || reason == "DEAD") {
                    DropdownField("Dead reason", DeadReasonList.map { it to it }, deadReason, { deadReason = it }, allowClear = true)
                }
            }
            AppTextField(remarks, { remarks = it }, "Remarks", singleLine = false)
            InlineError(submit.error)

            if (replacing && !admin) {
                Text("Only an administrator can replace a laptop.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            val ready = employee != null && asset != null && !checking &&
                (!replacing || (admin && reason != null && (reason != "OTHER" || oldStatus != null)))
            PrimaryButton(if (replacing) "Replace laptop" else "Assign laptop", {
                val newId = asset!!.id
                if (replacing) {
                    submit.run({
                        repo.replace(
                            body(
                                "employeeId" to employee!!.id, "newAssetId" to newId, "reason" to reason,
                                "oldAssetStatus" to oldStatus, "deadReason" to deadReason,
                                "replacementDate" to date, "remarks" to remarks,
                            ),
                        )
                    }, { nav.popBackStack(); nav.navigate(Routes.asset(newId)) })
                } else {
                    submit.run({
                        repo.assign(
                            body(
                                "branchId" to branch!!.id, "employeeId" to employee!!.id, "assetId" to newId,
                                "assignmentDate" to date, "remarks" to remarks,
                            ),
                        )
                    }, { nav.popBackStack(); nav.navigate(Routes.asset(newId)) })
                }
            }, enabled = ready, busy = submit.busy || checking)
        }
    }
}
