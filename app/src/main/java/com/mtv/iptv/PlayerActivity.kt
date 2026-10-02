package com.mtv.iptv

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerView
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.ui.player.PlayerScreen
import com.mtv.iptv.ui.theme.MtvTheme
import kotlinx.coroutines.launch

/**
 * Activity separada que hostea el reproductor.
 * En TV usa un PlayerView directo con su controlador (navegable con D-pad);
 * en celular muestra PlayerScreen (gestos + overlay Compose).
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
        val manager = container.playerManager
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val isTv = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        if (isTv) {
            val playerView = PlayerView(this).apply {
                useController = true
                setShowPreviousButton(false)
                setShowNextButton(false)
                controllerShowTimeoutMs = 4000
            }
            playerView.player = manager.player
            setContentView(playerView)
            // En TV se continúa automáticamente donde quedó (sin diálogo).
            lifecycleScope.launch {
                val saved = container.playbackRepository.get(mediaKey)
                val startAt = if (saved != null && saved.positionMs > 10_000 &&
                    (saved.durationMs <= 0 || saved.positionMs < saved.durationMs - 15_000)
                ) {
                    saved.positionMs
                } else {
                    0L
                }
                manager.play(url, mediaKey, title, imageUrl, startAt)
            }
        } else {
            setContent {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    MtvTheme {
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
        // PlayerScreen también guarda la posición al salir; esto es respaldo.
        appContainer.playerManager.stop()
        super.onDestroy()
    }
}
