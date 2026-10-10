package com.arsys.netatmo.ui.screens.maintenance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arsys.netatmo.data.local.entities.MaintenanceRecordEntity
import com.arsys.netatmo.domain.model.MaintenanceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Design tokens aligned with Stitch Netatmo Smart Atmosphere ---
private val BgColor = Color(0xFF101419)
private val SurfaceLowest = Color(0xFF0A0E13)
private val SurfaceContLow = Color(0xFF181C21)
private val SurfaceCont = Color(0xFF1C2025)
private val SurfaceContHigh = Color(0xFF262A30)
private val SurfaceContHighest = Color(0xFF31353B)
private val OutlineVar = Color(0xFF3F4850)
private val TextPrimary = Color(0xFFE0E2EA)
private val TextSecondary = Color(0xFFBFC7D2)
private val BluePrimary = Color(0xFF93CCFF)
private val OrangeSecondary = Color(0xFFFFB599)
private val GreenTertiary = Color(0xFF62DF7D)
private val ErrorColor = Color(0xFFFFB4AB)

private val dateShort = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES"))
private val dateRich = SimpleDateFormat("d MMM yyyy · HH:mm", Locale("es", "ES"))

private fun MaintenanceType.chipColor() = when (this) {
    MaintenanceType.ANNUAL_REVISION -> GreenTertiary
    MaintenanceType.PURGE -> BluePrimary
    MaintenanceType.PRESSURE_CHECK -> OrangeSecondary
    MaintenanceType.VALVE_CALIBRATION -> Color(0xFFBFC7D2)
    MaintenanceType.OTHER -> Color(0xFF89929B)
}

private fun MaintenanceType.icon() = when (this) {
    MaintenanceType.ANNUAL_REVISION -> Icons.Outlined.CheckCircle
    MaintenanceType.PURGE -> Icons.Outlined.Water
    MaintenanceType.PRESSURE_CHECK -> Icons.Outlined.Speed
    MaintenanceType.VALVE_CALIBRATION -> Icons.Outlined.Tune
    MaintenanceType.OTHER -> Icons.Outlined.Build
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceHistoryScreen(
    onBack: () -> Unit,
    onCertificate: (Long) -> Unit,
    viewModel: MaintenanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "Historial De Mantenimiento",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filtrar", tint = TextSecondary)
                        }
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.Download, contentDescription = "Descargar reporte", tint = TextSecondary)
                        }
                        // Blue circle user avatar
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(BluePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Usuario",
                                tint = Color(0xFF001D31),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SurfaceLowest.copy(alpha = 0.80f)
                    )
                )
                // Breadcrumb sub-header
                BreadcrumbSubHeader()
            }
        },
        containerColor = BgColor
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Section 1: Equipment & Warranty Banner
            item {
                EquipmentSummaryBanner(
                    totalCount = uiState.totalCount,
                    certCount = uiState.certCount,
                    annualCount = uiState.annualCount
                )
            }

            // Section 2: Filter Chips
            item {
                FilterChipsRow(
                    activeFilter = uiState.activeFilter,
                    totalCount = uiState.totalCount,
                    certCount = uiState.certCount,
                    purgeCount = uiState.purgeCount,
                    annualCount = uiState.annualCount,
                    onFilterSelected = viewModel::setFilter
                )
            }

            item { Spacer(Modifier.height(8.dp)) }

            // Section 3: Timeline with month labels
            val displayed = uiState.filteredRecords
            if (displayed.isEmpty()) {
                item {
                    EmptyMaintenanceState(hasRecords = uiState.totalCount > 0)
                }
            } else {
                // Group by month/period
                val groups = displayed.groupByMonth()
                groups.forEachIndexed { groupIndex, (period, records) ->
                    item(key = "header_$period") {
                        TimelineSectionHeader(period = period)
                    }
                    itemsIndexed(records, key = { _, r -> r.id }) { idx, record ->
                        val isFirst = groupIndex == 0 && idx == 0
                        val isLast = groupIndex == groups.lastIndex && idx == records.lastIndex
                        MaintenanceRecordCard(
                            record = record,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            onCertificate = { onCertificate(record.id) },
                            onDelete = { viewModel.deleteRecord(record) },
                            isMostRecentCertified = isFirst,
                            isOldest = isLast
                        )
                    }
                }

                // Section 4: Bottom CTAs
                item {
                    BottomCtaSection()
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Breadcrumb Sub-Header
// ---------------------------------------------------------------------------
@Composable
private fun BreadcrumbSubHeader() {
    val infiniteTransition = rememberInfiniteTransition(label = "sync_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceLowest.copy(alpha = 0.80f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Ajustes > Historial de Mantenimiento",
            color = TextSecondary,
            fontSize = 12.sp
        )
        // SYNC ACTIVA pill
        Surface(
            color = SurfaceContHigh,
            shape = RoundedCornerShape(999.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(GreenTertiary.copy(alpha = alpha))
                )
                Text(
                    "SYNC ACTIVA",
                    color = GreenTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Equipment & Warranty Summary Banner
// ---------------------------------------------------------------------------
@Composable
private fun EquipmentSummaryBanner(totalCount: Int, certCount: Int, annualCount: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        color = SurfaceContLow,
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Ambient glow top-right
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 16.dp, y = (-16).dp)
                    .blur(24.dp)
                    .clip(CircleShape)
                    .background(BluePrimary.copy(alpha = 0.10f))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Device header row
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = SurfaceContHigh,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Thermostat,
                                contentDescription = null,
                                tint = BluePrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Caldera Saunier Duval IsoFast V3",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            "Circuito Hidráulico Primario · Modulante",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    // Status chip: 1.35 bar
                    Surface(
                        color = SurfaceContHigh,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = GreenTertiary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text("1.35 bar", color = GreenTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Warranty pill
                Surface(
                    color = SurfaceContHigh.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = GreenTertiary, modifier = Modifier.size(16.dp))
                        Text(
                            "Garantía Oficial Activa hasta Octubre 2029",
                            color = GreenTertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Quick stats — no dividers
                Surface(
                    color = SurfaceCont.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatCell(value = totalCount.toString(), label = "Intervenciones\nregistradas", color = BluePrimary)
                        StatCell(value = certCount.toString(), label = "Certificados PDF\noficiales", color = OrangeSecondary)
                        StatCell(value = "Oct 2025", label = "Revisión RITE", color = GreenTertiary)
                    }
                }

                // eIDAS link row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(14.dp))
                        Text("Libro Digital del Edificio (Sincronizado eIDAS)", color = TextSecondary, fontSize = 11.sp)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Consultar", color = BluePrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = TextSecondary, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 2)
    }
}

// ---------------------------------------------------------------------------
//  Filter Chips Row
// ---------------------------------------------------------------------------
@Composable
private fun FilterChipsRow(
    activeFilter: MaintenanceFilter,
    totalCount: Int,
    certCount: Int,
    purgeCount: Int,
    annualCount: Int,
    onFilterSelected: (MaintenanceFilter) -> Unit
) {
    val filters = listOf(
        Triple(MaintenanceFilter.ALL, Icons.Default.ClearAll, "Todos ($totalCount)"),
        Triple(MaintenanceFilter.CERTIFICATE, Icons.Outlined.VerifiedUser, "Certificados Oficiales ($certCount)"),
        Triple(MaintenanceFilter.PURGE_PRESSURE, Icons.Outlined.Tune, "Purgas y Presión ($purgeCount)"),
        Triple(MaintenanceFilter.ANNUAL, Icons.Outlined.Engineering, "Revisiones SAT ($annualCount)")
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { (filter, icon, label) ->
            val selected = activeFilter == filter
            Surface(
                onClick = { onFilterSelected(filter) },
                color = if (selected) BluePrimary else SurfaceCont,
                shape = RoundedCornerShape(999.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (selected) Color(0xFF001D31) else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        label,
                        color = if (selected) Color(0xFF001D31) else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Timeline Section Header
// ---------------------------------------------------------------------------
@Composable
private fun TimelineSectionHeader(period: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            period.uppercase(),
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )
        Text(
            "ORDEN CRONOLÓGICO",
            color = TextSecondary.copy(alpha = 0.6f),
            fontSize = 10.sp,
            letterSpacing = 0.6.sp
        )
    }
}

// ---------------------------------------------------------------------------
//  Empty State
// ---------------------------------------------------------------------------
@Composable
private fun EmptyMaintenanceState(hasRecords: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = SurfaceContHigh,
                shape = CircleShape,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Build,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Text(
                if (hasRecords) "Sin registros para este filtro" else "Sin registros de mantenimiento",
                color = TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ---------------------------------------------------------------------------
//  Maintenance Record Card
// ---------------------------------------------------------------------------
@Composable
private fun MaintenanceRecordCard(
    record: MaintenanceRecordEntity,
    modifier: Modifier = Modifier,
    onCertificate: () -> Unit,
    onDelete: () -> Unit,
    isMostRecentCertified: Boolean = false,
    isOldest: Boolean = false
) {
    val type = remember(record.type) {
        try { MaintenanceType.valueOf(record.type) } catch (e: Exception) { MaintenanceType.OTHER }
    }
    val chipColor = type.chipColor()
    val typeIcon = type.icon()
    val isPurge = type == MaintenanceType.PURGE || type == MaintenanceType.PRESSURE_CHECK
    val hasCertificate = record.certificateRef.isNotBlank()

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isOldest) Modifier.then(Modifier) else Modifier),
            color = SurfaceContLow,
            shape = RoundedCornerShape(16.dp)
        ) {
            Box {
                // Ambient glow for most-recent certified card
                if (isMostRecentCertified) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 8.dp, y = (-8).dp)
                            .blur(16.dp)
                            .clip(CircleShape)
                            .background(GreenTertiary.copy(alpha = 0.10f))
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .then(if (isOldest) Modifier.then(Modifier) else Modifier),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Top row: timestamp (left) + category badge (right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    dateRich.format(Date(record.date)),
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Surface(
                                color = chipColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(typeIcon, contentDescription = null, tint = chipColor, modifier = Modifier.size(13.dp))
                                    Text(type.displayName, color = chipColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // Title (description)
                        Text(
                            record.description,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 20.sp
                        )

                        // Expediente subtitle if cert ref present
                        if (record.certificateRef.isNotBlank()) {
                            Text(
                                "Expediente #${record.certificateRef} · Sello TSA RFC 3161",
                                color = BluePrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Technician row
                        if (record.technicianName.isNotBlank()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                                Text(record.technicianName, color = TextSecondary, fontSize = 12.sp)
                            }
                        }

                        // Telemetry chips for certified/SAT cards
                        if (isMostRecentCertified && hasCertificate) {
                            TelemetryChipStrip()
                        }

                        // Delta metric box for purge-type cards
                        if (isPurge) {
                            DeltaMetricBox()
                        }
                    }

                    // Bleed footer for certified card
                    if (hasCertificate) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    SurfaceCont.copy(alpha = 0.50f),
                                    RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onCertificate,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BluePrimary,
                                        contentColor = Color(0xFF001D31)
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Ver Certificado PDF (244 KB)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    TextButton(
                                        onClick = { },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Acuse de Recibo", color = TextSecondary, fontSize = 12.sp)
                                    }
                                    TextButton(
                                        onClick = { },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Detalles", color = TextSecondary, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Reduced opacity overlay for oldest card
        if (isOldest) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BgColor.copy(alpha = 0.15f))
            )
        }
    }
}

// ---------------------------------------------------------------------------
//  Telemetry Chip Strip
// ---------------------------------------------------------------------------
@Composable
private fun TelemetryChipStrip() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TelemetryChip(label = "Cavitación 0%")
        TelemetryChip(label = "ΔT 18°C")
        TelemetryChip(label = "Presión 1.35 bar")
    }
}

@Composable
private fun TelemetryChip(label: String) {
    Surface(
        color = SurfaceContHigh,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ---------------------------------------------------------------------------
//  Delta Metric Box (purge cards)
// ---------------------------------------------------------------------------
@Composable
private fun DeltaMetricBox() {
    Surface(
        color = SurfaceContHigh,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("0.85 bar", color = TextSecondary, fontSize = 12.sp)
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = GreenTertiary, modifier = Modifier.size(14.dp))
            Text("1.35 bar", color = GreenTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("Aire evacuado", color = TextSecondary, fontSize = 11.sp)
        }
    }
}

// ---------------------------------------------------------------------------
//  Bottom CTA Section
// ---------------------------------------------------------------------------
@Composable
private fun BottomCtaSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BluePrimary,
                contentColor = Color(0xFF001D31)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "Exportar Historial Completo (PDF Acreditativo)",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }

        Button(
            onClick = { },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SurfaceContHigh,
                contentColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "Solicitar Cita con Servicio Técnico Oficial",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }

        // Cryptographic footnote
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = GreenTertiary, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Registro inmutable respaldado en Legrand Cloud Security",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

// ---------------------------------------------------------------------------
//  Add Maintenance Bottom Sheet (kept for ViewModel compatibility)
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMaintenanceSheet(
    onDismiss: () -> Unit,
    onConfirm: (MaintenanceType, String, String, String) -> Unit
) {
    var selectedType by remember { mutableStateOf(MaintenanceType.ANNUAL_REVISION) }
    var description by remember { mutableStateOf("") }
    var techName by remember { mutableStateOf("") }
    var certRef by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = BluePrimary,
        unfocusedBorderColor = OutlineVar,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = BluePrimary,
        focusedLabelColor = BluePrimary,
        unfocusedLabelColor = TextSecondary
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCont,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(36.dp, 4.dp)
                    .clip(CircleShape)
                    .background(OutlineVar)
            )

            Text("Nuevo registro de mantenimiento", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tipo de intervención") },
                    leadingIcon = {
                        Icon(selectedType.icon(), contentDescription = null, tint = selectedType.chipColor(), modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = fieldColors
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = SurfaceContHigh
                ) {
                    MaintenanceType.entries.forEach { type ->
                        DropdownMenuItem(
                            leadingIcon = { Icon(type.icon(), contentDescription = null, tint = type.chipColor(), modifier = Modifier.size(16.dp)) },
                            text = { Text(type.displayName, color = TextPrimary) },
                            onClick = { selectedType = type; expanded = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción de la intervención") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                colors = fieldColors
            )
            OutlinedTextField(
                value = techName,
                onValueChange = { techName = it },
                label = { Text("Técnico responsable (opcional)") },
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors
            )
            OutlinedTextField(
                value = certRef,
                onValueChange = { certRef = it },
                label = { Text("Referencia certificado (opcional)") },
                leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors
            )

            Button(
                onClick = {
                    if (description.isNotBlank()) onConfirm(selectedType, description, techName, certRef)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = description.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary, contentColor = Color(0xFF001D31)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Guardar registro", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Helpers
// ---------------------------------------------------------------------------
private fun List<MaintenanceRecordEntity>.groupByMonth(): List<Pair<String, List<MaintenanceRecordEntity>>> {
    val monthFmt = SimpleDateFormat("MMMM yyyy", Locale("es", "ES"))
    return groupBy { monthFmt.format(Date(it.date)).replaceFirstChar { c -> c.uppercase() } }
        .entries
        .sortedByDescending { it.value.maxOf { r -> r.date } }
        .map { it.key to it.value.sortedByDescending { r -> r.date } }
}
