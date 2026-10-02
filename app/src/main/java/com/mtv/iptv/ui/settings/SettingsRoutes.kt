package com.mtv.iptv.ui.settings

/**
 * Rutas del navegador interno de Ajustes (jerárquico).
 *
 * Ajustes tiene su propio NavHost dentro de la ruta "settings": la raíz
 * lista los grupos y cada grupo abre su pantalla secundaria.
 */
object SettingsRoutes {
    const val ROOT = "settings_root"
    const val GENERAL = "settings/general"
    const val DATA = "settings/data"
    const val USERS = "settings/users"
    const val PLAYLISTS = "settings/playlists"
    const val DOWNLOADS_PREFS = "settings/downloads_prefs"
    const val NETWORK = "settings/network"
    const val PLAYBACK = "settings/playback"
    const val REMOTE = "settings/remote"
    const val PARENTAL = "settings/parental"
    const val SUBTITLES = "settings/subtitles"
    const val APPEARANCE = "settings/appearance"
    const val ABOUT = "settings/about"
}
