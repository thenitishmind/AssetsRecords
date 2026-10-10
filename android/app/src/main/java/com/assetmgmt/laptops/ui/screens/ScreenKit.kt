package com.assetmgmt.laptops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.assetmgmt.laptops.data.remote.User
import com.assetmgmt.laptops.ui.components.DetailTopBar
import com.assetmgmt.laptops.ui.components.ErrorBox
import com.assetmgmt.laptops.ui.components.InlineError
import com.assetmgmt.laptops.ui.components.LoadingBox
import com.assetmgmt.laptops.viewmodel.DetailUi
import com.assetmgmt.laptops.viewmodel.DetailViewModel
import com.assetmgmt.laptops.viewmodel.appContainer

/** Signed-in user (never null while a screen behind login is visible). */
@Composable
fun currentUser(): User? {
    val u by appContainer().session.user.collectAsState()
    return u
}

@Composable
fun isAdmin(): Boolean = currentUser()?.isAdmin == true

/** Back-arrow screen shell used by every non-drawer screen. */
@Composable
fun ScreenScaffold(
    nav: NavHostController,
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = { DetailTopBar(title, { nav.popBackStack() }, actions) },
        floatingActionButton = floatingActionButton,
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

/** Renders the loading / error / data states of a [DetailViewModel] with pull-to-refresh. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DetailBody(vm: DetailViewModel<T>, pad: PaddingValues, content: @Composable (T) -> Unit) {
    val state by vm.ui.collectAsState()
    when (val s = state) {
        DetailUi.Loading -> LoadingBox(Modifier.padding(pad))
        is DetailUi.Error -> ErrorBox(s.message, { vm.load() }, Modifier.padding(pad))
        is DetailUi.Data -> PullToRefreshBox(isRefreshing = s.refreshing, onRefresh = { vm.load(silent = true) }, modifier = Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                InlineError(s.error, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Box(Modifier.weight(1f)) { content(s.value) }
            }
        }
    }
}

/** Scrollable column for detail pages. */
@Composable
fun ScrollPage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = {
            content()
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 80.dp))
        },
    )
}

/** Scrollable form column that stays above the keyboard. */
@Composable
fun FormPage(pad: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = {
            content()
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 32.dp))
        },
    )
}

/** Modal bottom sheet with a title and scrollable body. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}
