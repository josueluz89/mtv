package com.mtv.iptv.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.ExoPlayer
import com.mtv.iptv.data.local.db.PlaybackEntity
import com.mtv.iptv.data.repository.PlaybackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Un solo ExoPlayer para toda la app (HLS/DASH/TS progresivo).
 * Guarda y restaura la posición en [PlaybackRepository].
 */
class PlayerManager(
    appContext: Context,
    private val playbackRepository: PlaybackRepository,
) {

    private val trackSelector = DefaultTrackSelector(appContext)

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setTrackSelector(trackSelector)
        .build()

    val selector: DefaultTrackSelector get() = trackSelector

    var currentMediaKey: String = ""
        private set
    var currentTitle: String = ""
        private set
    var currentImageUrl: String = ""
        private set
    var currentUrl: String = ""
        private set

    fun play(url: String, mediaKey: String, title: String, imageUrl: String, startPositionMs: Long = 0) {
        currentMediaKey = mediaKey
        currentTitle = title
        currentImageUrl = imageUrl
        currentUrl = url
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        if (startPositionMs > 0) player.seekTo(startPositionMs)
        player.playWhenReady = true
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    /** Guarda la posición actual (para "Seguir viendo"). */
    suspend fun savePosition() = withContext(Dispatchers.IO) {
        val key = currentMediaKey
        if (key.isBlank()) return@withContext
        val pos = player.currentPosition
        val dur = player.duration.takeIf { it > 0 } ?: 0L
        // Si ya terminó (últimos 10s), guardar 0 para no reanudar al final.
        val positionToSave = if (dur > 0 && pos >= dur - 10_000) 0L else pos
        playbackRepository.save(
            PlaybackEntity(
                mediaKey = key,
                name = currentTitle,
                imageUrl = currentImageUrl,
                url = currentUrl,
                positionMs = positionToSave,
                durationMs = dur,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    fun release() {
        player.release()
    }

    // ---------------- Pistas de audio / subtítulos ----------------

    data class TrackOption(val groupIndex: Int, val trackIndex: Int, val label: String)

    fun audioTracks(): List<TrackOption> = trackOptions(C.TRACK_TYPE_AUDIO)

    fun textTracks(): List<TrackOption> = trackOptions(C.TRACK_TYPE_TEXT)

    private fun trackOptions(trackType: Int): List<TrackOption> {
        val out = mutableListOf<TrackOption>()
        val groups = player.currentTracks.groups
        for (gi in 0 until groups.size) {
            val group = groups[gi]
            if (group.type != trackType || group.length == 0) continue
            for (ti in 0 until group.length) {
                val f = group.getTrackFormat(ti)
                val label = f.label ?: f.language ?: f.sampleMimeType ?: "Pista ${ti + 1}"
                out += TrackOption(gi, ti, label)
            }
        }
        return out
    }

    fun selectAudio(groupIndex: Int, trackIndex: Int) {
        val group = player.currentTracks.groups[groupIndex]
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex)))
        )
    }

    fun clearAudioOverride() {
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        )
    }

    fun selectText(groupIndex: Int, trackIndex: Int) {
        val group = player.currentTracks.groups[groupIndex]
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex)))
        )
    }

    fun disableText() {
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        )
    }
}
