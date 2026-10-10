package com.assetmgmt.laptops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.assetmgmt.laptops.BuildConfig
import com.assetmgmt.laptops.data.remote.Asset
import com.assetmgmt.laptops.data.remote.AuditLog
import com.assetmgmt.laptops.data.remote.SearchResult
import com.assetmgmt.laptops.data.remote.User
import com.assetmgmt.laptops.data.repository.body
import com.assetmgmt.laptops.navigation.DrawerScaffold
import com.assetmgmt.laptops.navigation.Routes
import com.assetmgmt.laptops.ui.components.AppCard
import com.assetmgmt.laptops.ui.components.AppTextField
import com.assetmgmt.laptops.ui.components.AssetCard
import com.assetmgmt.laptops.ui.components.DropdownField
import com.assetmgmt.laptops.ui.components.EmptyState
import com.assetmgmt.laptops.ui.components.ErrorBox
import com.assetmgmt.laptops.ui.components.FilterConfig
import com.assetmgmt.laptops.ui.components.InfoRow
import com.assetmgmt.laptops.ui.components.InlineError
import com.assetmgmt.laptops.ui.components.LoadingBox
import com.assetmgmt.laptops.ui.components.PrimaryButton
import com.assetmgmt.laptops.ui.components.QueryList
import com.assetmgmt.laptops.ui.components.SearchField
import com.assetmgmt.laptops.ui.components.SecondaryButton
import com.assetmgmt.laptops.ui.components.SectionCard
import com.assetmgmt.laptops.ui.components.StatusBadge
import com.assetmgmt.laptops.util.fmtDateTime
import com.assetmgmt.laptops.util.prettyEnum
import com.assetmgmt.laptops.util.rememberSubmit
import com.assetmgmt.laptops.viewmodel.QueryListViewModel
import com.assetmgmt.laptops.viewmodel.appContainer
import com.assetmgmt.laptops.viewmodel.appViewModel
import kotlinx.coroutines.delay

private val RoleOptions = listOf("ADMIN" to "Administrator", "OPERATOR" to "Operator")
private val UserStatusOptions = listOf("ACTIVE" to "Active", "INACTIVE" to "Inactive")

// ================================================================= Settings

@Composable
fun SettingsScreen(nav: NavHostController) {
    val container = appContainer()
    val session = container.session
    val user = currentUser()
    var url by remember { mutableStateOf(session.baseUrl) }
    var urlMsg by remember { mutableStateOf<String?>(null) }
    var showPassword by remember { mutableStateOf(false) }

    DrawerScaffold(nav, Routes.SETTINGS, "Settings") { pad ->
        ScrollPage(Modifier.padding(pad)) {
            SectionCard("Profile") {
                InfoRow("Name", user?.name)
                InfoRow("Username", user?.username)
                InfoRow("Role", prettyEnum(user?.role))
                InfoRow("Last sign-in", user?.lastLoginAt?.let { fmtDateTime(it) })
            }
            SectionCard("Server") {
                Text(
                    "Address of the asset management backend. The Android emulator reaches your computer at 10.0.2.2; a phone needs your computer's LAN IP or an HTTPS address.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url, onValueChange = { url = it; urlMsg = null }, label = { Text("API base URL") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                urlMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp)) }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Save", {
                        if (url.trim().startsWith("http://") || url.trim().startsWith("https://")) {
                            session.setBaseUrl(url); url = session.baseUrl; urlMsg = "Saved: $url"
                        } else urlMsg = "Enter a full address starting with http:// or https://"
                    }, Modifier.weight(1f))
                    SecondaryButton("Reset", { session.resetBaseUrl(); url = session.baseUrl; urlMsg = "Reset to default" }, Modifier.weight(1f))
                }
            }
            SectionCard("Security") {
                SecondaryButton("Change password", { showPassword = true }, Modifier.fillMaxWidth())
            }
            if (user?.isAdmin == true) {
                SectionCard("Administration") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("User management", { nav.navigate(Routes.USERS) }, Modifier.fillMaxWidth())
                        SecondaryButton("Audit logs", { nav.navigate(Routes.AUDIT) }, Modifier.fillMaxWidth())
                    }
                }
            }
            SectionCard("About") {
                InfoRow("App", "Laptop Asset Manager")
                InfoRow("Version", BuildConfig.VERSION_NAME)
            }
            SecondaryButton("Sign out", { session.signOut() }, Modifier.fillMaxWidth())
        }
    }
    if (showPassword) ChangePasswordSheet { showPassword = false }
}

@Composable
private fun ChangePasswordSheet(onDismiss: () -> Unit) {
    val repo = appContainer().repo
    val submit = rememberSubmit()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = confirm.isNotEmpty() && confirm != new
    ActionSheet("Change password", onDismiss) {
        PasswordField(current, { current = it }, "Current password")
        PasswordField(new, { new = it }, "New password (min 8 characters)")
        PasswordField(confirm, { confirm = it }, "Confirm new password", isError = mismatch)
        if (mismatch) Text("Passwords do not match.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        InlineError(submit.error)
        PrimaryButton("Update password", {
            submit.run({ repo.changePassword(current, new) }, { onDismiss() })
        }, enabled = current.isNotEmpty() && new.length >= 8 && new == confirm, busy = submit.busy)
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, isError: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, isError = isError,
        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
}

// ================================================================= Users (admin)

@Composable
fun UsersScreen(nav: NavHostController) {
    val repo = appContainer().repo
    val admin = isAdmin()
    val vm = appViewModel<QueryListViewModel<User>>(key = "users") { r -> QueryListViewModel(r) { rp, q -> rp.users(q) } }
    var editing by remember { mutableStateOf<User?>(null) }
    var creating by remember { mutableStateOf(false) }

    ScreenScaffold(
        nav, "Users",
        floatingActionButton = {
            if (admin) ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add user") },
            )
        },
    ) { pad ->
        if (!admin) {
            ErrorBox("Only administrators can manage users.", null, Modifier.padding(pad))
        } else {
            Column(Modifier.padding(pad)) {
                QueryList(
                    vm = vm, repo = repo, searchHint = "Search username or name", emptyTitle = "No users",
                    key = { it.userId },
                ) { _, u ->
                    AppCard(Modifier.fillMaxWidth(), { editing = u }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(u.name, style = MaterialTheme.typography.titleSmall)
                                Text("${u.username}  ·  ${prettyEnum(u.role)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Last sign-in: ${fmtDateTime(u.lastLoginAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusBadge(u.status)
                        }
                    }
                }
            }
        }
    }
    if (creating) UserSheet(null) { creating = false }
    editing?.let { UserSheet(it) { editing = null } }
}

@Composable
private fun UserSheet(existing: User?, onDismiss: () -> Unit) {
    val repo = appContainer().repo
    val submit = rememberSubmit()
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf<String?>(existing?.role ?: "OPERATOR") }
    var status by remember { mutableStateOf<String?>(existing?.status ?: "ACTIVE") }
    ActionSheet(if (existing == null) "Add user" else "Edit user", onDismiss) {
        AppTextField(username, { username = it.trim().lowercase() }, "Username", required = true, enabled = existing == null)
        AppTextField(name, { name = it }, "Full name", required = true)
        PasswordField(password, { password = it }, if (existing == null) "Password (min 8 characters)" else "New password (leave blank to keep)")
        DropdownField("Role", RoleOptions, role, { role = it }, required = true)
        if (existing != null) DropdownField("Status", UserStatusOptions, status, { status = it }, required = true)
        InlineError(submit.error)
        val passOk = if (existing == null) password.length >= 8 else password.isEmpty() || password.length >= 8
        PrimaryButton("Save user", {
            if (existing == null) {
                submit.run({ repo.createUser(body("username" to username, "name" to name, "password" to password, "role" to role)) }, { onDismiss() })
            } else {
                submit.run({ repo.updateUser(existing.userId, body("name" to name, "password" to password, "role" to role, "status" to status)) }, { onDismiss() })
            }
        }, enabled = username.length >= 3 && name.isNotBlank() && passOk, busy = submit.busy)
    }
}

// ================================================================= Audit logs (admin)

@Composable
fun AuditScreen(nav: NavHostController) {
    val repo = appContainer().repo
    val admin = isAdmin()
    val vm = appViewModel<QueryListViewModel<AuditLog>>(key = "audit") { r -> QueryListViewModel(r) { rp, q -> rp.auditLogs(q) } }
    ScreenScaffold(nav, "Audit logs") { pad ->
        if (!admin) {
            ErrorBox("Only administrators can view audit logs.", null, Modifier.padding(pad))
        } else {
            Column(Modifier.padding(pad)) {
                QueryList(
                    vm = vm, repo = repo, searchHint = "Search action, entity or user", emptyTitle = "No audit entries yet",
                    emptyMessage = "Every create, update, assign, replace and repair action is recorded here.",
                    filterConfig = null,
                    key = { it.auditId },
                ) { _, a ->
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(prettyEnum(a.action), style = MaterialTheme.typography.titleSmall)
                            Text("${prettyEnum(a.entityType)}  ·  ${a.entityId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Text(
                                "${a.user.username.ifBlank { "system" }}  ·  ${fmtDateTime(a.date)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            a.remarks?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}

// ================================================================= Global search

@Composable
fun SearchScreen(nav: NavHostController) {
    val repo = appContainer().repo
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<SearchResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var openedFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(text) {
        val q = text.trim()
        if (q.length < 2) { result = null; error = null; loading = false; return@LaunchedEffect }
        delay(350)
        loading = true
        repo.search(q)
            .onSuccess { r ->
                result = r; error = null
                // An exact laptop / serial hit opens the laptop directly.
                if (r.exactAssetId != null && openedFor != q) {
                    openedFor = q
                    nav.navigate(Routes.asset(r.exactAssetId))
                }
            }
            .onFailure { error = it.message }
        loading = false
    }

    ScreenScaffold(nav, "Search") { pad ->
        Column(Modifier.padding(pad)) {
            SearchField(text, { text = it }, Modifier.padding(16.dp), "Asset ID, serial, employee, code, branch")
            val r = result
            when {
                loading && r == null -> LoadingBox()
                error != null -> ErrorBox(error ?: "", null)
                r == null -> EmptyState("Search everything", "Type at least 2 characters to find laptops, employees and branches.")
                r.assets.isEmpty() && r.employees.isEmpty() && r.branches.isEmpty() -> EmptyState("No results", "Nothing matches \"${text.trim()}\".")
                else -> ScrollPage {
                    if (r.assets.isNotEmpty()) SectionCard("Laptops (${r.assets.size})") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { r.assets.forEach { a: Asset -> AssetCard(a, onClick = { nav.navigate(Routes.asset(a.assetId)) }) } }
                    }
                    if (r.employees.isNotEmpty()) SectionCard("Employees (${r.employees.size})") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { r.employees.forEach { e -> EmployeeCard(e) { nav.navigate(Routes.employee(e.employeeId)) } } }
                    }
                    if (r.branches.isNotEmpty()) SectionCard("Branches (${r.branches.size})") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            r.branches.forEach { b ->
                                AppCard(Modifier.fillMaxWidth(), { nav.navigate(Routes.branch(b.branchId)) }) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(b.name, style = MaterialTheme.typography.titleSmall)
                                        Text("${b.code}  ·  ${b.city}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
