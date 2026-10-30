package com.arsys.netatmo.service

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent plain-text diagnostic log for automation timing (alarm armed, alarm received,
 * worker run, trigger decision, action result). Lives in the app's private files dir and is
 * shown/shared from the "Diagnóstico" screen. Never write tokens, passwords or ids of the
 * Netatmo account here: only automation names, trigger types, instants and error codes.
 */
object DiagnosticLog {
    private const val FILE_NAME = "diagnostico-automatizaciones.log"
    private const val MAX_BYTES = 200_000L
    private const val KEEP_BYTES = 120_000L
    private val lock = Any()

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    fun log(context: Context, tag: String, message: String) {
        try {
            synchronized(lock) {
                val f = file(context)
                if (f.exists() && f.length() > MAX_BYTES) {
                    val tail = f.readText().takeLast(KEEP_BYTES.toInt())
                    f.writeText(tail.substringAfter('\n', tail))
                }
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                f.appendText("$ts [$tag] $message\n")
            }
        } catch (_: Exception) {
            // Logging must never break an automation.
        }
    }

    fun read(context: Context): String = try {
        synchronized(lock) { file(context).takeIf { it.exists() }?.readText().orEmpty() }
    } catch (_: Exception) { "" }

    fun clear(context: Context) {
        try { synchronized(lock) { file(context).delete() } } catch (_: Exception) {}
    }

    fun fmt(ms: Long): String =
        if (ms <= 0) "-" else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ms))
}
