package com.mtv.iptv.data.repository

import com.mtv.iptv.data.local.db.FavoriteDao
import com.mtv.iptv.data.local.db.FavoriteEntity
import com.mtv.iptv.data.local.db.PlaybackDao
import com.mtv.iptv.data.local.db.PlaybackEntity
import com.mtv.iptv.data.local.db.ServerDao
import com.mtv.iptv.data.local.db.ServerEntity
import com.mtv.iptv.data.local.db.SpeedTestDao
import com.mtv.iptv.data.local.db.SpeedTestRecord
import kotlinx.coroutines.flow.Flow

class ServerRepository(private val dao: ServerDao) {
    fun observeAll(): Flow<List<ServerEntity>> = dao.observeAll()
    suspend fun getAll(): List<ServerEntity> = dao.getAll()
    suspend fun getById(id: Long): ServerEntity? = dao.getById(id)
    suspend fun upsert(server: ServerEntity): Long = dao.upsert(server)
    suspend fun delete(server: ServerEntity) = dao.delete(server)

    /**
     * Fila única de servidor (los DNS están ocultos en el código, no en la UI).
     * Devuelve la primera fila existente o crea una (name="MTV").
     * La fila se persiste de inmediato para que su id sea estable y los
     * favoritos (keyed por serverId) sigan funcionando.
     */
    suspend fun getOrCreateSingle(): ServerEntity {
        dao.getAll().firstOrNull()?.let { return it }
        val id = dao.upsert(ServerEntity(name = "MTV"))
        return ServerEntity(id = id, name = "MTV")
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

/** Historial de mediciones del test de velocidad. */
class SpeedTestHistoryRepository(private val dao: SpeedTestDao) {
    fun observeAll(): Flow<List<SpeedTestRecord>> = dao.observeAll()

    suspend fun record(mbps: Double, dnsLabel: String, verdict: String) {
        dao.insert(
            SpeedTestRecord(
                mbps = mbps,
                dnsLabel = dnsLabel,
                verdict = verdict,
                measuredAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun clear() = dao.clear()
}
