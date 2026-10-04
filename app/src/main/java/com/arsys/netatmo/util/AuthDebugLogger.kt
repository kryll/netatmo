package com.arsys.netatmo.util

import android.util.Log
import com.arsys.netatmo.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AuthDebugLogger {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun log(msg: String) {
        if (!BuildConfig.DEBUG) return
        val entry = "[${fmt.format(Date())}] $msg"
        Log.d("NetatmoAuth", msg)
        _logs.update { (it + entry).takeLast(100) }
    }

    fun clear() {
        _logs.value = emptyList()
    }

    fun allLogsAsText(): String = _logs.value.joinToString("\n")
}
