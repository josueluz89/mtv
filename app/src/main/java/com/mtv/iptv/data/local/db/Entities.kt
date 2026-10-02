package com.mtv.iptv.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Servidor Xtream guardado por el usuario. */
@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val url: String = "",
    val username: String = "",
    val password: String = "",
)

/** Favorito: canal en vivo, película o serie. */
@Entity(tableName = "favorites", primaryKeys = ["serverId", "kind", "refId"])
data class FavoriteEntity(
    val serverId: Long = 0,
    /** "live" | "vod" | "series" */
    val kind: String = "",
    /** stream_id o series_id */
    val refId: String = "",
    val name: String = "",
    val imageUrl: String = "",
)

/** Posición de reproducción para "Seguir viendo". */
@Entity(tableName = "playback")
data class PlaybackEntity(
    @PrimaryKey val mediaKey: String = "",
    val name: String = "",
    val imageUrl: String = "",
    /** URL directa de reproducción (para continuar sin navegar). */
    val url: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = 0,
)
