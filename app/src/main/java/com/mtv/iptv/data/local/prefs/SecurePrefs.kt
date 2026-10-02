package com.mtv.iptv.data.local.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Credenciales Xtream cifradas (EncryptedSharedPreferences, androidx.security).
 *
 * Se guardan POR SERVIDOR con keys "cred_{serverId}_user" / "cred_{serverId}_pass",
 * más el id del último servidor usado ("last_server_id").
 *
 * NOTA: requiere la dependencia androidx.security:security-crypto (1.0.0+),
 * que el coordinador agrega en build.gradle.kts.
 */
class SecurePrefs(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "mtv_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** Guarda el usuario y la clave del servidor indicado. */
    fun saveCredentials(serverId: Long, user: String, pass: String) {
        prefs.edit()
            .putString("cred_${serverId}_user", user)
            .putString("cred_${serverId}_pass", pass)
            .apply()
    }

    /** Devuelve (usuario, clave) del servidor, o null si no hay guardadas. */
    fun getCredentials(serverId: Long): Pair<String, String>? {
        val user = prefs.getString("cred_${serverId}_user", null)
        val pass = prefs.getString("cred_${serverId}_pass", null)
        return if (user.isNullOrBlank() || pass.isNullOrBlank()) null else user to pass
    }

    /** Borra las credenciales del servidor indicado. */
    fun clearCredentials(serverId: Long) {
        prefs.edit()
            .remove("cred_${serverId}_user")
            .remove("cred_${serverId}_pass")
            .apply()
    }

    fun setLastServerId(id: Long) {
        prefs.edit().putLong("last_server_id", id).apply()
    }

    /** -1 si nunca se guardó. */
    fun getLastServerId(): Long = prefs.getLong("last_server_id", -1L)

    /** Borra TODO lo guardado (credenciales de todos los servidores + último id). */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
