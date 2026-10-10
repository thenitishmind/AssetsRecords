package com.assetmgmt.laptops

import android.app.Application
import com.assetmgmt.laptops.data.local.SessionStore
import com.assetmgmt.laptops.data.remote.AppJson
import com.assetmgmt.laptops.data.remote.NetworkModule
import com.assetmgmt.laptops.data.repository.AssetRepository

/** Manual dependency container (keeps the app free of DI framework overhead). */
class AppContainer(app: Application) {
    val session = SessionStore(app, AppJson)
    val repo = AssetRepository(NetworkModule.create(session), session)
}

class AssetApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
