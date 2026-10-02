package com.mtv.iptv.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ServerEntity::class,
        FavoriteEntity::class,
        PlaybackEntity::class,
        SpeedTestRecord::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class MtvDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playbackDao(): PlaybackDao
    abstract fun speedTestDao(): SpeedTestDao

    companion object {
        fun create(context: Context): MtvDatabase =
            Room.databaseBuilder(context, MtvDatabase::class.java, "mtv.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
