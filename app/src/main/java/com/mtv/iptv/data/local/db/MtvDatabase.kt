package com.mtv.iptv.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ServerEntity::class, FavoriteEntity::class, PlaybackEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MtvDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playbackDao(): PlaybackDao

    companion object {
        fun create(context: Context): MtvDatabase =
            Room.databaseBuilder(context, MtvDatabase::class.java, "mtv.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
