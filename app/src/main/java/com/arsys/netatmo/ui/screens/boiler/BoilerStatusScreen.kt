package com.arsys.netatmo.ui.screens.boiler

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

private val ScreenBackground = Color(0xFF101419)
private val WarmColor = Color(0xFFEA580C)
private val PrimaryColor = Color(0xFF93CCFF)
private val SurfaceCard = Color(0xFF1C2028)
private val OnSurface = Color(0xFFE0E2EA)
private val OnSurfaceVariant = Color(0xFFBFC7D2)
private val OutlineVariant = Color(0xFF3F4850)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoilerStatusScreen(
    navController: NavController,
    viewModel: BoilerStatusViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Pulsing glow animation for active boiler
    val infiniteTransition = rememberInfiniteTransition(label = "boiler_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Estado Caldera",
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = OnSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { navController.navigate("hydraulic_diagnostic") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Diagnóstico",
                            tint = PrimaryColor
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Actualizar",
                            tint = OnSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ScreenBackground
                )
            )
        },
        containerColor = ScreenBackground
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryColor)
            }
            return@Scaffold
        }

        if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFB4AB),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.error ?: "Error desconocido",
                        color = Color(0xFFFFB4AB),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.refresh() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                    ) {
                        Text("Reintentar", color = Color(0xFF003351))
                    }
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Large boiler status indicator
            BoilerStatusIndicator(
                isBoilerOn = uiState.isBoilerOn,
                glowAlpha = if (uiState.isBoilerOn) glowAlpha else 0f,
                modulationPct = uiState.modulationPct
            )

            // Status text
            Text(
                text = if (uiState.isBoilerOn) "Caldera activa" else "En reposo",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (uiState.isBoilerOn) WarmColor else OnSurfaceVariant
            )

            if (uiState.modulationPct != null) {
                Text(
                    text = "Modulación: ${uiState.modulationPct}%",
                    fontSize = 14.sp,
                    color = OnSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Modulation card
            if (uiState.modulationPct != null) {
                MetricCard(
                    title = "Modulación",
                    icon = Icons.Default.Tune,
                    iconTint = WarmColor
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0%", fontSize = 12.sp, color = OnSurfaceVariant)
                            Text(
                                "${uiState.modulationPct}%",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = WarmColor
                            )
                            Text("100%", fontSize = 12.sp, color = OnSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (uiState.modulationPct ?: 0) / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = WarmColor,
                            trackColor = OutlineVariant
                        )
                    }
                }
            }

            // Heating zones card
            MetricCard(
                title = "Zonas calentando",
                icon = Icons.Default.Home,
                iconTint = PrimaryColor
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${uiState.roomCount}",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.heatingActive) PrimaryColor else OnSurfaceVariant
                    )
                    Text(
                        text = if (uiState.roomCount == 1) "zona activa" else "zonas activas",
                        fontSize = 14.sp,
                        color = OnSurfaceVariant
                    )
                }
            }

            // System pressure card
            if (uiState.pressureBar != null) {
                MetricCard(
                    title = "Presión sistema",
                    icon = Icons.Default.Speed,
                    iconTint = Color(0xFF62DF7D)
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = String.format("%.2f", uiState.pressureBar),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF62DF7D)
                        )
                        Text(
                            text = "bar",
                            fontSize = 16.sp,
                            color = OnSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    val pressureStatus = when {
                        uiState.pressureBar < 1.0 -> "Baja — revisar sistema"
                        uiState.pressureBar > 2.5 -> "Alta — atención"
                        else -> "Normal"
                    }
                    val pressureColor = when {
                        uiState.pressureBar < 1.0 || uiState.pressureBar > 2.5 -> Color(0xFFFFB4AB)
                        else -> Color(0xFF62DF7D)
                    }
                    Text(text = pressureStatus, fontSize = 13.sp, color = pressureColor)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Navigate to hydraulic diagnostic
            Button(
                onClick = { navController.navigate("hydraulic_diagnostic") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1C2028)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = PrimaryColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Diagnóstico hidráulico",
                    color = PrimaryColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BoilerStatusIndicator(
    isBoilerOn: Boolean,
    glowAlpha: Float,
    modulationPct: Int?
) {
    val glowColor = WarmColor
    val circleColor = if (isBoilerOn)
        Brush.radialGradient(listOf(Color(0xFFFF7A2B), Color(0xFFEA580C)))
    else
        Brush.radialGradient(listOf(Color(0xFF2A2F38), Color(0xFF1C2028)))

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(160.dp)
            .drawBehind {
                if (isBoilerOn) {
                    drawCircle(
                        color = glowColor.copy(alpha = glowAlpha * 0.5f),
                        radius = size.minDimension / 2f + 28.dp.toPx()
                    )
                    drawCircle(
                        color = glowColor.copy(alpha = glowAlpha * 0.25f),
                        radius = size.minDimension / 2f + 52.dp.toPx()
                    )
                }
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(
                    if (isBoilerOn)
                        Brush.radialGradient(listOf(Color(0xFFFF7A2B), Color(0xFFB84500)))
                    else
                        Brush.radialGradient(listOf(Color(0xFF2A2F38), Color(0xFF1C2028)))
                )
        ) {
            Icon(
                imageVector = Icons.Default.LocalFireDepartment,
                contentDescription = "Estado caldera",
                tint = if (isBoilerOn) Color.White else Color(0xFF4A5568),
                modifier = Modifier.size(72.dp)
            )
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant
                )
            }
            content()
        }
    }
}
