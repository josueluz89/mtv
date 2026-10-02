package com.mtv.iptv

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.ui.mobile.MobileNav
import com.mtv.iptv.ui.theme.MtvTheme
import com.mtv.iptv.ui.tv.TvApp

/** Detecta TV (UiModeManager) y muestra la UI correspondiente. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val isTv = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val container = appContainer
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                if (isTv) TvApp() else MtvTheme { MobileNav() }
            }
        }
    }
}
