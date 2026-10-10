package com.arsys.netatmo.ui.screens.boiler

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val BgColor = Color(0xFF101419)
private val CardColor = Color(0xFF1C2025)
private val BorderColor = Color(0xFF3F4850)
private val TextPrimary = Color(0xFFE0E2EA)
private val TextSecondary = Color(0xFFBFC7D2)
private val WarmColor = Color(0xFFEA580C)
private val BlueAccent = Color(0xFF93CCFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoilerStatusScreen(
    onBack: () -> Unit,
    onDiagnostic: () -> Unit,
    viewModel: BoilerStatusViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estado Caldera", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = BlueAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E13))
            )
        },
        containerColor = BgColor
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BlueAccent)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            state.error?.let {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF93000A)), shape = RoundedCornerShape(12.dp)) {
                    Text(it, modifier = Modifier.padding(16.dp), color = Color(0xFFFFDAD6))
                }
            }

            // Central boiler indicator
            val statusColor = if (state.isBoilerOn) WarmColor else TextSecondary
            val statusText = if (state.isBoilerOn) "Caldera activa" else "En reposo"
            val glowColor = if (state.isBoilerOn) WarmColor.copy(alpha = glowAlpha) else Color.Transparent

            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                // Glow ring
                if (state.isBoilerOn) {
                    Surface(
                        modifier = Modifier.size(160.dp),
                        shape = CircleShape,
                        color = glowColor
                    ) {}
                }
                Surface(
                    modifier = Modifier.size(120.dp),
                    shape = CircleShape,
                    color = if (state.isBoilerOn) WarmColor.copy(alpha = 0.15f) else CardColor,
                    border = BorderStroke(2.dp, statusColor)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }
            }

            Text(statusText, color = statusColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)

            // Stat cards
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Modulación",
                    value = state.modulationPct?.let { "$it%" } ?: "--",
                    icon = Icons.Default.Tune,
                    tint = BlueAccent
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Zonas activas",
                    value = "${state.roomsHeating}/${state.totalRooms}",
                    icon = Icons.Default.GridView,
                    tint = BlueAccent
                )
            }

            state.modulationPct?.let { pct ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardColor),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Nivel de modulación", color = TextSecondary, fontSize = 13.sp)
                        LinearProgressIndicator(
                            progress = { pct / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = WarmColor,
                            trackColor = BorderColor
                        )
                        Text("$pct%", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }

            // Diagnostic button
            FilledTonalButton(
                onClick = onDiagnostic,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF1C2025),
                    contentColor = BlueAccent
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Iniciar diagnóstico hidráulico", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Text(value, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(label, color = TextSecondary, fontSize = 12.sp)
        }
    }
}
