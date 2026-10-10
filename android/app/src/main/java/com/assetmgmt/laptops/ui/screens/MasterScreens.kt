package com.assetmgmt.laptops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import com.assetmgmt.laptops.data.remote.Branch
import com.assetmgmt.laptops.data.remote.BranchAssets
import com.assetmgmt.laptops.data.remote.Employee
import com.assetmgmt.laptops.data.remote.EmployeeAssets
import com.assetmgmt.laptops.data.remote.Asset
import com.assetmgmt.laptops.data.repository.body
import com.assetmgmt.laptops.data.repository.query
import com.assetmgmt.laptops.navigation.DrawerScaffold
import com.assetmgmt.laptops.navigation.Routes
import com.assetmgmt.laptops.ui.components.AppCard
import com.assetmgmt.laptops.ui.components.AppTextField
import com.assetmgmt.laptops.ui.components.AssetCard
import com.assetmgmt.laptops.ui.components.ConfirmDialog
import com.assetmgmt.laptops.ui.components.DateField
import com.assetmgmt.laptops.ui.components.DropdownField
import com.assetmgmt.laptops.ui.components.EmptyState
import com.assetmgmt.laptops.ui.components.ErrorBox
import com.assetmgmt.laptops.ui.components.FilterConfig
import com.assetmgmt.laptops.ui.components.InfoRow
import com.assetmgmt.laptops.ui.components.InlineError
import com.assetmgmt.laptops.ui.components.LoadingBox
import com.assetmgmt.laptops.ui.components.Option
import com.assetmgmt.laptops.ui.components.PrimaryButton
import com.assetmgmt.laptops.ui.components.QueryList
import com.assetmgmt.laptops.ui.components.RemotePickerField
import com.assetmgmt.laptops.ui.components.SecondaryButton
import com.assetmgmt.laptops.ui.components.SectionCard
import com.assetmgmt.laptops.ui.components.StatusBadge
import com.assetmgmt.laptops.ui.components.StatCard
import com.assetmgmt.laptops.ui.components.branchOptions
import com.assetmgmt.laptops.ui.theme.Indigo500
import com.assetmgmt.laptops.ui.theme.StatusAssigned
import com.assetmgmt.laptops.ui.theme.StatusAvailable
import com.assetmgmt.laptops.ui.theme.StatusDead
import com.assetmgmt.laptops.ui.theme.StatusRepair
import com.assetmgmt.laptops.ui.theme.StatusRetired
import com.assetmgmt.laptops.util.fmtDate
import com.assetmgmt.laptops.util.fmtInt
import com.assetmgmt.laptops.util.fmtMoney
import com.assetmgmt.laptops.util.isoToDay
import com.assetmgmt.laptops.util.nz
import com.assetmgmt.laptops.util.prettyEnum
import com.assetmgmt.laptops.util.rememberSubmit
import com.assetmgmt.laptops.util.toMoneyOrNull
import com.assetmgmt.laptops.viewmodel.DetailViewModel
import com.assetmgmt.laptops.viewmodel.QueryListViewModel
import com.assetmgmt.laptops.viewmodel.appContainer
import com.assetmgmt.laptops.viewmodel.appViewModel

private val RecordStatus = listOf("ACTIVE" to "Active", "INACTIVE" to "Inactive")

/** Fetches the record being edited (when [id] is set) before showing a prefilled form. */
@Composable
private fun <T> LoadForEdit(pad: PaddingValues, id: String?, fetch: suspend () -> Result<T>, content: @Composable (T?) -> Unit) {
    if (id == null) {
        content(null)
        return
    }
    var state by remember(id) { mutableStateOf<Result<T>?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(id, retry) {
        state = null
        state = fetch()
    }
    val s = state
    when {
        s == null -> LoadingBox(Modifier.padding(pad))
        s.isFailure -> ErrorBox(s.exceptionOrNull()?.message ?: "Could not load the record.", { retry++ }, Modifier.padding(pad))
        else -> content(s.getOrNull())
    }
}

// ================================================================= Branches

@Composable
fun BranchesScreen(nav: NavHostController) {
    val repo = appContainer().repo
    val admin = isAdmin()
    val vm = appViewModel<QueryListViewModel<Branch>>(key = "branches") { r -> QueryListViewModel(r) { rp, q -> rp.branches(q) } }
    DrawerScaffold(
        nav, Routes.BRANCHES, "Branches",
        floatingActionButton = {
            if (admin) ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.branchForm()) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add branch") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            QueryList(
                vm = vm, repo = repo,
                searchHint = "Search branch name, code, city",
                emptyTitle = "No branches",
                emptyMessage = if (admin) "Add your first branch to start managing laptops." else "No branches have been created yet.",
                filterConfig = FilterConfig(branch = false, statuses = RecordStatus),
                key = { it.branchId },
            ) { _, b ->
                AppCard(Modifier.fillMaxWidth(), { nav.navigate(Routes.branch(b.branchId)) }) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(b.name, style = MaterialTheme.typography.titleMedium)
                                Text("${b.code}  ·  ${b.city}, ${b.state}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusBadge(b.status)
                        }
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                MiniStat("Laptops", b.totalAssets, MaterialTheme.colorScheme.primary)
                                MiniStat("Assigned", b.assigned, StatusAssigned)
                                MiniStat("Available", b.available, StatusAvailable)
                                MiniStat("Repair", b.repair, StatusRepair)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                MiniStat("Dead", b.dead, StatusDead)
                                MiniStat("Sold", b.sold, StatusRetired)
                                MiniStat("Retired", b.retired, StatusRetired)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(fmtInt(value), style = MaterialTheme.typography.titleSmall, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BranchDetailScreen(nav: NavHostController, id: String) {
    val admin = isAdmin()
    val vm = appViewModel<DetailViewModel<BranchAssets>>(key = "branch-$id") { DetailViewModel(it) { r -> r.branchAssets(id, query("limit" to 8)) } }
    ScreenScaffold(
        nav, "Branch",
        actions = { if (admin) IconButton({ nav.navigate(Routes.branchForm(id)) }) { Icon(Icons.Default.Edit, "Edit branch") } },
    ) { pad ->
        DetailBody(vm, pad) { d -> BranchDetailContent(nav, d, admin) }
    }
}

@Composable
private fun BranchDetailContent(nav: NavHostController, d: BranchAssets, admin: Boolean) {
    val b = d.branch
    var confirm by remember { mutableStateOf(false) }
    ScrollPage {
        AppCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.name, style = MaterialTheme.typography.titleLarge)
                    Text("${b.code}  ·  ${b.branchId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusBadge(b.status)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Laptops", fmtInt(b.totalAssets), Icons.Default.Laptop, Indigo500, Modifier.weight(1f)) { nav.navigate(Routes.stockList("ALL", b.branchId)) }
            StatCard("Employees", fmtInt(b.employeeCount), Icons.Default.People, StatusAssigned, Modifier.weight(1f))
        }
        SectionCard("Stock") {
            InfoRow("Assigned", "${b.assigned}")
            InfoRow("Available", "${b.available}", onClick = { nav.navigate(Routes.stockList("AVAILABLE", b.branchId)) })
            InfoRow("Under repair", "${b.repair}", onClick = { nav.navigate(Routes.stockList("REPAIR", b.branchId)) })
            InfoRow("Dead", "${b.dead}", onClick = { nav.navigate(Routes.stockList("DEAD", b.branchId)) })
            InfoRow("Sold", "${b.sold}", onClick = { nav.navigate(Routes.stockList("SOLD", b.branchId)) })
            InfoRow("Retired", "${b.retired}")
            InfoRow("Total repair cost", fmtMoney(b.repairCost))
        }
        SectionCard("Branch information") {
            InfoRow("City / State", "${b.city}, ${b.state}")
            InfoRow("Address", b.address)
            InfoRow("Contact", b.contactNumber)
            InfoRow("Created", fmtDate(b.createdAt))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (admin) SecondaryButton("Add employee", { nav.navigate(Routes.employeeForm(branchId = b.branchId)) }, Modifier.weight(1f))
            SecondaryButton("Assign laptop", { nav.navigate(Routes.assign(branchId = b.branchId)) }, Modifier.weight(1f))
        }
        SectionCard("Laptops in this branch") {
            if (d.assets.items.isEmpty()) {
                Text("No laptops in this branch yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.assets.items.forEach { a -> AssetCard(a, onClick = { nav.navigate(Routes.asset(a.assetId)) }) }
                    if (d.assets.hasMore) SecondaryButton("View all ${d.assets.total} laptops", { nav.navigate(Routes.stockList("ALL", b.branchId)) }, Modifier.fillMaxWidth())
                }
            }
        }
        if (admin && b.status == "ACTIVE") {
            SecondaryButton("Deactivate branch", { confirm = true }, Modifier.fillMaxWidth())
        }
    }
    if (confirm) {
        val repo = appContainer().repo
        val submit = rememberSubmit()
        ConfirmDialog(
            title = "Deactivate branch?",
            message = "${b.name} will be marked inactive. It can only be deactivated when it has no active employees or laptops in use or stock.",
            confirmText = "Deactivate", destructive = true, busy = submit.busy, error = submit.error,
            onConfirm = { submit.run({ repo.deactivateBranch(b.branchId) }, { confirm = false }) },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
fun BranchFormScreen(nav: NavHostController, idArg: String?) {
    val id = idArg.nz()
    val repo = appContainer().repo
    ScreenScaffold(nav, if (id == null) "Add branch" else "Edit branch") { pad ->
        LoadForEdit(pad, id, { repo.branch(id!!) }) { existing ->
            val submit = rememberSubmit()
            var name by remember { mutableStateOf(existing?.name.orEmpty()) }
            var code by remember { mutableStateOf(existing?.code.orEmpty()) }
            var city by remember { mutableStateOf(existing?.city.orEmpty()) }
            var state by remember { mutableStateOf(existing?.state.orEmpty()) }
            var address by remember { mutableStateOf(existing?.address.orEmpty()) }
            var contact by remember { mutableStateOf(existing?.contactNumber.orEmpty()) }
            var status by remember { mutableStateOf<String?>(existing?.status ?: "ACTIVE") }
            FormPage(pad) {
                AppTextField(name, { name = it }, "Branch name", required = true)
                AppTextField(code, { code = it }, "Branch code", required = true, capitalizeAll = true)
                AppTextField(city, { city = it }, "City", required = true)
                AppTextField(state, { state = it }, "State", required = true)
                AppTextField(address, { address = it }, "Address", singleLine = false)
                AppTextField(contact, { contact = it }, "Contact number", keyboardType = KeyboardType.Phone)
                if (existing != null) DropdownField("Status", RecordStatus, status, { status = it }, required = true)
                InlineError(submit.error)
                PrimaryButton("Save branch", {
                    val b = body("name" to name, "code" to code, "city" to city, "state" to state, "address" to address, "contactNumber" to contact, "status" to status)
                    submit.run({ if (id == null) repo.createBranch(b) else repo.updateBranch(id, b) }, { nav.popBackStack() })
                }, enabled = name.isNotBlank() && code.isNotBlank() && city.isNotBlank() && state.isNotBlank(), busy = submit.busy)
            }
        }
    }
}

// ================================================================= Employees

@Composable
fun EmployeesScreen(nav: NavHostController) {
    val repo = appContainer().repo
    val admin = isAdmin()
    val vm = appViewModel<QueryListViewModel<Employee>>(key = "employees") { r -> QueryListViewModel(r) { rp, q -> rp.employees(q) } }
    DrawerScaffold(
        nav, Routes.EMPLOYEES, "Employees",
        floatingActionButton = {
            if (admin) ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.employeeForm()) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add employee") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            QueryList(
                vm = vm, repo = repo,
                searchHint = "Search name, code, department",
                emptyTitle = "No employees",
                emptyMessage = if (admin) "Add employees to assign laptops." else "No employees have been created yet.",
                filterConfig = FilterConfig(branch = true, statuses = RecordStatus),
                key = { it.employeeId },
            ) { _, e -> EmployeeCard(e) { nav.navigate(Routes.employee(e.employeeId)) } }
        }
    }
}

@Composable
internal fun EmployeeCard(e: Employee, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("${e.name}  ·  ${e.employeeCode}", style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(e.branchName, e.department, e.designation).filter { it.isNotBlank() }.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (e.currentAssetId != null) "${e.currentAssetTitle ?: e.currentAssetId}  ·  ${e.currentAssetId}  ·  ${e.currentAssetSerial.orEmpty()}  ·  ${fmtDate(e.assignmentDate)}"
                    else "No laptop assigned",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (e.currentAssetId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(
                when {
                    e.status != "ACTIVE" -> e.status
                    e.currentAssetId == null -> e.assignmentStatus ?: "NO_LAPTOP_ASSIGNED"
                    else -> e.currentAssetStatus ?: e.status
                },
            )
        }
    }
}

@Composable
fun EmployeeDetailScreen(nav: NavHostController, id: String) {
    val admin = isAdmin()
    val vm = appViewModel<DetailViewModel<EmployeeAssets>>(key = "employee-$id") { DetailViewModel(it) { r -> r.employeeAssets(id) } }
    ScreenScaffold(
        nav, "Employee",
        actions = { if (admin) IconButton({ nav.navigate(Routes.employeeForm(id)) }) { Icon(Icons.Default.Edit, "Edit employee") } },
    ) { pad ->
        DetailBody(vm, pad) { d -> EmployeeDetailContent(nav, d, admin) }
    }
}

@Composable
private fun EmployeeDetailContent(nav: NavHostController, d: EmployeeAssets, admin: Boolean) {
    val e = d.employee
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    val tabs = listOf("Current Asset", "Previous Assets", "Repair History", "Replacement History")
    ScrollPage {
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.name, style = MaterialTheme.typography.titleLarge)
                        Text(e.employeeCode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusBadge(e.status)
                }
                Spacer(Modifier.height(8.dp))
                InfoRow("Branch", e.branchName, onClick = { nav.navigate(Routes.branch(e.branchId)) })
                InfoRow("Department", e.department)
                InfoRow("Designation", e.designation)
                InfoRow("Laptop name / model", e.currentAssetTitle)
                InfoRow("Laptop ID", e.currentAssetId, onClick = e.currentAssetId?.let { assetId -> { nav.navigate(Routes.asset(assetId)) } })
                InfoRow("Serial number", e.currentAssetSerial)
                InfoRow("Laptop status", e.currentAssetStatus?.let { prettyEnum(it) })
                InfoRow("Assignment status", e.assignmentStatus?.let { prettyEnum(it) })
                InfoRow("Handover status", e.handoverStatus?.let { prettyEnum(it) })
                InfoRow("Assignment date", e.assignmentDate?.let { fmtDate(it) })
                InfoRow("Contact", e.contactNumber)
                InfoRow("Email", e.email)
                InfoRow("Joining date", e.joiningDate?.let { fmtDate(it) })
            }
        }
        if (e.status == "ACTIVE") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    if (d.current == null) "Assign laptop" else "Replace laptop",
                    { nav.navigate(Routes.assign(branchId = e.branchId, employeeId = e.employeeId)) },
                    Modifier.weight(1f),
                )
                if (admin) SecondaryButton("Deactivate", { confirm = true }, Modifier.weight(1f))
            }
        }
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> {
                val c = d.current
                if (c == null) EmptyState("No laptop assigned", "Assign a laptop from stock.")
                else AssetCard(c, showEmployee = false, onClick = { nav.navigate(Routes.asset(c.assetId)) })
            }
            1 -> if (d.previous.isEmpty()) EmptyState("No previous laptops") else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                d.previous.forEach { p ->
                    AppCard(Modifier.fillMaxWidth(), { nav.navigate(Routes.asset(p.assetId)) }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text("${p.assetId}  ·  ${p.serialNumber.orEmpty()}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                Text("${p.brand.orEmpty()} ${p.model.orEmpty()}".trim(), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${fmtDate(p.assignmentDate)} → ${fmtDate(p.releaseDate)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                p.releaseReason?.let { Text(prettyEnum(it), style = MaterialTheme.typography.bodySmall) }
                            }
                            StatusBadge(p.currentAssetStatus ?: p.statusAfterRelease)
                        }
                    }
                }
            }
            2 -> if (d.repairs.isEmpty()) EmptyState("No repairs") else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                d.repairs.forEach { r -> RepairCard(r) { nav.navigate(Routes.repair(r.repairId)) } }
            }
            else -> if (d.replacements.isEmpty()) EmptyState("No replacements") else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                d.replacements.forEach { r ->
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("${r.oldAssetId} → ${r.newAssetId}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${fmtDate(r.replacementDate)}  ·  ${prettyEnum(r.reason)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            r.oldAssetStatus?.let { Text("Old laptop: ${prettyEnum(it)}", style = MaterialTheme.typography.bodySmall) }
                            r.remarks?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
    if (confirm) {
        val repo = appContainer().repo
        val submit = rememberSubmit()
        ConfirmDialog(
            title = "Deactivate employee?",
            message = "${e.name} will be marked inactive. An employee can only be deactivated after returning the assigned laptop.",
            confirmText = "Deactivate", destructive = true, busy = submit.busy, error = submit.error,
            onConfirm = { submit.run({ repo.deactivateEmployee(e.employeeId) }, { confirm = false }) },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
fun EmployeeFormScreen(nav: NavHostController, idArg: String?, branchIdArg: String?) {
    val id = idArg.nz()
    val presetBranch = branchIdArg.nz()
    val repo = appContainer().repo
    ScreenScaffold(nav, if (id == null) "Add employee" else "Edit employee") { pad ->
        LoadForEdit(pad, id, { repo.employee(id!!) }) { existing ->
            val submit = rememberSubmit()
            var code by remember { mutableStateOf(existing?.employeeCode.orEmpty()) }
            var name by remember { mutableStateOf(existing?.name.orEmpty()) }
            var branch by remember { mutableStateOf(existing?.let { Option(it.branchId, it.branchName ?: it.branchId, it.branchCode) }) }
            var department by remember { mutableStateOf(existing?.department.orEmpty()) }
            var designation by remember { mutableStateOf(existing?.designation.orEmpty()) }
            var contact by remember { mutableStateOf(existing?.contactNumber.orEmpty()) }
            var email by remember { mutableStateOf(existing?.email.orEmpty()) }
            var joining by remember { mutableStateOf(isoToDay(existing?.joiningDate)) }
            var status by remember { mutableStateOf<String?>(existing?.status ?: "ACTIVE") }
            PresetBranch(presetBranch, existing == null) { branch = it }
            FormPage(pad) {
                AppTextField(code, { code = it }, "Employee code", required = true, capitalizeAll = true)
                AppTextField(name, { name = it }, "Employee name", required = true)
                RemotePickerField(
                    "Branch", branch, { branch = it }, { branchOptions(repo, it) },
                    required = true, enabled = existing?.currentAssetId == null,
                )
                AppTextField(department, { department = it }, "Department")
                AppTextField(designation, { designation = it }, "Designation")
                AppTextField(contact, { contact = it }, "Contact number", keyboardType = KeyboardType.Phone)
                AppTextField(email, { email = it }, "Email", keyboardType = KeyboardType.Email)
                DateField("Joining date", joining, { joining = it })
                if (existing != null) DropdownField("Status", RecordStatus, status, { status = it }, required = true)
                InlineError(submit.error)
                PrimaryButton("Save employee", {
                    val b = body(
                        "employeeCode" to code, "name" to name, "branchId" to branch?.id, "department" to department,
                        "designation" to designation, "contactNumber" to contact, "email" to email,
                        "joiningDate" to joining, "status" to status,
                    )
                    submit.run({ if (id == null) repo.createEmployee(b) else repo.updateEmployee(id, b) }, { nav.popBackStack() })
                }, enabled = code.isNotBlank() && name.isNotBlank() && branch != null, busy = submit.busy)
            }
        }
    }
}

/** Pre-selects the branch (by id) when adding an employee from a branch page. */
@Composable
private fun PresetBranch(branchId: String?, enabled: Boolean, onBranch: (Option) -> Unit) {
    val repo = appContainer().repo
    LaunchedEffect(branchId, enabled) {
        if (branchId != null && enabled) repo.branch(branchId).onSuccess { onBranch(Option(it.branchId, it.name, "${it.code} · ${it.city}")) }
    }
}

// ================================================================= Laptop (asset) form

@Composable
fun AssetFormScreen(nav: NavHostController, idArg: String?) {
    val id = idArg.nz()
    val repo = appContainer().repo
    ScreenScaffold(nav, if (id == null) "Add laptop" else "Edit laptop") { pad ->
        LoadForEdit(pad, id, { repo.assetDetails(id!!).map { it.asset } }) { existing: Asset? ->
            val submit = rememberSubmit()
            var tag by remember { mutableStateOf(existing?.assetTag.orEmpty()) }
            var serial by remember { mutableStateOf(existing?.serialNumber.orEmpty()) }
            var brand by remember { mutableStateOf(existing?.brand.orEmpty()) }
            var model by remember { mutableStateOf(existing?.model.orEmpty()) }
            var processor by remember { mutableStateOf(existing?.processor.orEmpty()) }
            var ram by remember { mutableStateOf(existing?.ram.orEmpty()) }
            var storage by remember { mutableStateOf(existing?.storage.orEmpty()) }
            var os by remember { mutableStateOf(existing?.operatingSystem.orEmpty()) }
            var purchase by remember { mutableStateOf(isoToDay(existing?.purchaseDate)) }
            var price by remember { mutableStateOf(existing?.purchasePrice?.toString().orEmpty()) }
            var wFrom by remember { mutableStateOf(isoToDay(existing?.warrantyStartDate)) }
            var wTo by remember { mutableStateOf(isoToDay(existing?.warrantyEndDate)) }
            var branch by remember { mutableStateOf(existing?.let { Option(it.currentBranchId, it.branchName ?: it.currentBranchId, it.branchCode) }) }
            FormPage(pad) {
                if (existing != null) {
                    Text("Status: ${prettyEnum(existing.status)} (changes through assign, repair, dead and retire actions)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RemotePickerField(
                    "Branch", branch, { branch = it }, { branchOptions(repo, it) },
                    required = true, enabled = existing == null || existing.status == "AVAILABLE",
                )
                AppTextField(serial, { serial = it }, "Serial number", required = true, capitalizeAll = true)
                AppTextField(tag, { tag = it }, "Asset tag (optional)")
                AppTextField(brand, { brand = it }, "Brand", required = true)
                AppTextField(model, { model = it }, "Model", required = true)
                AppTextField(processor, { processor = it }, "Processor")
                AppTextField(ram, { ram = it }, "RAM (e.g. 16 GB)")
                AppTextField(storage, { storage = it }, "Storage (e.g. 512 GB SSD)")
                AppTextField(os, { os = it }, "Operating system")
                DateField("Purchase date", purchase, { purchase = it })
                AppTextField(price, { price = it.filter { c -> c.isDigit() || c == '.' } }, "Purchase price", keyboardType = KeyboardType.Decimal, prefix = "₹")
                DateField("Warranty start", wFrom, { wFrom = it })
                DateField("Warranty end", wTo, { wTo = it })
                InlineError(submit.error)
                PrimaryButton("Save laptop", {
                    val b = body(
                        "assetTag" to tag, "serialNumber" to serial, "brand" to brand, "model" to model, "processor" to processor,
                        "ram" to ram, "storage" to storage, "operatingSystem" to os, "purchaseDate" to purchase,
                        "purchasePrice" to price.toMoneyOrNull(), "warrantyStartDate" to wFrom, "warrantyEndDate" to wTo,
                        "currentBranchId" to branch?.id,
                    )
                    submit.run({ if (id == null) repo.createAsset(b) else repo.updateAsset(id, b) }, { nav.popBackStack() })
                }, enabled = serial.isNotBlank() && brand.isNotBlank() && model.isNotBlank() && branch != null, busy = submit.busy)
            }
        }
    }
}
