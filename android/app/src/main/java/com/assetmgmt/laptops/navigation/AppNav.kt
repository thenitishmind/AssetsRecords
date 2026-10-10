package com.assetmgmt.laptops.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LaptopChromebook
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.assetmgmt.laptops.ui.screens.*
import com.assetmgmt.laptops.ui.theme.Indigo500
import com.assetmgmt.laptops.ui.theme.Navy900
import com.assetmgmt.laptops.viewmodel.appContainer
import kotlinx.coroutines.launch

object Routes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val ASSETS = "assets"
    const val HANDOVER = "handover"
    const val REPAIRS = "repairs"
    const val STOCK = "stock"
    const val DEAD = "dead"
    const val BRANCHES = "branches"
    const val EMPLOYEES = "employees"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val USERS = "users"
    const val AUDIT = "audit"

    fun asset(id: String) = "asset/$id"
    fun repair(id: String) = "repair/$id"
    fun branch(id: String) = "branch/$id"
    fun employee(id: String) = "employee/$id"
    fun stockList(status: String, branchId: String? = null) = "stockList/$status" + (branchId?.let { "?branchId=$it" } ?: "")
    fun assign(branchId: String? = null, employeeId: String? = null, assetId: String? = null) =
        "assign?branchId=${branchId.orEmpty()}&employeeId=${employeeId.orEmpty()}&assetId=${assetId.orEmpty()}"
    fun branchForm(id: String? = null) = "branchForm?id=${id.orEmpty()}"
    fun employeeForm(id: String? = null, branchId: String? = null) = "employeeForm?id=${id.orEmpty()}&branchId=${branchId.orEmpty()}"
    fun assetForm(id: String? = null) = "assetForm?id=${id.orEmpty()}"
    fun repairForm(assetId: String? = null) = "repairForm?assetId=${assetId.orEmpty()}"
}

data class TopLevel(val route: String, val title: String, val icon: ImageVector)

val topLevelDestinations = listOf(
    TopLevel(Routes.DASHBOARD, "Dashboard", Icons.Default.Dashboard),
    TopLevel(Routes.ASSETS, "Assets", Icons.Default.Laptop),
    TopLevel(Routes.HANDOVER, "Laptop Handover", Icons.Default.SwapHoriz),
    TopLevel(Routes.REPAIRS, "Repair Assets", Icons.Default.Build),
    TopLevel(Routes.STOCK, "Stock", Icons.Default.Inventory2),
    TopLevel(Routes.DEAD, "Dead Laptops", Icons.Default.ReportProblem),
    TopLevel(Routes.BRANCHES, "Branches", Icons.Default.Apartment),
    TopLevel(Routes.EMPLOYEES, "Employees", Icons.Default.People),
    TopLevel(Routes.REPORTS, "Reports", Icons.Default.BarChart),
    TopLevel(Routes.SETTINGS, "Settings", Icons.Default.Settings),
)

fun NavHostController.goTopLevel(route: String) = navigate(route) {
    popUpTo(Routes.DASHBOARD) { saveState = true }
    launchingSingle()
}

private fun androidx.navigation.NavOptionsBuilder.launchingSingle() {
    launchSingleTop = true
    restoreState = true
}

/** Drawer + top app bar shared by the 9 main sections. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerScaffold(
    nav: NavHostController,
    route: String,
    title: String,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val session = appContainer().session
    val user by session.user.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Box(
                        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Navy900, Indigo500))).statusBarsPadding().padding(20.dp),
                    ) {
                        Column {
                            Icon(Icons.Default.LaptopChromebook, null, tint = Color.White, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("Asset Manager", style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Text(
                                "${user?.name ?: ""} · ${user?.role?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    topLevelDestinations.forEach { d ->
                        NavigationDrawerItem(
                            icon = { Icon(d.icon, null) },
                            label = { Text(d.title) },
                            selected = d.route == route,
                            onClick = {
                                scope.launch { drawer.close() }
                                if (d.route != route) nav.goTopLevel(d.route)
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                        label = { Text("Sign out") },
                        selected = false,
                        onClick = { scope.launch { drawer.close() }; session.signOut() },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { IconButton({ scope.launch { drawer.open() } }) { Icon(Icons.Default.Menu, "Menu") } },
                    actions = {
                        IconButton({ nav.navigate(Routes.SEARCH) }) { Icon(Icons.Default.Search, "Search") }
                        actions()
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            floatingActionButton = floatingActionButton,
            containerColor = MaterialTheme.colorScheme.background,
            content = content,
        )
    }
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val session = appContainer().session
    val user by session.user.collectAsState()
    val start = if (session.token != null && user != null) Routes.DASHBOARD else Routes.LOGIN

    // Return to login whenever the session ends (sign-out or expired token).
    LaunchedEffect(user) {
        if (user == null && nav.currentDestination?.route != Routes.LOGIN) {
            nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
        }
    }

    NavHost(nav, startDestination = start) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = { nav.navigate(Routes.DASHBOARD) { popUpTo(0) { inclusive = true } } })
        }
        composable(Routes.DASHBOARD) { DashboardScreen(nav) }
        composable(Routes.ASSETS) { AssetsScreen(nav) }
        composable(Routes.HANDOVER) { HandoverScreen(nav) }
        composable(Routes.REPAIRS) { RepairAssetsScreen(nav) }
        composable(Routes.STOCK) { StockScreen(nav) }
        composable(Routes.DEAD) { DeadScreen(nav) }
        composable(Routes.BRANCHES) { BranchesScreen(nav) }
        composable(Routes.EMPLOYEES) { EmployeesScreen(nav) }
        composable(Routes.REPORTS) { ReportsScreen(nav) }
        composable(Routes.SETTINGS) { SettingsScreen(nav) }
        composable(Routes.SEARCH) { SearchScreen(nav) }
        composable(Routes.USERS) { UsersScreen(nav) }
        composable(Routes.AUDIT) { AuditScreen(nav) }

        composable("asset/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
            AssetDetailScreen(nav, it.arguments!!.getString("id")!!)
        }
        composable("repair/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
            RepairDetailScreen(nav, it.arguments!!.getString("id")!!)
        }
        composable("branch/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
            BranchDetailScreen(nav, it.arguments!!.getString("id")!!)
        }
        composable("employee/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
            EmployeeDetailScreen(nav, it.arguments!!.getString("id")!!)
        }
        composable(
            "stockList/{status}?branchId={branchId}",
            listOf(
                navArgument("status") { type = NavType.StringType },
                navArgument("branchId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { StockListScreen(nav, it.arguments!!.getString("status")!!, it.arguments?.getString("branchId")) }

        composable(
            "assign?branchId={branchId}&employeeId={employeeId}&assetId={assetId}",
            optionalArgs("branchId", "employeeId", "assetId"),
        ) { e ->
            AssignScreen(nav, e.arguments?.getString("branchId"), e.arguments?.getString("employeeId"), e.arguments?.getString("assetId"))
        }
        composable("branchForm?id={id}", optionalArgs("id")) { BranchFormScreen(nav, it.arguments?.getString("id")) }
        composable("employeeForm?id={id}&branchId={branchId}", optionalArgs("id", "branchId")) {
            EmployeeFormScreen(nav, it.arguments?.getString("id"), it.arguments?.getString("branchId"))
        }
        composable("assetForm?id={id}", optionalArgs("id")) { AssetFormScreen(nav, it.arguments?.getString("id")) }
        composable("repairForm?assetId={assetId}", optionalArgs("assetId")) { RepairFormScreen(nav, it.arguments?.getString("assetId")) }
    }
}

private fun optionalArgs(vararg names: String) = names.map {
    navArgument(it) { type = NavType.StringType; nullable = true; defaultValue = "" }
}
