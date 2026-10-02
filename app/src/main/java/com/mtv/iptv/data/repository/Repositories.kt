package com.mtv.iptv.data.repository

import com.mtv.iptv.data.local.db.FavoriteDao
import com.mtv.iptv.data.local.db.FavoriteEntity
import com.mtv.iptv.data.local.db.PlaybackDao
import com.mtv.iptv.data.local.db.PlaybackEntity
import com.mtv.iptv.data.local.db.ServerDao
import com.mtv.iptv.data.local.db.ServerEntity
import kotlinx.coroutines.flow.Flow

class ServerRepository(private val dao: ServerDao) {
    fun observeAll(): Flow<List<ServerEntity>> = dao.observeAll()
    suspend fun getAll(): List<ServerEntity> = dao.getAll()
    suspend fun getById(id: Long): ServerEntity? = dao.getById(id)
    suspend fun upsert(server: ServerEntity): Long = dao.upsert(server)
    suspend fun delete(server: ServerEntity) = dao.delete(server)

    /**
     * Servidores precargados (solo URL; el usuario ingresa su usuario/clave en la app).
     * Se insertan una sola vez, cuando la tabla está vacía.
     */
    suspend fun ensurePresets() {
        if (dao.count() == 0) {
            dao.upsert(ServerEntity(name = "Lion TV", url = "http://liontv.es:8080"))
            dao.upsert(ServerEntity(name = "TVPrem", url = "https://tvprem.pro:80"))
            dao.upsert(ServerEntity(name = "CC IPTV", url = "http://cciptv.es:80"))
        }
    }
}

class FavoritesRepository(private val dao: FavoriteDao) {
    fun observeAll(): Flow<List<FavoriteEntity>> = dao.observeAll()

    suspend fun isFavorite(serverId: Long, kind: String, refId: String): Boolean =
        dao.exists(serverId, kind, refId) > 0

    /** Alterna el favorito. Devuelve true si quedó agregado. */
    suspend fun toggle(fav: FavoriteEntity): Boolean =
        if (dao.exists(fav.serverId, fav.kind, fav.refId) > 0) {
            dao.deleteByRef(fav.serverId, fav.kind, fav.refId)
            false
        } else {
            dao.upsert(fav)
            true
        }

    suspend fun delete(fav: FavoriteEntity) = dao.delete(fav)
}

class PlaybackRepository(private val dao: PlaybackDao) {
    suspend fun get(mediaKey: String): PlaybackEntity? = dao.get(mediaKey)
    suspend fun save(entity: PlaybackEntity) = dao.upsert(entity)
    suspend fun recent(limit: Int = 15): List<PlaybackEntity> = dao.recent(limit)
    suspend fun delete(mediaKey: String) = dao.deleteByKey(mediaKey)
}
