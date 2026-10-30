package com.arsys.netatmo.ui.screens.automations

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavController
import com.arsys.netatmo.service.DiagnosticLog

/** Shows the persistent automation timing log plus the phone settings that can block alarms. */
@Composable
fun AutomationDiagnosticScreen(navController: NavController) {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val text = remember(refresh) { DiagnosticLog.read(context) }
    val state = remember(refresh) { phoneState(context) }
    val lines = remember(text) { text.lines().filter { it.isNotBlank() }.takeLast(400).reversed().joinToString("\n") }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("Diagnóstico", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { refresh++ }) { Text("Actualizar") }
            OutlinedButton(onClick = {
                val body = "Estado:\n$state\n\nRegistro (más reciente primero):\n${lines.takeLast(100_000)}"
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico automatizaciones Netatmo Smart")
                    putExtra(Intent.EXTRA_TEXT, body)
                }
                context.startActivity(Intent.createChooser(send, "Compartir o guardar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Compartir") }
            TextButton(onClick = { DiagnosticLog.clear(context); refresh++ }) { Text("Borrar") }
        }
        SelectionContainer {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                Text(state, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (lines.isEmpty()) "Sin registros todavía. Se escriben al armar alarmas, recibirlas y evaluar automatizaciones."
                    else lines,
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun phoneState(context: Context): String {
    val fmt = DiagnosticLog::fmt
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager?.canScheduleExactAlarms() == true
    val power = context.getSystemService(PowerManager::class.java)
    val ignoringBattery = power?.isIgnoringBatteryOptimizations(context.packageName) == true
    val notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val armed = context.getSharedPreferences("advanced_automation_state", Context.MODE_PRIVATE).getLong("alarm_next_at", 0L)
    return buildString {
        appendLine("Ahora: ${fmt(System.currentTimeMillis())}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Alarmas exactas permitidas: ${if (exact) "sí" else "NO (Ajustes > Apps > Netatmo Smart > Alarmas y recordatorios)"}")
        appendLine("Sin restricción de batería: ${if (ignoringBattery) "sí" else "no (la batería puede retrasar tareas)"}")
        appendLine("Notificaciones activadas: ${if (notifications) "sí" else "NO"}")
        append("Próxima alarma armada: ${fmt(armed)}")
    }
}
