package com.assetmgmt.laptops.data.local

import android.content.Context
import com.assetmgmt.laptops.BuildConfig
import com.assetmgmt.laptops.data.remote.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Holds the server URL, auth token and signed-in user. Stored in app-private
 * storage. Contains NO database credentials - those live only on the server.
 */
class SessionStore(context: Context, private val json: Json) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    @Volatile var baseUrl: String = prefs.getString(KEY_URL, null) ?: BuildConfig.DEFAULT_API_URL
        private set
    @Volatile var token: String? = prefs.getString(KEY_TOKEN, null)
        private set

    private val _user = MutableStateFlow(
        prefs.getString(KEY_USER, null)?.let { runCatching { json.decodeFromString<User>(it) }.getOrNull() },
    )
    val user: StateFlow<User?> = _user

    /** Emits true when the session was invalidated by the server (401). */
    private val _expired = MutableStateFlow(0)
    val expired: StateFlow<Int> = _expired

    fun setBaseUrl(url: String) {
        var u = url.trim()
        if (!u.endsWith("/")) u += "/"
        if (!u.endsWith("/api/")) u += "api/"
        baseUrl = u
        prefs.edit().putString(KEY_URL, u).apply()
    }

    fun resetBaseUrl() {
        baseUrl = BuildConfig.DEFAULT_API_URL
        prefs.edit().remove(KEY_URL).apply()
    }

    fun signIn(token: String, user: User) {
        this.token = token
        _user.value = user
        prefs.edit().putString(KEY_TOKEN, token).putString(KEY_USER, json.encodeToString(user)).apply()
    }

    fun updateUser(user: User) {
        _user.value = user
        prefs.edit().putString(KEY_USER, json.encodeToString(user)).apply()
    }

    fun signOut() {
        token = null
        _user.value = null
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USER).apply()
    }

    fun markExpired() {
        if (token != null) {
            signOut()
            _expired.value = _expired.value + 1
        }
    }

    private companion object {
        const val KEY_URL = "base_url"
        const val KEY_TOKEN = "token"
        const val KEY_USER = "user"
    }
}
