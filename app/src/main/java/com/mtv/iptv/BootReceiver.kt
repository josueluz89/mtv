package com.mtv.iptv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mtv.iptv.data.local.prefs.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Auto-arranque: si en Ajustes → General está activo "Abrir la app al
 * encender", lanza MainActivity al completar el boot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = UserPrefs(context.applicationContext)
                if (prefs.openOnBoot.first()) {
                    val launch = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(launch)
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
