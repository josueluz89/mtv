package com.mtv.iptv

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import coil.Coil
import com.mtv.iptv.di.AppContainer
import com.mtv.iptv.util.CrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MtvApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    /** Vive lo que vive la app: propaga el ajuste de DNS privado al proveedor HTTP. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        // ImageLoader global con caché de memoria acotado (~10% heap) para poca RAM.
        Coil.setImageLoader(container.imageLoader)
        // Aplica el DNS privado guardado desde el arranque (y ante cada cambio).
        appScope.launch {
            container.userPrefs.privateDns.collect { enabled ->
                container.httpClientProvider.usePrivateDns = enabled
            }
        }
        watchBackground()
    }

    /**
     * Libera los recursos pesados del reproductor cuando la app pasa a
     * background. Se implementa con ActivityLifecycleCallbacks (sin
     * ProcessLifecycleOwner, que no está en las dependencias): cuando no
     * queda ninguna Activity iniciada, la app está en background.
     *
     * El chequeo va con un pequeño retardo para no confundir una rotación
     * (stop seguido de start) con un verdadero paso a background.
     * En PiP la Activity sigue iniciada, así que no se interrumpe.
     */
    private fun watchBackground() {
        var started = 0
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                started++
            }

            override fun onActivityStopped(activity: Activity) {
                started--
                if (started > 0) return
                started = 0
                appScope.launch {
                    delay(700)
                    if (started == 0) {
                        // Guarda "seguir viendo" y suelta decoders/codecs (ver AppContainer).
                        container.onAppBackgrounded(this)
                    }
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as MtvApplication).container
