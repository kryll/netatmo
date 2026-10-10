package com.arsys.netatmo.ui.screens.boiler

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arsys.netatmo.ui.theme.OutlineVariant
import com.arsys.netatmo.ui.theme.SurfaceContainer
import com.arsys.netatmo.ui.theme.SurfaceContainerHigh
import com.arsys.netatmo.ui.theme.SurfaceContainerLow

// ── Design tokens ─────────────────────────────────────────────────────────────

private val BgColor       = Color(0xFF101419)
private val GreenGood     = Color(0xFF62DF7D)
private val RedBad        = Color(0xFFFFB4AB)
private val AmberWarn     = Color(0xFFF59E0B)

// ── Main Screen ───────────────────────────────────────────────────────────────

@Composable
fun BoilerStatusScreen(
    onBack: () -> Unit,
    onDiagnostic: () -> Unit,
    viewModel: BoilerStatusViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Shared infinite transition for all animations
    val transition = rememberInfiniteTransition(label = "boilerAnims")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.30f,
        targetValue  = 1.00f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val pingScale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue  = 2.1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pingScale"
    )
    val pingAlpha by transition.animateFloat(
        initialValue = 0.70f,
        targetValue  = 0.00f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pingAlpha"
    )

    val health       = state.boilerHealth
    val pressureBar  = health.pressureBar?.toFloat() ?: 1.35f
    val modulationPct = health.modulationPct
    val impulsionTempC = health.impulsionTempC?.toFloat()
    val isModulating = health.isModulating
    val rfStrength   = state.modules.firstOrNull { it.type == "NAPlug" }?.rfStrength

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Fixed header ─────────────────────────────────────────────────
            BoilerHeader(
                onClose  = onBack,
                onAbort  = { viewModel.dismissError() },
                pulseAlpha = pulseAlpha
            )

            // ── Scrollable content ───────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                if (state.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {

                    // Error banner (conditional)
                    state.error?.let { err ->
                        ErrorBanner(message = err, onDismiss = { viewModel.dismissError() })
                    }

                    // 1. Status pill banner
                    StatusPillBanner(
                        pulseAlpha = pulseAlpha,
                        rfStrength = rfStrength
                    )

                    // 2. Hero confirmation card
                    HeroConfirmationCard(
                        errorCount = 0,
                        pulseAlpha = pulseAlpha
                    )

                    // 3. Hydraulic manometer card
                    HydraulicManometerCard(pressureBar = pressureBar)

                    // 4. Boiler telemetry 2×2 grid
                    BoilerTelemetryCard(
                        modulationPct  = modulationPct,
                        isModulating   = isModulating,
                        impulsionTempC = impulsionTempC,
                        rfStrength     = rfStrength,
                        pingScale      = pingScale,
                        pingAlpha      = pingAlpha
                    )

                    // 5. Security checklist
                    SecurityChecklistCard()

                    // 6. Log / Bitácora
                    BitacoraCard(pressureBar = pressureBar)

                    // 7. Footer action buttons
                    FooterButtons(
                        onBack       = onBack,
                        onDiagnostic = {
                            viewModel.launchDiagnostic()
                            onDiagnostic()
                        }
                    )
                }
            }
        }
    }
}

// ── Fixed Header ──────────────────────────────────────────────────────────────

@Composable
private fun BoilerHeader(
    onClose: () -> Unit,
    onAbort: () -> Unit,
    pulseAlpha: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(BgColor)
            .padding(horizontal = 16.dp),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // X – close button
        Surface(
            shape  = CircleShape,
            color  = SurfaceContainerHigh.copy(alpha = 0.60f),
            modifier = Modifier.size(40.dp)
        ) {
            IconButton(onClick = onClose, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Center – device subtitle + screen title
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(GreenGood.copy(alpha = pulseAlpha))
                )
                Text(
                    "Radiador Suite Principal",
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "Purga De Aire En Tiempo Real",
                color  = MaterialTheme.colorScheme.onSurface,
                style  = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Right – Abortar + avatar
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.20f)
            ) {
                TextButton(
                    onClick         = onAbort,
                    modifier        = Modifier.height(36.dp),
                    contentPadding  = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(
                        "Abortar",
                        color      = MaterialTheme.colorScheme.error,
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "JA",
                    color      = MaterialTheme.colorScheme.onPrimary,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Error Banner ──────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.50f)
        ),
        shape  = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.40f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Text(
                message,
                color    = MaterialTheme.colorScheme.onSurface,
                style    = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ── Status Pill Banner ────────────────────────────────────────────────────────

@Composable
private fun StatusPillBanner(
    pulseAlpha: Float,
    rfStrength: Int?
) {
    Surface(
        shape  = RoundedCornerShape(50),
        color  = SurfaceContainerHigh.copy(alpha = 0.80f),
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(GreenGood.copy(alpha = pulseAlpha))
                )
                Text(
                    "SISTEMA RESTABLECIDO · Circuito OK",
                    color = GreenGood,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.CellTower,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "868 MHz · ${if (rfStrength != null) "100%" else "100%"}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

// ── Hero Confirmation Card ────────────────────────────────────────────────────

@Composable
private fun HeroConfirmationCard(
    errorCount: Int,
    pulseAlpha: Float
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
        shape  = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon with pulsing glow rings
            Box(contentAlignment = Alignment.Center) {
                // Outer diffused glow (pulsing)
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(GreenGood.copy(alpha = 0.07f * pulseAlpha))
                )
                // Mid glow ring
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(GreenGood.copy(alpha = 0.12f))
                )
                // Filled check_circle icon
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint     = GreenGood,
                    modifier = Modifier.size(44.dp)
                )
            }

            // Success badge (tertiary/15 pill with Verified icon)
            Surface(
                shape = RoundedCornerShape(50),
                color = GreenGood.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = null,
                        tint     = GreenGood,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "BLOQUEO PREVENTIVO RESUELTO ($errorCount ERRORES)",
                        color = GreenGood,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Headline
            Text(
                "Circuito Re-presurizado con Éxito",
                color     = MaterialTheme.colorScheme.onSurface,
                style     = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            // Body description
            Text(
                "El sistema hidráulico ha sido restaurado correctamente. La presión del circuito se mantiene dentro de los parámetros óptimos y la caldera ha reanudado su operación normal.",
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Hydraulic Manometer Card ──────────────────────────────────────────────────

@Composable
private fun HydraulicManometerCard(pressureBar: Float) {
    val pressureText = "%.2f bar".format(pressureBar)
    val isOptimal    = pressureBar in 1.20f..1.50f
    val pressureLabel = when {
        pressureBar < 1.0f  -> "PRESIÓN BAJA"
        isOptimal            -> "PRESIÓN ÓPTIMA"
        pressureBar > 2.0f  -> "PRESIÓN ALTA"
        else                 -> "PRESIÓN ACEPTABLE"
    }
    val pressureLabelColor = when {
        pressureBar < 1.0f  -> RedBad
        isOptimal            -> GreenGood
        pressureBar > 2.0f  -> AmberWarn
        else                 -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        shape  = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section header
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionHeader(
                    icon         = Icons.Default.Speed,
                    iconColor    = MaterialTheme.colorScheme.primary,
                    sectionLabel = "MANÓMETRO",
                    title        = "Netatmo"
                )
                // ESTABLE pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = GreenGood.copy(alpha = 0.15f)
                ) {
                    Text(
                        "ESTABLE",
                        color    = GreenGood,
                        style    = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Arc gauge + digital readout
            Column(
                modifier            = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PressureArcGauge(
                    pressureBar = pressureBar,
                    modifier    = Modifier
                        .width(220.dp)
                        .height(118.dp)
                )
                Text(
                    pressureText,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    pressureLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = pressureLabelColor
                )
                Spacer(Modifier.height(4.dp))
                // Recovery delta badge
                Surface(
                    shape = RoundedCornerShape(50),
                    color = GreenGood.copy(alpha = 0.12f)
                ) {
                    Text(
                        "+0.50 bar recuperados",
                        color    = GreenGood,
                        style    = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            // Segmented tolerance bar
            Column(
                modifier            = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.40f)
                            .fillMaxHeight()
                            .background(RedBad.copy(alpha = 0.35f))
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.32f)
                            .fillMaxHeight()
                            .background(GreenGood)
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.28f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "0.0\nBaja",
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        style     = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Start
                    )
                    Text(
                        "1.2\nÓptimo",
                        color     = GreenGood,
                        style     = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "1.5",
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        style     = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "2.5\nMáx",
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        style     = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

// ── Pressure Arc Gauge (Canvas) ───────────────────────────────────────────────

@Composable
private fun PressureArcGauge(
    pressureBar: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val sw     = 11.dp.toPx()
        val cx     = size.width / 2f
        val r      = cx - sw
        val cy     = sw / 2f

        val left   = cx - r
        val top    = cy - r
        val arcSz  = Size(r * 2f, r * 2f)
        val origin = Offset(left, top)

        // Background track (full 180° arc)
        drawArc(
            color      = SurfaceContainerHigh,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter  = false,
            style      = Stroke(width = sw, cap = StrokeCap.Round),
            topLeft    = origin,
            size       = arcSz
        )

        // Error / caution zone: first 40% = 72° (0 – ~1.0 bar)
        drawArc(
            color      = RedBad.copy(alpha = 0.30f),
            startAngle = 180f,
            sweepAngle = 72f,
            useCenter  = false,
            style      = Stroke(width = sw, cap = StrokeCap.Butt),
            topLeft    = origin,
            size       = arcSz
        )

        // Optimal zone highlight: next 32% = 57.6° (1.0 – 1.5 bar)
        drawArc(
            color      = GreenGood.copy(alpha = 0.20f),
            startAngle = 252f,            // 180 + 72
            sweepAngle = 57.6f,
            useCenter  = false,
            style      = Stroke(width = sw, cap = StrokeCap.Butt),
            topLeft    = origin,
            size       = arcSz
        )

        // Active fill proportional to pressure (max 2.5 bar = 180°)
        val fraction    = (pressureBar / 2.5f).coerceIn(0f, 1f)
        val activeSweep = 180f * fraction
        if (activeSweep > 0.5f) {
            drawArc(
                color      = GreenGood,
                startAngle = 180f,
                sweepAngle = activeSweep,
                useCenter  = false,
                style      = Stroke(width = sw, cap = StrokeCap.Round),
                topLeft    = origin,
                size       = arcSz
            )
        }
    }
}

// ── Boiler Telemetry Card (2×2 grid) ─────────────────────────────────────────

@Composable
private fun BoilerTelemetryCard(
    modulationPct: Int?,
    isModulating: Boolean,
    impulsionTempC: Float?,
    rfStrength: Int?,
    pingScale: Float,
    pingAlpha: Float
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        shape  = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader(
                icon         = Icons.Default.Thermostat,
                iconColor    = MaterialTheme.colorScheme.primary,
                sectionLabel = "TELEMETRÍA",
                title        = "Estado Caldera"
            )

            val secondary = MaterialTheme.colorScheme.secondary
            val primary   = MaterialTheme.colorScheme.primary

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Row 1
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TelemetryTile(
                        modifier   = Modifier.weight(1f),
                        icon       = Icons.Default.LocalFireDepartment,
                        iconColor  = secondary,
                        label      = "QUEMADOR",
                        value      = if (isModulating) "Activo" else "Standby",
                        subtitle   = if (isModulating) "Modo modulante" else "En reposo",
                        pingScale  = pingScale,
                        pingAlpha  = pingAlpha,
                        isLive     = isModulating
                    )
                    TelemetryTile(
                        modifier   = Modifier.weight(1f),
                        icon       = Icons.Default.Sync,
                        iconColor  = primary,
                        label      = "BOMBA CIRC.",
                        value      = "${modulationPct ?: 45}%",
                        subtitle   = "Activa · Régimen continuo",
                        pingScale  = pingScale,
                        pingAlpha  = pingAlpha,
                        isLive     = true
                    )
                }
                // Row 2
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TelemetryTile(
                        modifier   = Modifier.weight(1f),
                        icon       = Icons.Default.Thermostat,
                        iconColor  = secondary,
                        label      = "TEMP. IMP.",
                        value      = impulsionTempC
                            ?.let { "%.1f°C".format(it) } ?: "48.5°C",
                        subtitle   = "En ascenso gradual",
                        pingScale  = pingScale,
                        pingAlpha  = pingAlpha,
                        isLive     = isModulating
                    )
                    TelemetryTile(
                        modifier   = Modifier.weight(1f),
                        icon       = Icons.Default.Hub,
                        iconColor  = GreenGood,
                        label      = "RELÉ RF",
                        value      = "${rfStrength ?: 12} ms",
                        subtitle   = "Sincronizado · Canal prioritario",
                        pingScale  = pingScale,
                        pingAlpha  = pingAlpha,
                        isLive     = true
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    subtitle: String,
    pingScale: Float,
    pingAlpha: Float,
    isLive: Boolean
) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(12.dp),
        color    = SurfaceContainerHigh
    ) {
        Column(
            modifier            = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Icon + live ping indicator
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint     = iconColor,
                    modifier = Modifier.size(20.dp)
                )
                if (isLive) {
                    PingDot(color = iconColor, scale = pingScale, alpha = pingAlpha)
                }
            }
            // Uppercase label (labelSmall)
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
            // Main value (titleMedium)
            Text(
                value,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
            // Subtitle line (bodySmall)
            Text(
                subtitle,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                style    = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Ping dot (animate-ping equivalent) ───────────────────────────────────────

@Composable
private fun PingDot(color: Color, scale: Float, alpha: Float) {
    Box(
        modifier         = Modifier.size(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp * scale)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha * 0.50f))
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

// ── Security Checklist Card ───────────────────────────────────────────────────

private data class CheckItem(val title: String, val description: String)

private val securityItems = listOf(
    CheckItem("Válvula de seguridad",    "Tarado a 3 bar, sellado sin fugas detectadas"),
    CheckItem("Vaso de expansión",       "Presión cargada 0.8 bar, membrana en buen estado"),
    CheckItem("Purgador automático",     "Aire eliminado con éxito, circuito sellado"),
    CheckItem("Protección anti-bloqueo", "Bomba validada en ciclo post-parada")
)

@Composable
private fun SecurityChecklistCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        shape  = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row with badge
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionHeader(
                    icon         = Icons.Default.FactCheck,
                    iconColor    = GreenGood,
                    sectionLabel = "SEGURIDAD",
                    title        = "Protocolo de Seguridad"
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = GreenGood.copy(alpha = 0.15f)
                ) {
                    Text(
                        "4 / 4 OK",
                        color    = GreenGood,
                        style    = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Checklist rows
            securityItems.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceContainerHigh.copy(alpha = 0.60f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(GreenGood.copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint     = GreenGood,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                item.title,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                item.description,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Bitácora / Log Card ───────────────────────────────────────────────────────

@Composable
private fun BitacoraCard(pressureBar: Float) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
        shape  = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Icon box (primary/15 rounded-xl)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.EventNote,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Bitácora Netatmo",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        "00:04:22",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    "Caldera reanudada — presión ${"%.2f".format(pressureBar)} bar estabilizada tras purga de aire",
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    style    = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
            }
        }
    }
}

// ── Footer Buttons ────────────────────────────────────────────────────────────

@Composable
private fun FooterButtons(
    onBack: () -> Unit,
    onDiagnostic: () -> Unit
) {
    Column(
        modifier            = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Primary – Volver al Panel Principal
        Button(
            onClick  = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                Icons.Default.Home,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Volver al Panel Principal",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Secondary – Ver Historial de Mantenimiento
        Button(
            onClick  = onDiagnostic,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = SurfaceContainerHigh,
                contentColor   = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Ver Historial de Mantenimiento",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

// ── Section Header (shared pattern) ──────────────────────────────────────────

@Composable
private fun SectionHeader(
    icon: ImageVector,
    iconColor: Color,
    sectionLabel: String,
    title: String
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint     = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Column {
            Text(
                sectionLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
