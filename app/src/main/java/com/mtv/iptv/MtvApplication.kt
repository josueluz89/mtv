package com.mtv.iptv

import android.app.Application
import android.content.Context
import com.mtv.iptv.di.AppContainer
import com.mtv.iptv.util.CrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MtvApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    /** Vive lo que vive la app: propaga el ajuste de DNS privado al proveedor HTTP. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        // Aplica el DNS privado guardado desde el arranque (y ante cada cambio).
        appScope.launch {
            container.userPrefs.privateDns.collect { enabled ->
                container.httpClientProvider.usePrivateDns = enabled
            }
        }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as MtvApplication).container
