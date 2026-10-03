package com.arsys.netatmo.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AuthDebugLogger {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun log(msg: String, context: Context? = null) {
        val entry = "[${fmt.format(Date())}] $msg"
        Log.d("NetatmoAuth", msg)
        _logs.update { (it + entry).takeLast(100) }
        context?.let { writeToFile(it, entry) }
    }

    fun clear() {
        _logs.value = emptyList()
    }

    fun getLogFile(context: Context): File =
        File(context.getExternalFilesDir(null), "auth_debug.log")

    private fun writeToFile(context: Context, entry: String) {
        try {
            val f = getLogFile(context)
            f.appendText("$entry\n")
        } catch (_: Exception) {}
    }

    fun allLogsAsText(): String = _logs.value.joinToString("\n")
}
