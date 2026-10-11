package com.arsys.netatmo.ui.screens.boiler

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arsys.netatmo.domain.model.ThermostatType
import com.arsys.netatmo.ui.theme.OutlineVariant
import com.arsys.netatmo.ui.theme.SurfaceContainer
import com.arsys.netatmo.ui.theme.SurfaceContainerHigh

// ── Design tokens ─────────────────────────────────────────────────────────────

private val BgColor       = Color(0xFF101419)
private val GreenGood     = Color(0xFF62DF7D)
private val RedBad        = Color(0xFFFFB4AB)

// ── Main Screen ───────────────────────────────────────────────────────────────

@Composable
fun BoilerStatusScreen(
    onBack: () -> Unit,
    onDiagnostic: () -> Unit,
    viewModel: BoilerStatusViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val transition = rememberInfiniteTransition(label = "boilerAnims")
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

    val isOpenTherm = state.thermostatType == ThermostatType.OPENTHERM
    val plug = state.modules.firstOrNull { it.type == "NAPlug" }
    val rfStrength = plug?.rfStrength

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            BoilerHeader(
                onClose   = onBack,
                onRefresh = { viewModel.refresh() }
            )

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

                    state.error?.let { err ->
                        ErrorBanner(message = err, onDismiss = { viewModel.dismissError() })
                    }

                    StatusPillBanner(
                        reachable  = plug?.reachable,
                        rfStrength = rfStrength
                    )

                    if (plug != null) {
                        BoilerTelemetryCard(
                            reachable     = plug.reachable,
                            modulationPct = if (isOpenTherm) state.boilerHealth.modulationPct else null,
                            isModulating  = state.boilerHealth.isModulating,
                            rfStrength    = rfStrength,
                            pingScale     = pingScale,
                            pingAlpha     = pingAlpha
                        )
                    }

                    FooterButtons(
                        onBack       = onBack,
                        onDiagnostic = onDiagnostic
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
    onRefresh: () -> Unit
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

        Text(
            "Estado de la caldera",
            color  = MaterialTheme.colorScheme.onSurface,
            style  = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        )

        Surface(
            shape  = CircleShape,
            color  = SurfaceContainerHigh.copy(alpha = 0.60f),
            modifier = Modifier.size(40.dp)
        ) {
            IconButton(onClick = onRefresh, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Actualizar",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
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
    reachable: Boolean?,
    rfStrength: Int?
) {
    val (statusText, statusColor) = when (reachable) {
        true  -> "Módulo de caldera conectado" to GreenGood
        false -> "Módulo de caldera sin conexión" to RedBad
        null  -> "Sin datos del módulo de caldera" to MaterialTheme.colorScheme.onSurfaceVariant
    }
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
                modifier              = Modifier.weight(1f),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    statusText,
                    color = statusColor,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (rfStrength != null) {
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
                        "RF: $rfStrength",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

// ── Boiler Telemetry Card ─────────────────────────────────────────────────────

@Composable
private fun BoilerTelemetryCard(
    reachable: Boolean,
    modulationPct: Int?,
    isModulating: Boolean,
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

            val tiles = buildList<@Composable (Modifier) -> Unit> {
                add { tileModifier ->
                    TelemetryTile(
                        modifier   = tileModifier,
                        icon       = Icons.Default.LocalFireDepartment,
                        iconColor  = secondary,
                        label      = "QUEMADOR",
                        value      = if (isModulating) "Activo" else "Standby",
                        subtitle   = if (isModulating) "Modo modulante" else "En reposo",
                        pingScale  = pingScale,
                        pingAlpha  = pingAlpha,
                        isLive     = isModulating
                    )
                }
                if (modulationPct != null) {
                    add { tileModifier ->
                        TelemetryTile(
                            modifier   = tileModifier,
                            icon       = Icons.Default.Sync,
                            iconColor  = primary,
                            label      = "MODULACIÓN",
                            value      = "$modulationPct%",
                            subtitle   = null,
                            pingScale  = pingScale,
                            pingAlpha  = pingAlpha,
                            isLive     = false
                        )
                    }
                }
                if (rfStrength != null) {
                    add { tileModifier ->
                        TelemetryTile(
                            modifier   = tileModifier,
                            icon       = Icons.Default.Hub,
                            iconColor  = primary,
                            label      = "SEÑAL RF",
                            value      = "$rfStrength",
                            subtitle   = if (reachable) "Módulo alcanzable" else "Módulo inalcanzable",
                            pingScale  = pingScale,
                            pingAlpha  = pingAlpha,
                            isLive     = false
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.chunked(2).forEach { rowTiles ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowTiles.forEach { tile -> tile(Modifier.weight(1f)) }
                        if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
                    }
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
    subtitle: String?,
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
            if (subtitle != null) {
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

        // Secondary – Ver estado detallado
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
                "Ver estado detallado",
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
