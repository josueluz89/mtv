package com.mtv.iptv.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY name")
    fun observeAll(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers ORDER BY name")
    suspend fun getAll(): List<ServerEntity>

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getById(id: Long): ServerEntity?

    @Query("SELECT COUNT(*) FROM servers")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(server: ServerEntity): Long

    @Delete
    suspend fun delete(server: ServerEntity)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY name")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT COUNT(*) FROM favorites WHERE serverId = :serverId AND kind = :kind AND refId = :refId")
    suspend fun exists(serverId: Long, kind: String, refId: String): Int

    @Upsert
    suspend fun upsert(fav: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE serverId = :serverId AND kind = :kind AND refId = :refId")
    suspend fun deleteByRef(serverId: Long, kind: String, refId: String)

    @Delete
    suspend fun delete(fav: FavoriteEntity)
}

@Dao
interface PlaybackDao {
    @Query("SELECT * FROM playback WHERE mediaKey = :mediaKey")
    suspend fun get(mediaKey: String): PlaybackEntity?

    @Upsert
    suspend fun upsert(entity: PlaybackEntity)

    @Query("SELECT * FROM playback ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<PlaybackEntity>

    @Query("DELETE FROM playback WHERE mediaKey = :mediaKey")
    suspend fun deleteByKey(mediaKey: String)
}

@Dao
interface SpeedTestDao {
    @Insert
    suspend fun insert(record: SpeedTestRecord)

    @Query("SELECT * FROM speed_tests ORDER BY measuredAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<SpeedTestRecord>

    @Query("SELECT * FROM speed_tests ORDER BY measuredAt DESC")
    fun observeAll(): Flow<List<SpeedTestRecord>>

    @Query("DELETE FROM speed_tests")
    suspend fun clear()
}
