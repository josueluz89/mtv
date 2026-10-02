package com.mtv.iptv.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.mtv.iptv.data.local.db.MtvDatabase
import com.mtv.iptv.data.local.prefs.SecurePrefs
import com.mtv.iptv.data.local.prefs.UserPrefs
import com.mtv.iptv.data.remote.tmdb.TmdbClient
import com.mtv.iptv.data.remote.tmdb.TmdbRepository
import com.mtv.iptv.data.remote.xtream.XtreamClient
import com.mtv.iptv.data.remote.xtream.XtreamRepository
import com.mtv.iptv.data.repository.FavoritesRepository
import com.mtv.iptv.data.repository.PlaybackRepository
import com.mtv.iptv.data.repository.ServerRepository
import com.mtv.iptv.player.PlayerManager
import com.mtv.iptv.player.downloads.DownloadModule
import com.mtv.iptv.util.CrashReporter

/** DI manual: un solo contenedor por aplicación, sin Hilt. */
class AppContainer(appContext: Context) {

    val database: MtvDatabase by lazy { MtvDatabase.create(appContext) }
    val userPrefs: UserPrefs by lazy { UserPrefs(appContext) }
    val securePrefs: SecurePrefs by lazy { SecurePrefs(appContext) }

    val xtreamClient = XtreamClient()
    val xtreamRepository = XtreamRepository(xtreamClient).also {
        it.eventLog = { msg -> CrashReporter.log(appContext, "xtream", msg) }
    }

    val tmdbClient = TmdbClient()
    val tmdbRepository = TmdbRepository(tmdbClient)

    val serverRepository by lazy { ServerRepository(database.serverDao()) }
    val favoritesRepository by lazy { FavoritesRepository(database.favoriteDao()) }
    val playbackRepository by lazy { PlaybackRepository(database.playbackDao()) }

    val downloadModule = DownloadModule(appContext)

    val playerManager by lazy {
        PlayerManager(appContext, playbackRepository, downloadModule.cacheDataSourceFactory)
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer no provisto")
}
