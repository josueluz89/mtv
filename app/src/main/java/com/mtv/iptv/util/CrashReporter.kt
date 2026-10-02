package com.mtv.iptv.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnóstico: bitácora en [filesDir]/mtv_diagnostico.log con marcas de tiempo
 * de cada fase de carga, más el stack trace completo si la app se cierra por
 * una excepción no capturada (incluye OutOfMemoryError).
 *
 * La pantalla de login muestra el contenido si hay un CRASH registrado, con
 * botón para copiarlo y pasarlo al desarrollador.
 */
object CrashReporter {
    private const val FILE = "mtv_diagnostico.log"
    private const val MAX_BYTES = 300_000L

    private fun file(context: Context): File = File(context.filesDir, FILE)

    private fun ts(): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())

    @Synchronized
    fun log(context: Context, tag: String, msg: String) {
        try {
            val f = file(context)
            if (f.exists() && f.length() > MAX_BYTES) f.writeText("")
            f.appendText("[${ts()}][$tag] $msg\n")
        } catch (_: Exception) {
        }
    }

    fun read(context: Context): String = try {
        file(context).takeIf { it.exists() }?.readText().orEmpty()
    } catch (_: Exception) {
        ""
    }

    fun hasCrash(context: Context): Boolean = read(context).contains("===== CRASH")

    fun readResumen(context: Context): String = try {
        File(context.filesDir, "crash_resumen.log").takeIf { it.exists() }?.readText().orEmpty()
    } catch (_: Exception) {
        ""
    }

    /**
     * Resumen compacto para diagnóstico: tipo de excepción, hilo, cantidad de
     * frames (un número enorme sugiere StackOverflowError), primeros 25 frames
     * y las últimas 40 líneas de bitácora ANTES del crash (qué estaba cargando).
     */
    private fun writeResumen(appContext: Context, t: Thread, e: Throwable) {
        try {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val atLines = sw.toString().lines().filter { it.trimStart().startsWith("at ") }
            val prevPhases = try {
                file(appContext).readLines().takeLast(40)
            } catch (_: Exception) {
                emptyList()
            }
            val resumen = buildString {
                appendLine("===== CRASH RESUMEN =====")
                appendLine("excepcion: $e")
                appendLine("thread: ${t.name}")
                appendLine("frames: ${atLines.size}")
                appendLine("--- primeros 25 frames ---")
                atLines.take(25).forEach { appendLine(it.trim()) }
                appendLine("--- últimas 40 líneas de bitácora antes del crash ---")
                prevPhases.forEach { appendLine(it) }
            }
            File(appContext.filesDir, "crash_resumen.log").writeText(resumen)
        } catch (_: Exception) {
        }
    }

    fun clear(context: Context) {
        try {
            file(context).delete()
            File(context.filesDir, "crash_resumen.log").delete()
        } catch (_: Exception) {
        }
    }

    /** Handler global: guarda el stack trace y deja que la app se cierre normal. */
    fun install(appContext: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                writeResumen(appContext, t, e)
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                file(appContext).appendText(
                    "\n===== CRASH ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} " +
                        "[thread=${t.name}] =====\n$e\n$sw\n",
                )
            } catch (_: Exception) {
            }
            prev?.uncaughtException(t, e)
        }
        log(appContext, "app", "inicio (diagnóstico activo)")
    }
}
