package com.mtv.iptv

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.ui.player.PlayerScreen
import com.mtv.iptv.ui.player.TvPlayerScreen
import com.mtv.iptv.ui.theme.MtvTheme

/**
 * Activity separada que hostea el reproductor (motor libVLC, v1.3).
 * En TV usa [TvPlayerScreen] (SurfaceView + controles Compose navegables con
 * D-pad); en celular muestra [PlayerScreen] (gestos + overlay Compose).
 */
class PlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MEDIA_KEY = "extra_media_key"
        const val EXTRA_IMAGE_URL = "extra_image_url"

        fun start(context: Context, url: String, title: String, mediaKey: String, imageUrl: String = "") {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MEDIA_KEY, mediaKey)
                putExtra(EXTRA_IMAGE_URL, imageUrl)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val mediaKey = intent.getStringExtra(EXTRA_MEDIA_KEY).orEmpty()
        val imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL).orEmpty()
        if (url.isBlank()) {
            finish()
            return
        }

        val container = appContainer
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val isTv = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        setContent {
            val theme by container.userPrefs.theme.collectAsState(initial = "sistema")
            CompositionLocalProvider(LocalAppContainer provides container) {
                MtvTheme(theme = theme) {
                    if (isTv) {
                        TvPlayerScreen(
                            url = url,
                            title = title,
                            mediaKey = mediaKey,
                            imageUrl = imageUrl,
                            onBack = { finish() },
                        )
                    } else {
                        PlayerScreen(
                            url = url,
                            title = title,
                            mediaKey = mediaKey,
                            imageUrl = imageUrl,
                            onBack = { finish() },
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        // PlayerScreen/TvPlayerScreen ya guardan la posición y detienen el motor
        // al salir; esto es respaldo (sin inicializar el player si nunca se usó).
        appContainer.stopVlcIfRunning()
        super.onDestroy()
    }
}
