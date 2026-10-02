package com.mtv.iptv.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mtv.iptv.appContainer
import com.mtv.iptv.data.remote.xtream.LoginResult
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Configuración y programación del refresco automático del catálogo.
 *
 * Frecuencias: "6h" | "12h" | "24h" | "manual" ("manual" = solo con el
 * botón "Actualizar ahora", sin Worker).
 */
object CatalogWork {
    const val UNIQUE_NAME = "catalog_refresh"

    fun freqLabel(freq: String): String = when (freq) {
        "6h" -> "Cada 6 horas"
        "24h" -> "Cada 24 horas"
        "manual" -> "Solo manual"
        else -> "Cada 12 horas"
    }

    fun freqMillis(freq: String): Long = when (freq) {
        "6h" -> 6 * 3600 * 1000L
        "24h" -> 24 * 3600 * 1000L
        "manual" -> Long.MAX_VALUE
        else -> 12 * 3600 * 1000L
    }

    /**
     * (Re)programa el Worker periódico según los ajustes. Si el segundo
     * plano está apagado o la frecuencia es manual, lo cancela.
     */
    fun schedule(context: Context, freq: String, background: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!background || freq == "manual") {
            wm.cancelUniqueWork(UNIQUE_NAME)
            return
        }
        val hours = when (freq) {
            "6h" -> 6L
            "24h" -> 24L
            else -> 12L
        }
        val request = PeriodicWorkRequestBuilder<CatalogRefreshWorker>(hours, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

/**
 * Actualiza el catálogo del proveedor en segundo plano.
 *
 * Respeta los ajustes: si el segundo plano está apagado o la frecuencia es
 * manual, no hace nada. Si hay credenciales guardadas, revalida la sesión
 * en silencio y descarga el catálogo completo (reemplaza memoria y disco).
 * Nunca molesta al usuario: sin notificaciones ni UI.
 */
class CatalogRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        return try {
            if (!container.userPrefs.catalogBackground.first()) return Result.success()
            val freq = container.userPrefs.catalogFreq.first()
            if (freq == "manual") return Result.success()
            val last = try {
                container.userPrefs.getLastCatalogRefresh()
            } catch (_: Exception) {
                0L
            }
            if (System.currentTimeMillis() - last < CatalogWork.freqMillis(freq)) {
                return Result.success()
            }
            val secure = container.securePrefs
            val lastId = secure.getLastServerId()
            var creds = if (lastId > 0) secure.getCredentials(lastId) else null
            if (creds == null) {
                val u = container.userPrefs.getUsername()
                val p = container.userPrefs.getPassword()
                if (u.isNotBlank() && p.isNotBlank()) creds = u to p
            }
            val (user, pass) = creds ?: return Result.success()
            when (container.xtreamRepository.loginAuto(user, pass)) {
                is LoginResult.Ok -> {
                    if (container.xtreamRepository.forceRefreshCatalog()) {
                        val now = System.currentTimeMillis()
                        container.userPrefs.setLastCatalogRefresh(now)
                        container.catalogRefreshTick.value = now
                    }
                    Result.success()
                }
                else -> Result.retry()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
