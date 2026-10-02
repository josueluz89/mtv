package com.mtv.iptv

import android.app.Application
import android.content.Context
import com.mtv.iptv.di.AppContainer
import com.mtv.iptv.util.CrashReporter

class MtvApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as MtvApplication).container
