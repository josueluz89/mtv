package com.mtv.iptv

import android.app.PictureInPictureParams
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.mtv.iptv.di.LocalAppContainer
import com.mtv.iptv.data.remote.xtream.streamExtensionFromUrl
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

    @Volatile
    private var pipOnHomeCached = false

    @Volatile
    private var afrEnabledCached = false

    @Volatile
    private var lastAfrModeId = 0

    private fun resizeModeFor(pref: String): Int = when (pref) {
        "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    private fun enterPip() {
        try {
            enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        } catch (_: Exception) {
        }
    }

    /**
     * Auto frame rate (best-effort): busca en los modos soportados del display
     * el refreshRate más cercano a los fps del video y lo pide como modo
     * preferido de la ventana.
     */
    private fun applyAfr(frameRate: Float) {
        try {
            val disp = if (Build.VERSION.SDK_INT >= 30) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            } ?: return
            val best = disp.supportedModes.minByOrNull {
                kotlin.math.abs(it.refreshRate - frameRate)
            } ?: return
            // Display no expone getModeId(): se evita re-aplicar el mismo modo
            // con el último id que nosotros mismos pedimos.
            if (best.modeId == 0 || best.modeId == lastAfrModeId) return
            val attrs = window.attributes
            attrs.preferredDisplayModeId = best.modeId
            window.attributes = attrs
            lastAfrModeId = best.modeId
        } catch (_: Exception) {
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Home durante la reproducción: PiP si el ajuste está activo y hay
        // reproducción en curso.
        if (pipOnHomeCached && appContainer.playerManager.player.isPlaying) {
            enterPip()
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
            // La lista de zapping también se usa en TV (diálogo "Canales").
            val zapChannels = zapFromIntent(intent)

            // En vivo: recordar el último canal (Ajustes → "Abrir el último canal").
            if (isLive) {
                val streamId = mediaKey.removePrefix("live:")
                lifecycleScope.launch {
                    try {
                        container.userPrefs.setLastLiveChannel("$streamId|$title")
                    } catch (_: Exception) {
                    }
                }
            }

            // Prefs que se leen una vez al abrir el reproductor (se observan
            // como caché para no bloquear el hilo principal en cada uso).
            lifecycleScope.launch {
                try {
                    container.userPrefs.pipOnHome.collect { pipOnHomeCached = it }
                } catch (_: Exception) {
                }
            }
            lifecycleScope.launch {
                try {
                    container.userPrefs.afrEnabled.collect { afrEnabledCached = it }
                } catch (_: Exception) {
                }
            }

            val frame = FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
            }
            val playerView = PlayerView(this).apply {
                // Sin controlador nativo: todo el control vive en TvPlayerOverlay
                // (Compose), 100% operable con D-pad y estilo TiviMate.
                useController = false
                isFocusable = false
            }
            // Aspecto inicial desde el pref (el overlay lo cambia en vivo).
            lifecycleScope.launch {
                try {
                    playerView.resizeMode = resizeModeFor(container.userPrefs.aspectRatio.first())
                } catch (_: Exception) {
                }
            }
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
            // Asigna la instancia vigente del player y (si AFR está activo)
            // engancha el ajuste de tasa de refresco a sus cambios de formato.
            fun watchPlayer(p: ExoPlayer) {
                playerView.player = p
                if (!afrEnabledCached) return
                p.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        val fps = p.videoFormat?.frameRate ?: 0f
                        if (fps > 0f) applyAfr(fps)
                    }
                })
            }
            // El player puede reconstruirse si cambian los ajustes de
            // decodificación (PlayerManager.playerEpoch): reasignar siempre la
            // instancia vigente y reenganchar el listener de AFR.
            lifecycleScope.launch {
                try {
                    manager.playerEpoch.collect { watchPlayer(manager.player) }
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
            // Overlay = controlador completo (título, progreso, transporte,
            // audio, subtítulos). Maneja su propio mostrar/ocultar con OK.
            val overlayView = ComposeView(this).apply {
                isFocusableInTouchMode = true
                setContent {
                    CompositionLocalProvider(LocalAppContainer provides container) {
                        MtvUiTheme {
                            TvPlayerOverlay(
                                title = title,
                                isLive = isLive,
                                mediaKey = mediaKey,
                                manager = manager,
                                zapChannels = zapChannels,
                                subTmdbId = subTmdbId,
                                subSeason = subSeason,
                                subEpisode = subEpisode,
                                onAspectChange = { mode ->
                                    lifecycleScope.launch {
                                        try {
                                            container.userPrefs.setAspectRatio(mode)
                                        } catch (_: Exception) {
                                        }
                                    }
                                    playerView.resizeMode = resizeModeFor(mode)
                                },
                                onPipClick = { enterPip() },
                                onClose = { finish() },
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
            setContentView(frame)
            // El overlay necesita el foco para recibir OK del D-pad.
            overlayView.requestFocus()
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
                manager.play(url, mediaKey, title, imageUrl, startAt) {
                    // Re-login silencioso + URL fresca: PlayerManager solo lo
                    // invoca ante un 401/403 del stream, una vez por item.
                    val repo = container.xtreamRepository
                    if (!repo.reLogin()) return@play null
                    try {
                        val ext = streamExtensionFromUrl(url)
                        when {
                            mediaKey.startsWith("live:") ->
                                repo.liveUrl(mediaKey.removePrefix("live:").toInt())
                            mediaKey.startsWith("vod:") ->
                                repo.vodUrl(mediaKey.removePrefix("vod:").toInt(), ext)
                            mediaKey.startsWith("ep:") ->
                                repo.episodeUrl(mediaKey.removePrefix("ep:"), ext)
                            else -> null
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
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
