package com.mtv.iptv

import android.app.Application
import android.content.Context
import com.mtv.iptv.di.AppContainer

class MtvApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as MtvApplication).container
