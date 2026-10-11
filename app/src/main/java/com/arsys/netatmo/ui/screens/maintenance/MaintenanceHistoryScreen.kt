package com.arsys.netatmo.ui.screens.maintenance

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
    var showAddSheet by remember { mutableStateOf(false) }

    if (showAddSheet) {
        AddMaintenanceSheet(
            onDismiss = { showAddSheet = false },
            onConfirm = { form ->
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES"))
                val visitMs = try { sdf.parse(form.visitDate)?.time } catch (e: Exception) { null }
                    ?: System.currentTimeMillis()
                viewModel.addRecord(
                    type = form.type,
                    description = form.description,
                    techName = form.techName,
                    certRef = form.certRef,
                    visitDate = visitMs,
                    visitTimeRange = form.visitTimeRange.ifBlank { null },
                    modelo = form.modelo.ifBlank { null },
                    numSerie = form.numSerie.ifBlank { null },
                    version = form.version.ifBlank { null },
                    combustible = form.combustible.ifBlank { null },
                    rendimiento = form.rendimiento.toFloatOrNull(),
                    co = form.co.toFloatOrNull(),
                    coCorrected = form.coCorrected.toFloatOrNull(),
                    coAmbiente = form.coAmbiente.toFloatOrNull(),
                    co2 = form.co2.toFloatOrNull(),
                    o2 = form.o2.toFloatOrNull(),
                    o2Ref = form.o2Ref.toFloatOrNull(),
                    temperaturaHumos = form.temperaturaHumos.toFloatOrNull(),
                    temperaturaAmbiente = form.temperaturaAmbiente.toFloatOrNull(),
                    temperaturaACS = form.temperaturaACS.toFloatOrNull(),
                    temperaturaAguaFria = form.temperaturaAguaFria.toFloatOrNull(),
                    caudalAgua = form.caudalAgua.toFloatOrNull(),
                    potenciaACS = form.potenciaACS.toFloatOrNull(),
                    presionDinamica = form.presionDinamica.toFloatOrNull(),
                    presionEstatica = form.presionEstatica.toFloatOrNull(),
                    tiro = form.tiro.toFloatOrNull(),
                    lambda = form.lambda.toFloatOrNull(),
                    perdidasChimenea = form.perdidasChimenea.toFloatOrNull()
                )
                showAddSheet = false
            }
        )
    }

    Scaffold(
        topBar = {
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceLowest.copy(alpha = 0.80f)
                )
            )
        },
        containerColor = BgColor,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = BluePrimary,
                contentColor = Color(0xFF001D31)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir registro")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Section 1: Summary banner
            if (uiState.totalCount > 0) {
                item {
                    SummaryBanner(
                        totalCount = uiState.totalCount,
                        certCount = uiState.certCount,
                        lastRevisionDate = uiState.records
                            .filter { it.type == MaintenanceType.ANNUAL_REVISION.name }
                            .maxOfOrNull { it.date }
                    )
                }
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
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Summary Banner (counts computed from real records)
// ---------------------------------------------------------------------------
@Composable
private fun SummaryBanner(totalCount: Int, certCount: Int, lastRevisionDate: Long?) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        color = SurfaceContLow,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCell(value = totalCount.toString(), label = "Intervenciones\nregistradas", color = BluePrimary)
            StatCell(value = certCount.toString(), label = "Con\nreferencia", color = OrangeSecondary)
            StatCell(
                value = lastRevisionDate?.let { dateShort.format(Date(it)) } ?: "—",
                label = "Última\nrevisión",
                color = GreenTertiary
            )
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
        Triple(MaintenanceFilter.CERTIFICATE, Icons.Outlined.VerifiedUser, "Con referencia ($certCount)"),
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
                if (hasRecords) "Sin registros para este filtro" else "Aún no hay registros",
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
                                "Expediente #${record.certificateRef}",
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

                        // Combustion analysis data (when present)
                        if (record.rendimiento != null || record.co != null || record.temperaturaHumos != null) {
                            CombustionAnalysisStrip(record)
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
                                    Text("Ver registro", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
//  Combustion Analysis Strip (for boiler revision records)
// ---------------------------------------------------------------------------
@Composable
private fun CombustionAnalysisStrip(record: MaintenanceRecordEntity) {
    val greenColor = GreenTertiary
    val amberColor = Color(0xFFF59E0B)
    val redColor = ErrorColor

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Efficiency + CO row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            record.rendimiento?.let { eff ->
                val c = if (eff >= 95f) greenColor else if (eff >= 88f) amberColor else redColor
                CombustionChip("η ${eff.format1()}%", c)
            }
            record.co?.let { co ->
                val c = if (co < 50f) greenColor else if (co < 100f) amberColor else redColor
                CombustionChip("CO ${co.format0()} ppm", c)
            }
            record.temperaturaHumos?.let {
                CombustionChip("T.Hum ${it.format1()}°C", TextSecondary)
            }
            record.o2?.let {
                CombustionChip("O₂ ${it.format1()}%", TextSecondary)
            }
        }
        // Pressures + flow row
        val hasPressure = record.presionDinamica != null || record.presionEstatica != null || record.caudalAgua != null
        if (hasPressure) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                record.presionDinamica?.let { CombustionChip("P.Din ${it.format1()} mbar", TextSecondary) }
                record.presionEstatica?.let { CombustionChip("P.Est ${it.format1()} mbar", TextSecondary) }
                record.caudalAgua?.let { CombustionChip("${it.format0()} l/min", TextSecondary) }
            }
        }
        // Model + serial
        val hasEquip = !record.modelo.isNullOrBlank() || !record.numSerie.isNullOrBlank()
        if (hasEquip) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                record.modelo?.let { CombustionChip(it, BluePrimary) }
                record.numSerie?.let { CombustionChip("SN: $it", TextSecondary) }
            }
        }
    }
}

private fun Float.format1() = "%.1f".format(this)
private fun Float.format0() = "%.0f".format(this)

@Composable
private fun CombustionChip(label: String, color: Color) {
    Surface(color = SurfaceContHighest, shape = RoundedCornerShape(6.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ---------------------------------------------------------------------------
//  Add Maintenance Bottom Sheet (kept for ViewModel compatibility)
// ---------------------------------------------------------------------------
data class BoilerRevisionForm(
    val type: MaintenanceType,
    val description: String,
    val techName: String,
    val certRef: String,
    val visitDate: String,       // dd/MM/yyyy
    val visitTimeRange: String,  // HH:mm - HH:mm
    val modelo: String,
    val numSerie: String,
    val version: String,
    val combustible: String,
    // Combustion readings
    val rendimiento: String,
    val co: String,
    val coCorrected: String,
    val coAmbiente: String,
    val co2: String,
    val o2: String,
    val o2Ref: String,
    val temperaturaHumos: String,
    val temperaturaAmbiente: String,
    val temperaturaACS: String,
    val temperaturaAguaFria: String,
    val caudalAgua: String,
    val potenciaACS: String,
    val presionDinamica: String,
    val presionEstatica: String,
    val tiro: String,
    val lambda: String,
    val perdidasChimenea: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMaintenanceSheet(
    onDismiss: () -> Unit,
    onConfirm: (BoilerRevisionForm) -> Unit
) {
    var selectedType by remember { mutableStateOf(MaintenanceType.ANNUAL_REVISION) }
    var description by remember { mutableStateOf("") }
    var techName by remember { mutableStateOf("") }
    var certRef by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var showCombustion by remember { mutableStateOf(false) }

    // Visit info
    var visitDate by remember { mutableStateOf("") }
    var visitTimeRange by remember { mutableStateOf("") }

    // Equipment
    var modelo by remember { mutableStateOf("") }
    var numSerie by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("") }
    var combustible by remember { mutableStateOf("") }

    // Combustion readings
    var rendimiento by remember { mutableStateOf("") }
    var co by remember { mutableStateOf("") }
    var coCorrected by remember { mutableStateOf("") }
    var coAmbiente by remember { mutableStateOf("") }
    var co2 by remember { mutableStateOf("") }
    var o2 by remember { mutableStateOf("") }
    var o2Ref by remember { mutableStateOf("") }
    var temperaturaHumos by remember { mutableStateOf("") }
    var temperaturaAmbiente by remember { mutableStateOf("") }
    var temperaturaACS by remember { mutableStateOf("") }
    var temperaturaAguaFria by remember { mutableStateOf("") }
    var caudalAgua by remember { mutableStateOf("") }
    var potenciaACS by remember { mutableStateOf("") }
    var presionDinamica by remember { mutableStateOf("") }
    var presionEstatica by remember { mutableStateOf("") }
    var tiro by remember { mutableStateOf("") }
    var lambda by remember { mutableStateOf("") }
    var perdidasChimenea by remember { mutableStateOf("") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = BluePrimary,
        unfocusedBorderColor = OutlineVar,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = BluePrimary,
        focusedLabelColor = BluePrimary,
        unfocusedLabelColor = TextSecondary
    )

    val isRevision = selectedType == MaintenanceType.ANNUAL_REVISION

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCont,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Box(
                    modifier = Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp).clip(CircleShape).background(OutlineVar)
                )
            }
            item {
                Text("Nuevo parte de mantenimiento", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            // Type selector
            item {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo de intervención") },
                        leadingIcon = {
                            Icon(selectedType.icon(), contentDescription = null, tint = selectedType.chipColor(), modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = fieldColors
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = SurfaceContHigh) {
                        MaintenanceType.entries.forEach { type ->
                            DropdownMenuItem(
                                leadingIcon = { Icon(type.icon(), contentDescription = null, tint = type.chipColor(), modifier = Modifier.size(16.dp)) },
                                text = { Text(type.displayName, color = TextPrimary) },
                                onClick = { selectedType = type; expanded = false }
                            )
                        }
                    }
                }
            }

            // Visit date + time
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = visitDate,
                        onValueChange = { visitDate = it },
                        label = { Text("Fecha visita (dd/MM/yyyy)") },
                        modifier = Modifier.weight(1f),
                        colors = fieldColors
                    )
                    OutlinedTextField(
                        value = visitTimeRange,
                        onValueChange = { visitTimeRange = it },
                        label = { Text("Horario") },
                        placeholder = { Text("10:04 - 10:39", color = TextSecondary.copy(0.5f)) },
                        modifier = Modifier.weight(1f),
                        colors = fieldColors
                    )
                }
            }

            // Description + technician
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Observaciones / descripción") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = fieldColors
                )
            }
            item {
                OutlinedTextField(
                    value = techName,
                    onValueChange = { techName = it },
                    label = { Text("Técnico responsable") },
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
            }

            // Equipment info (for ANNUAL_REVISION)
            if (isRevision) {
                item {
                    Text("EQUIPO", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = modelo, onValueChange = { modelo = it }, label = { Text("Modelo") }, modifier = Modifier.weight(1f), colors = fieldColors)
                        OutlinedTextField(value = numSerie, onValueChange = { numSerie = it }, label = { Text("Nº Serie") }, modifier = Modifier.weight(1f), colors = fieldColors)
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = version, onValueChange = { version = it }, label = { Text("Versión") }, modifier = Modifier.weight(1f), colors = fieldColors)
                        OutlinedTextField(value = combustible, onValueChange = { combustible = it }, label = { Text("Combustible") }, modifier = Modifier.weight(1f), colors = fieldColors)
                    }
                }

                // Combustion analysis toggle
                item {
                    Surface(
                        onClick = { showCombustion = !showCombustion },
                        color = if (showCombustion) BluePrimary.copy(alpha = 0.12f) else SurfaceContHigh,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Science, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(16.dp))
                                Text("Análisis de combustión", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            Icon(
                                if (showCombustion) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        }
                    }
                }

                if (showCombustion) {
                    item { Text("LECTURAS", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp) }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CombustionField("Rendimiento (%)", rendimiento, { rendimiento = it }, Modifier.weight(1f), fieldColors)
                            CombustionField("T. Humos (°C)", temperaturaHumos, { temperaturaHumos = it }, Modifier.weight(1f), fieldColors)
                            CombustionField("T. Ambiente (°C)", temperaturaAmbiente, { temperaturaAmbiente = it }, Modifier.weight(1f), fieldColors)
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CombustionField("CO (ppm)", co, { co = it }, Modifier.weight(1f), fieldColors)
                            CombustionField("CO corregido (ppm)", coCorrected, { coCorrected = it }, Modifier.weight(1f), fieldColors)
                            CombustionField("CO ambiente (ppm)", coAmbiente, { coAmbiente = it }, Modifier.weight(1f), fieldColors)
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CombustionField("CO₂ (%)", co2, { co2 = it }, Modifier.weight(1f), fieldColors)
                            CombustionField("O₂ (%)", o2, { o2 = it }, Modifier.weight(1f), fieldColors)
                            CombustionField("O₂ ref. (%)", o2Ref, { o2Ref = it }, Modifier.weight(1f), fieldColors)
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CombustionField("P. dinámica (mbar)", presionDinamica, { presionDinamica = it }, Modifier.weight(1f), fieldColors)
                            CombustionField("P. estática (mbar)", presionEstatica, { presionEstatica = it }, Modifier.weight(1f), fieldColors)
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CombustionField("T. ACS (°C)", temperaturaACS, { temperaturaACS = it }, Modifier.weight(1f), fieldColors)
                            CombustionField("T. agua fría (°C)", temperaturaAguaFria, { temperaturaAguaFria = it }, Modifier.weight(1f), fieldColors)
                            CombustionField("Caudal (l/min)", caudalAgua, { caudalAgua = it }, Modifier.weight(1f), fieldColors)
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CombustionField("Tiro (mbar)", tiro, { tiro = it }, Modifier.weight(1f), fieldColors)
                            CombustionField("Lambda (%)", lambda, { lambda = it }, Modifier.weight(1f), fieldColors)
                            CombustionField("Pérd. chimenea (%)", perdidasChimenea, { perdidasChimenea = it }, Modifier.weight(1f), fieldColors)
                        }
                    }
                    item {
                        CombustionField("Potencia ACS (KW)", potenciaACS, { potenciaACS = it }, Modifier.fillMaxWidth(), fieldColors)
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = certRef,
                    onValueChange = { certRef = it },
                    label = { Text("Referencia certificado (opcional)") },
                    leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
            }

            item {
                Button(
                    onClick = {
                        if (description.isNotBlank()) {
                            onConfirm(
                                BoilerRevisionForm(
                                    type = selectedType,
                                    description = description,
                                    techName = techName,
                                    certRef = certRef,
                                    visitDate = visitDate,
                                    visitTimeRange = visitTimeRange,
                                    modelo = modelo,
                                    numSerie = numSerie,
                                    version = version,
                                    combustible = combustible,
                                    rendimiento = rendimiento,
                                    co = co,
                                    coCorrected = coCorrected,
                                    coAmbiente = coAmbiente,
                                    co2 = co2,
                                    o2 = o2,
                                    o2Ref = o2Ref,
                                    temperaturaHumos = temperaturaHumos,
                                    temperaturaAmbiente = temperaturaAmbiente,
                                    temperaturaACS = temperaturaACS,
                                    temperaturaAguaFria = temperaturaAguaFria,
                                    caudalAgua = caudalAgua,
                                    potenciaACS = potenciaACS,
                                    presionDinamica = presionDinamica,
                                    presionEstatica = presionEstatica,
                                    tiro = tiro,
                                    lambda = lambda,
                                    perdidasChimenea = perdidasChimenea
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = description.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary, contentColor = Color(0xFF001D31)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Guardar parte", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun CombustionField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    colors: TextFieldColors
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 10.sp) },
        modifier = modifier,
        colors = colors,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
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
