package com.arsys.netatmo.ui.screens.boiler

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arsys.netatmo.ui.theme.ComfortColor
import com.arsys.netatmo.ui.theme.OutlineVariant
import com.arsys.netatmo.ui.theme.SurfaceContainer
import com.arsys.netatmo.ui.theme.SurfaceContainerLowest
import com.arsys.netatmo.ui.theme.WarmColor

/**
 * Snapshot of the boiler module status built only from data exposed by the Netatmo API
 * (module reachability, RF signal and modulation). It does not run any hardware test.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HydraulicDiagnosticScreen(
    onBack: () -> Unit,
    viewModel: BoilerStatusViewModel = hiltViewModel()
) {
    val vmState by viewModel.uiState.collectAsStateWithLifecycle()
    val report = remember(vmState.modules, vmState.boilerHealth) { buildStatusReport(vmState) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Estado de la caldera",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Actualizar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceContainerLowest
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            when {
                report != null -> DiagnosticResultPhase(result = report, onBack = onBack)
                vmState.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.padding(top = 48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                else -> Text(
                    vmState.error ?: "Sin datos del módulo de caldera disponibles",
                    modifier = Modifier.padding(top = 48.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── Phase 2 — Result ─────────────────────────────────────────────────────────

@Composable
private fun DiagnosticResultPhase(
    result: DiagnosticResult,
    onBack: () -> Unit
) {
    val (gradeColor, gradeBgAlpha) = when (result.grade) {
        "A+" -> ComfortColor to 0.12f
        "A"  -> ComfortColor to 0.10f
        "B"  -> MaterialTheme.colorScheme.primary to 0.10f
        "C"  -> WarmColor to 0.12f
        else -> MaterialTheme.colorScheme.error to 0.15f
    }

    // Grade hero card
    Card(
        colors = CardDefaults.cardColors(containerColor = gradeColor.copy(alpha = gradeBgAlpha)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, gradeColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = gradeColor,
                modifier = Modifier.size(52.dp)
            )
            Text(
                "Estado actual",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            // Grade badge
            Surface(
                shape = RoundedCornerShape(50),
                color = gradeColor.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        result.grade,
                        color = gradeColor,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Column {
                        Text(
                            "Puntuación",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Text(
                            "${result.gradeScore} / 100",
                            color = gradeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }
            val gradeLabel = when (result.grade) {
                "A+" -> "Excelente — sin incidencias en los datos disponibles"
                "A"  -> "Muy bueno — sin incidencias relevantes"
                "B"  -> "Bueno — revisa los avisos si los hay"
                "C"  -> "Regular — revisa las recomendaciones"
                else -> "Crítico — requiere atención"
            }
            Text(
                gradeLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    // Component breakdown
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                "Detalle por componente",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = OutlineVariant, thickness = 0.5.dp)
            result.checks.forEach { check ->
                DiagnosticCheckRow(check = check)
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
            }
        }
    }

    // Recommendations (only shown when there are issues)
    if (result.recommendations.isNotEmpty()) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Recomendaciones",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                result.recommendations.forEach { rec ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            rec,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    // Close / back button
    OutlinedButton(
        onClick = onBack,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Icon(
            Icons.Default.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text("Volver al estado de caldera", fontWeight = FontWeight.Medium)
    }

    Text(
        "Basado solo en los datos que expone la API de Netatmo (conexión, señal RF y modulación). No sustituye una revisión técnica.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(8.dp))
}

@Composable
private fun DiagnosticCheckRow(check: DiagnosticCheckResult) {
    val (iconTint, statusText, statusIcon) = when (check.status) {
        CheckStatus.OK       -> Triple(ComfortColor, "OK",      Icons.Default.CheckCircle as ImageVector)
        CheckStatus.WARNING  -> Triple(WarmColor,    "Aviso",   Icons.Default.Warning as ImageVector)
        CheckStatus.CRITICAL -> Triple(MaterialTheme.colorScheme.error, "Crítico", Icons.Default.Warning as ImageVector)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                statusIcon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    check.label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    check.detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = iconTint.copy(alpha = 0.12f)
        ) {
            Text(
                statusText,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = iconTint,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
