package com.mtv.iptv.player.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.mtv.iptv.appContainer

/**
 * Servicio en primer plano que ejecuta las descargas de Media3.
 * Declarado en el Manifest con foregroundServiceType="dataSync".
 */
class MtvDownloadService : DownloadService(NOTIFICATION_ID) {

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Descargas",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Descargando contenido" }
        )
    }

    override fun getDownloadManager(): DownloadManager =
        appContainer.downloadModule.downloadManager

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification {
        val activas = downloads.filter {
            it.state == Download.STATE_DOWNLOADING ||
                it.state == Download.STATE_QUEUED ||
                it.state == Download.STATE_RESTARTING
        }
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Descargando contenido")
        if (activas.isNotEmpty()) {
            val maxPct = activas.maxOf { it.percentDownloaded.toInt().coerceIn(0, 100) }
            builder
                .setContentText("${activas.size} descarga(s) en curso")
                .setProgress(100, maxPct, false)
        } else {
            builder.setContentText("Descargas")
        }
        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "mtv_descargas"
        private const val NOTIFICATION_ID = 1001
    }
}
