package com.mtv.iptv

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.player.ZapChannel
import com.mtv.iptv.ui.common.MtvUiTheme
import com.mtv.iptv.ui.player.PlayerScreen
import com.mtv.iptv.ui.theme.MtvTheme
import com.mtv.iptv.ui.tv.TvPlayerOverlay
import kotlinx.coroutines.flow.first
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
        const val EXTRA_ZAP_IDS = "extra_zap_ids"
        const val EXTRA_ZAP_NAMES = "extra_zap_names"
        const val EXTRA_ZAP_NUMS = "extra_zap_nums"
        const val EXTRA_ZAP_ICONS = "extra_zap_icons"
        const val EXTRA_SUB_TMDB = "extra_sub_tmdb"
        const val EXTRA_SUB_SEASON = "extra_sub_season"
        const val EXTRA_SUB_EPISODE = "extra_sub_episode"

        fun start(
            context: Context,
            url: String,
            title: String,
            mediaKey: String,
            imageUrl: String = "",
            zap: List<ZapChannel> = emptyList(),
            subTmdbId: Int? = null,
            subSeason: Int? = null,
            subEpisode: Int? = null,
        ) {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MEDIA_KEY, mediaKey)
                putExtra(EXTRA_IMAGE_URL, imageUrl)
                if (zap.isNotEmpty()) {
                    putExtra(EXTRA_ZAP_IDS, zap.map { it.streamId }.toIntArray())
                    putExtra(EXTRA_ZAP_NAMES, zap.map { it.name }.toTypedArray())
                    putExtra(EXTRA_ZAP_NUMS, zap.map { it.num }.toIntArray())
                    putExtra(EXTRA_ZAP_ICONS, zap.map { it.icon }.toTypedArray())
                }
                if (subTmdbId != null) {
                    putExtra(EXTRA_SUB_TMDB, subTmdbId)
                    if (subSeason != null) putExtra(EXTRA_SUB_SEASON, subSeason)
                    if (subEpisode != null) putExtra(EXTRA_SUB_EPISODE, subEpisode)
                }
            }
            context.startActivity(intent)
        }

        /** Reconstruye la lista de zapping desde los extras del intent. */
        fun zapFromIntent(intent: Intent): List<ZapChannel> {
            val ids = intent.getIntArrayExtra(EXTRA_ZAP_IDS) ?: return emptyList()
            val names = intent.getStringArrayExtra(EXTRA_ZAP_NAMES) ?: return emptyList()
            val nums = intent.getIntArrayExtra(EXTRA_ZAP_NUMS) ?: return emptyList()
            val icons = intent.getStringArrayExtra(EXTRA_ZAP_ICONS) ?: emptyArray()
            val n = minOf(ids.size, names.size, nums.size)
            return List(n) { i ->
                ZapChannel(
                    streamId = ids[i],
                    name = names[i].orEmpty(),
                    num = nums[i],
                    icon = icons.getOrNull(i).orEmpty(),
                )
            }
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
            // Rama TV: PlayerView con su controlador (D-pad) + overlay Compose
            // con título y botones de opciones (Audio, Subtítulos, ±10 s).
            // El overlay se muestra y oculta JUNTO con el controlador, para
            // que el título no quede fijo para siempre.
            val subTmdbId = if (intent.hasExtra(EXTRA_SUB_TMDB)) intent.getIntExtra(EXTRA_SUB_TMDB, 0).takeIf { it > 0 } else null
            val subSeason = if (intent.hasExtra(EXTRA_SUB_SEASON)) intent.getIntExtra(EXTRA_SUB_SEASON, 0).takeIf { it > 0 } else null
            val subEpisode = if (intent.hasExtra(EXTRA_SUB_EPISODE)) intent.getIntExtra(EXTRA_SUB_EPISODE, 0).takeIf { it > 0 } else null
            val isLive = mediaKey.startsWith("live:")

            val frame = FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
            }
            val playerView = PlayerView(this).apply {
                useController = true
                setShowPreviousButton(false)
                setShowNextButton(false)
                controllerShowTimeoutMs = 4000
            }
            playerView.player = manager.player
            // Estilo de subtítulos (Ajustes → Subtítulos) también en la rama TV.
            lifecycleScope.launch {
                try {
                    val prefs = container.userPrefs
                    val dip = when (prefs.subtitleSize.first()) {
                        "S" -> 14f
                        "L" -> 24f
                        else -> 18f
                    }
                    val fg = when (prefs.subtitleColor.first()) {
                        "amarillo" -> Color.YELLOW
                        "cian" -> Color.CYAN
                        "verde" -> Color.GREEN
                        else -> Color.WHITE
                    }
                    val style = when (prefs.subtitleBackground.first()) {
                        "solido" -> CaptionStyleCompat(
                            fg, Color.BLACK, Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_NONE, Color.BLACK, null,
                        )
                        "ninguno" -> CaptionStyleCompat(
                            fg, Color.TRANSPARENT, Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, Color.BLACK, null,
                        )
                        else -> CaptionStyleCompat(
                            fg, 0x99000000.toInt(), Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_NONE, Color.BLACK, null,
                        )
                    }
                    playerView.subtitleView?.let { sv ->
                        sv.setFixedTextSize(TypedValue.COMPLEX_UNIT_DIP, dip)
                        sv.setApplyEmbeddedStyles(false)
                        sv.setStyle(style)
                    }
                } catch (_: Exception) {
                }
            }
            frame.addView(
                playerView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            // Overlay de opciones: visible solo junto con el controlador.
            val overlayVisible = mutableStateOf(true)
            val overlayView = ComposeView(this).apply {
                setContent {
                    CompositionLocalProvider(LocalAppContainer provides container) {
                        MtvUiTheme {
                            TvPlayerOverlay(
                                title = title,
                                visible = overlayVisible.value,
                                isLive = isLive,
                                manager = manager,
                                subTmdbId = subTmdbId,
                                subSeason = subSeason,
                                subEpisode = subEpisode,
                            )
                        }
                    }
                }
            }
            frame.addView(
                overlayView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            playerView.setControllerVisibilityListener(
                PlayerView.ControllerVisibilityListener { visibility ->
                    overlayVisible.value = visibility == View.VISIBLE
                },
            )
            setContentView(frame)
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
            // Rama móvil: la lista de zapping solo se usa en PlayerScreen.
            val zapChannels = zapFromIntent(intent)
            val subTmdbId = if (intent.hasExtra(EXTRA_SUB_TMDB)) intent.getIntExtra(EXTRA_SUB_TMDB, 0).takeIf { it > 0 } else null
            val subSeason = if (intent.hasExtra(EXTRA_SUB_SEASON)) intent.getIntExtra(EXTRA_SUB_SEASON, 0).takeIf { it > 0 } else null
            val subEpisode = if (intent.hasExtra(EXTRA_SUB_EPISODE)) intent.getIntExtra(EXTRA_SUB_EPISODE, 0).takeIf { it > 0 } else null
            setContent {
                val theme by container.userPrefs.theme.collectAsState(initial = "sistema")
                CompositionLocalProvider(LocalAppContainer provides container) {
                    MtvTheme(theme = theme) {
                        PlayerScreen(
                            url = url,
                            title = title,
                            mediaKey = mediaKey,
                            imageUrl = imageUrl,
                            onBack = { finish() },
                            zapChannels = zapChannels,
                            subTmdbId = subTmdbId,
                            subSeason = subSeason,
                            subEpisode = subEpisode,
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
