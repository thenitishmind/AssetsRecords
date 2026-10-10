package com.assetmgmt.laptops.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Navigation arguments arrive as "" when optional; treat blank as "not provided". */
fun String?.nz(): String? = this?.takeIf { it.isNotBlank() }

/**
 * Tiny busy / error holder for form submits and confirm actions. Keeps screens free of
 * one-off ViewModels: call [run] with a repository call and react in [onOk].
 */
class Submit(private val scope: CoroutineScope) {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    fun <T> run(block: suspend () -> Result<T>, onOk: (T) -> Unit = {}) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            val r = block()
            busy = false
            r.onSuccess { onOk(it) }.onFailure { error = it.message ?: "Something went wrong. Please try again." }
        }
    }

    fun clearError() {
        error = null
    }
}

@Composable
fun rememberSubmit(): Submit {
    val scope = rememberCoroutineScope()
    return remember { Submit(scope) }
}

/** Parses a user-typed number; blank / invalid -> null. */
fun String.toMoneyOrNull(): Double? = trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
