package com.assetmgmt.laptops.data.remote

import com.assetmgmt.laptops.BuildConfig
import com.assetmgmt.laptops.data.local.SessionStore
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}

/** Rewrites the placeholder host to the server URL chosen in Settings and attaches the JWT. */
private class SessionInterceptor(private val session: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val base = runCatching { session.baseUrl.toHttpUrl() }.getOrNull()
        val builder = original.newBuilder()
        if (base != null) {
            val tail = original.url.encodedPath.removePrefix("/api")
            val url = original.url.newBuilder()
                .scheme(base.scheme).host(base.host).port(base.port)
                .encodedPath(base.encodedPath.trimEnd('/') + tail)
                .build()
            builder.url(url)
        }
        session.token?.let { builder.header("Authorization", "Bearer $it") }
        builder.header("Accept", "application/json")
        val response = chain.proceed(builder.build())
        val isLogin = original.url.encodedPath.endsWith("/auth/login")
        if (response.code == 401 && !isLogin) session.markExpired()
        return response
    }
}

object NetworkModule {
    fun create(session: SessionStore): ApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor(SessionInterceptor(session))
            .apply {
                if (BuildConfig.DEBUG) {
                    // BASIC only: never log bodies (they contain tokens / PII).
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("http://placeholder.invalid/api/")
            .client(client)
            .addConverterFactory(AppJson.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}
