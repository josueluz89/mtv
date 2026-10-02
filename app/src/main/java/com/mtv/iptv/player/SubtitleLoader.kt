package com.mtv.iptv.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import java.io.File

/**
 * Adjunta un subtítulo SRT descargado (p. ej. desde OpenSubtitles) al video
 * que se está reproduciendo, sin perder la posición actual.
 *
 * Reconstruye el MediaItem en curso agregando el SRT como pista de texto
 * externa y la selecciona automáticamente cuando ExoPlayer la publica.
 *
 * Debe llamarse desde el hilo principal (como todo acceso a `player`).
 * No toca [PlayerManager.currentMediaKey]: el "seguir viendo" sigue
 * funcionando igual.
 */
fun PlayerManager.attachExternalSubtitle(context: Context, srtFile: File, label: String) {
    val player = this.player
    val positionMs = player.currentPosition
    val wasPlaying = player.playWhenReady

    val subtitle = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(srtFile))
        .setMimeType(MimeTypes.APPLICATION_SUBRIP)
        .setLanguage("es")
        .setLabel(label)
        .build()
    val newItem = MediaItem.Builder()
        .setUri(currentUrl)
        .setSubtitleConfigurations(listOf(subtitle))
        .build()

    // Selecciona la pista del SRT en cuanto ExoPlayer la publica. El listener
    // se queda hasta lograrlo (los grupos de pistas pueden publicarse en
    // más de un evento).
    val listener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            var selected = false
            for (group in tracks.groups) {
                if (group.type != C.TRACK_TYPE_TEXT || group.length == 0) continue
                for (ti in 0 until group.length) {
                    val f = group.getTrackFormat(ti)
                    if (f.label == label || f.sampleMimeType == MimeTypes.APPLICATION_SUBRIP) {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(
                                TrackSelectionOverride(group.mediaTrackGroup, listOf(ti))
                            )
                            .build()
                        selected = true
                        break
                    }
                }
                if (selected) break
            }
            if (selected) player.removeListener(this)
        }
    }
    player.addListener(listener)
    player.setMediaItem(newItem, positionMs)
    player.prepare()
    player.playWhenReady = wasPlaying
}
