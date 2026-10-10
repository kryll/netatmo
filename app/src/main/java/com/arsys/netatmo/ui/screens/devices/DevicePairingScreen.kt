package com.arsys.netatmo.ui.screens.devices

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val BgColor = Color(0xFF101419)
private val CardColor = Color(0xFF1C2025)
private val BorderColor = Color(0xFF3F4850)
private val TextPrimary = Color(0xFFE0E2EA)
private val TextSecondary = Color(0xFFBFC7D2)
private val BlueAccent = Color(0xFF93CCFF)
private val GreenOk = Color(0xFF62DF7D)

private val FAKE_DEVICES = listOf(
    "Netatmo NRV Válvula — AA:BB:CC:DD:EE:01",
    "Netatmo NTH01 Termostato — AA:BB:CC:DD:EE:02"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePairingScreen(onBack: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var foundDevices by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedDevice by remember { mutableStateOf<String?>(null) }
    var deviceName by remember { mutableStateOf("") }
    var isConfigured by remember { mutableStateOf(false) }

    LaunchedEffect(step) {
        if (step == 1) {
            delay(3000)
            foundDevices = FAKE_DEVICES
        }
    }

    val infinite = rememberInfiniteTransition(label = "scan")
    val scanScale by infinite.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scan"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emparejar Dispositivo", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E13))
            )
        },
        containerColor = BgColor
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Step progress
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Preparación", "Búsqueda", "Configuración").forEachIndexed { i, label ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = CircleShape,
                            color = if (step >= i) BlueAccent else BorderColor
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${i + 1}", color = if (step >= i) Color(0xFF001D31) else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(label, color = if (step >= i) BlueAccent else TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            when (step) {
                0 -> {
                    // Preparation step
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardColor),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Default.DevicesOther, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(48.dp))
                            Text("Preparación", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Asegúrate de que el dispositivo Netatmo está encendido y en modo emparejamiento (LED parpadeando).", color = TextSecondary, fontSize = 14.sp)
                            Text("• Mantén el botón de configuración 3 segundos\n• El LED debe parpadear en azul\n• Mantén el dispositivo cerca del teléfono", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                    Button(
                        onClick = { step = 1 },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Color(0xFF001D31)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Buscar dispositivos", fontWeight = FontWeight.SemiBold)
                    }
                }

                1 -> {
                    // Search step
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.size(120.dp).scale(scanScale),
                            shape = CircleShape,
                            color = BlueAccent.copy(alpha = 0.1f),
                            border = BorderStroke(2.dp, BlueAccent.copy(alpha = 0.4f))
                        ) {}
                        Surface(modifier = Modifier.size(80.dp), shape = CircleShape, color = BlueAccent.copy(alpha = 0.15f)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(40.dp))
                            }
                        }
                    }

                    if (foundDevices.isEmpty()) {
                        Text("Buscando dispositivos...", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        Text("${foundDevices.size} dispositivo(s) encontrado(s)", color = GreenOk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        foundDevices.forEach { device ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { selectedDevice = device; step = 2; deviceName = device.substringBefore("—").trim() },
                                colors = CardDefaults.cardColors(containerColor = CardColor),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (selectedDevice == device) BlueAccent else BorderColor)
                            ) {
                                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Router, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(24.dp))
                                    Text(device, color = TextPrimary, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // Configuration step
                    if (!isConfigured) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardColor),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("Configurar dispositivo", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(selectedDevice ?: "", color = TextSecondary, fontSize = 12.sp)
                                OutlinedTextField(
                                    value = deviceName,
                                    onValueChange = { deviceName = it },
                                    label = { Text("Nombre del dispositivo", color = TextSecondary) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BlueAccent, unfocusedBorderColor = BorderColor,
                                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                                    )
                                )
                            }
                        }
                        Button(
                            onClick = { isConfigured = true },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            enabled = deviceName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Color(0xFF001D31)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Emparejar y guardar", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenOk, modifier = Modifier.size(72.dp))
                                Text("¡Dispositivo emparejado!", color = GreenOk, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text("\"$deviceName\" se ha añadido correctamente al sistema.", color = TextSecondary, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
