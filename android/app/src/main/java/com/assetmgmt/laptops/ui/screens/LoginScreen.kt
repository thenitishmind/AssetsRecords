package com.assetmgmt.laptops.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LaptopChromebook
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assetmgmt.laptops.ui.components.AppCard
import com.assetmgmt.laptops.ui.components.AppTextField
import com.assetmgmt.laptops.ui.components.InlineError
import com.assetmgmt.laptops.ui.components.PrimaryButton
import com.assetmgmt.laptops.ui.theme.Indigo500
import com.assetmgmt.laptops.ui.theme.Navy900
import com.assetmgmt.laptops.ui.theme.Sky400
import com.assetmgmt.laptops.viewmodel.appContainer
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var showServer by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf(container.session.baseUrl) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (busy) return
        if (username.isBlank() || password.isEmpty()) {
            error = "Enter your username and password."
            return
        }
        val normalizedServer = if (showServer) normalizeServerUrl(server) else null
        if (showServer && normalizedServer == null) {
            error = "Enter a valid server address, such as http://192.168.1.16:4000."
            return
        }
        focusManager.clearFocus()
        busy = true; error = null
        scope.launch {
            try {
                normalizedServer?.let {
                    container.session.setBaseUrl(it)
                    server = container.session.baseUrl
                }
                container.repo.login(username.trim(), password)
                    .onSuccess { onLoggedIn() }
                    .onFailure { error = it.message ?: "Sign in failed. Check your details and try again." }
            } finally {
                busy = false
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Navy900, Indigo500, Sky400.copy(alpha = 0.85f)))),
    ) {
        Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        ) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.LaptopChromebook, null, tint = Color.White, modifier = Modifier.size(46.dp))
        }
            Spacer(Modifier.height(16.dp))
            Text("Asset Manager", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text("Laptop asset, repair & stock lifecycle", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(28.dp))

            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sign in", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        trailingIcon = {
                            IconButton({ showPw = !showPw }, enabled = !busy) {
                                Icon(
                                    if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    if (showPw) "Hide password" else "Show password",
                                )
                            }
                        },
                    )
                    if (showServer) {
                        AppTextField(
                            server,
                            { server = it },
                            "Server address",
                            keyboardType = KeyboardType.Uri,
                            enabled = !busy,
                        )
                        Text(
                            "Enter the server address, for example http://192.168.1.16:4000. The /api path is added automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    InlineError(error)
                    PrimaryButton("Sign in", ::submit, busy = busy)
                    TextButton({ showServer = !showServer; error = null }, Modifier.fillMaxWidth(), enabled = !busy) {
                        Text(if (showServer) "Hide server settings" else "Server settings", textAlign = TextAlign.Center)
                    }
                }
            }
            }
        }
    }
}

private fun normalizeServerUrl(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    val candidate = if ("://" in trimmed) trimmed else "http://$trimmed"
    val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
    if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
    if (uri.port != -1 && uri.port !in 1..65535) return null
    if (uri.encodedUserInfo != null || uri.query != null || uri.fragment != null) return null
    if (uri.path !in listOf("", "/", "/api", "/api/")) return null
    return "${uri.scheme}://${uri.encodedAuthority}"
}
