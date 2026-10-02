package com.mtv.iptv.player

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import com.mtv.iptv.PlayerActivity
import com.mtv.iptv.data.local.db.PlaybackEntity
import com.mtv.iptv.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val VLC_PACKAGE = "org.videolan.vlc"
private const val VLC_STORE_PAGE = "details?id=$VLC_PACKAGE"

/**
 * Reproductor externo (VLC).
 *
 * Resuelve si corresponde usar el reproductor externo según la preferencia
 * "player_mode" ("auto" | "internal" | "external") y lanza el contenido en
 * VLC con un ACTION_VIEW. Si VLC no está instalado muestra un diálogo en
 * español para instalarlo o elegir otra app.
 */
object ExternalPlayer {

    /** Decide si el modo [mode] implica usar el reproductor externo. */
    fun resolveUseExternal(mode: String, context: Context): Boolean = when (mode) {
        "external" -> true
        "internal" -> false
        else -> isVlcInstalled(context)
    }

    /** Chequea si VLC está instalado (paquete org.videolan.vlc). */
    fun isVlcInstalled(context: Context): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(VLC_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * Punto único de reproducción: respeta el ajuste "Reproductor".
     * Lee la preferencia "player_mode" ("auto" | "internal" | "external"):
     * externo (o auto con VLC instalado) -> app VLC vía [playExternal];
     * en otro caso -> reproductor interno ([PlayerActivity], ExoPlayer).
     * [zapChannels] solo aplica al reproductor interno (zapping en vivo).
     * Llamar desde cualquier onClick / handler de reproducción.
     */
    fun play(
        context: Context,
        container: AppContainer,
        url: String,
        title: String,
        mediaKey: String,
        imageUrl: String = "",
        zapChannels: List<ZapChannel> = emptyList(),
    ) {
        val activity = context as? ComponentActivity
        CoroutineScope(Dispatchers.Main).launch {
            val mode = withContext(Dispatchers.IO) {
                container.userPrefs.playerMode.first()
            }
            if (activity != null && resolveUseExternal(mode, activity)) {
                playExternal(activity, url, title, mediaKey, container)
            } else {
                PlayerActivity.start(context, url, title, mediaKey, imageUrl, zapChannels)
            }
        }
    }

    /**
     * Reproduce [url] en VLC externo. Si hay un archivo local descargado
     * (vía [com.mtv.iptv.player.downloads.DownloadModule.localPlaybackFile])
     * se usa en lugar de la URL remota. Si VLC no está instalado muestra el
     * diálogo de instalación. En ambos casos marca el contenido como
     * iniciado/visto en [com.mtv.iptv.data.repository.PlaybackRepository]
     * (con reproductor externo no se puede rastrear la posición, así que se
     * guarda posición 0).
     */
    fun playExternal(
        activity: ComponentActivity,
        url: String,
        title: String,
        mediaKey: String,
        container: AppContainer,
    ) {
        runOnMain {
            if (isVlcInstalled(activity)) {
                launchVlc(activity, url, title, mediaKey, container)
            } else {
                markStarted(container, mediaKey, title, url)
                showInstallDialog(activity, url, title)
            }
        }
    }

    private fun launchVlc(
        activity: ComponentActivity,
        url: String,
        title: String,
        mediaKey: String,
        container: AppContainer,
    ) {
        val localFile = runCatching { container.downloadModule.localPlaybackFile(url) }.getOrNull()
        val uri = if (localFile != null) Uri.fromFile(localFile) else Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            setPackage(VLC_PACKAGE)
            putExtra("title", title)
        }
        try {
            markStarted(container, mediaKey, title, url)
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            markStarted(container, mediaKey, title, url)
            showInstallDialog(activity, url, title)
        }
    }

    private fun showInstallDialog(activity: ComponentActivity, url: String, title: String) {
        val isAmazon = Build.MANUFACTURER.equals("Amazon", ignoreCase = true)
        val message = if (isAmazon) {
            "Para reproducir con el reproductor externo necesitás instalar VLC. Buscá VLC en la Amazon Appstore."
        } else {
            "Para reproducir con el reproductor externo necesitás instalar VLC. ¿Querés instalarlo desde Google Play?"
        }
        AlertDialog.Builder(activity)
            .setTitle("VLC no está instalado")
            .setMessage(message)
            .setPositiveButton("Instalar") { _, _ -> openStore(activity) }
            .setNeutralButton("Otra app") { _, _ -> openChooser(activity, url, title) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openStore(activity: ComponentActivity) {
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://$VLC_STORE_PAGE")),
            )
        } catch (_: ActivityNotFoundException) {
            activity.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/$VLC_STORE_PAGE"),
                ),
            )
        }
    }

    private fun openChooser(activity: ComponentActivity, url: String, title: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            putExtra("title", title)
        }
        activity.startActivity(Intent.createChooser(intent, "Abrir con"))
    }

    /**
     * Marca el contenido como iniciado/visto: guarda posición 0 (con el
     * reproductor externo no se puede rastrear la posición real) y completa
     * los datos faltantes para que aparezca en "Seguir viendo".
     */
    private fun markStarted(
        container: AppContainer,
        mediaKey: String,
        title: String,
        url: String,
    ) {
        if (mediaKey.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val existing = container.playbackRepository.get(mediaKey)
                container.playbackRepository.save(
                    (existing ?: PlaybackEntity(mediaKey = mediaKey)).copy(
                        name = existing?.name?.takeIf { it.isNotBlank() } ?: title,
                        url = existing?.url?.takeIf { it.isNotBlank() } ?: url,
                        positionMs = 0L,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            Handler(Looper.getMainLooper()).post(block)
        }
    }
}
