package com.arsys.netatmo.ui.screens.maintenance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Historial de Mantenimiento",
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
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceLowest)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = BluePrimary,
                contentColor = Color(0xFF001D31),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir registro")
            }
        },
        containerColor = BgColor
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // --- Equipment & Warranty Summary Banner ---
            item {
                EquipmentSummaryBanner(
                    totalCount = uiState.totalCount,
                    certCount = uiState.certCount,
                    annualCount = uiState.annualCount
                )
            }

            // --- Filter Chips ---
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

            // --- Spacer between chips and list ---
            item { Spacer(Modifier.height(8.dp)) }

            // --- Records ---
            val displayed = uiState.filteredRecords
            if (displayed.isEmpty()) {
                item {
                    EmptyMaintenanceState(hasRecords = uiState.totalCount > 0)
                }
            } else {
                items(displayed, key = { it.id }) { record ->
                    MaintenanceRecordCard(
                        record = record,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        onCertificate = { onCertificate(record.id) },
                        onDelete = { viewModel.deleteRecord(record) }
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        AddMaintenanceSheet(
            onDismiss = { showAddSheet = false },
            onConfirm = { type, desc, tech, cert ->
                viewModel.addRecord(type, desc, tech, cert)
                showAddSheet = false
            }
        )
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
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineVar)
    ) {
        Box {
            // Ambient glow
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .offset(x = 220.dp, y = (-20).dp)
                    .clip(CircleShape)
                    .background(BluePrimary.copy(alpha = 0.07f))
            )

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Device header
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = SurfaceContHighest,
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
                        Text("Caldera / Sistema de calefacción", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Circuito Hidráulico Primario", color = TextSecondary, fontSize = 12.sp)
                    }
                    Surface(
                        color = GreenTertiary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, GreenTertiary.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenTertiary, modifier = Modifier.size(12.dp))
                            Text("OK", color = GreenTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Warranty pill
                Surface(
                    color = SurfaceContHighest.copy(alpha = 0.6f),
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
                        Text("Garantía Oficial Activa", color = GreenTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Quick stats
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
                        Box(modifier = Modifier.width(1.dp).height(32.dp).background(OutlineVar))
                        StatCell(value = certCount.toString(), label = "Certificados\noficiales", color = OrangeSecondary)
                        Box(modifier = Modifier.width(1.dp).height(32.dp).background(OutlineVar))
                        StatCell(value = annualCount.toString(), label = "Revisiones\nanuales", color = GreenTertiary)
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
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
        Triple(MaintenanceFilter.CERTIFICATE, Icons.Outlined.VerifiedUser, "Certificados ($certCount)"),
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
                shape = RoundedCornerShape(999.dp),
                border = if (!selected) BorderStroke(1.dp, OutlineVar) else null
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
                        modifier = Modifier.size(15.dp)
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
            Text(
                "Pulsa + para añadir el primer registro",
                color = Color(0xFF89929B),
                fontSize = 13.sp
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
    onDelete: () -> Unit
) {
    val type = remember(record.type) {
        try { MaintenanceType.valueOf(record.type) } catch (e: Exception) { MaintenanceType.OTHER }
    }
    val chipColor = type.chipColor()
    val typeIcon = type.icon()
    val now = remember { System.currentTimeMillis() }
    val warrantyActive = record.warrantyExtendedUntil?.let { it > now } == true
    var showDeleteDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceContLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineVar)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top row: type badge + date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = chipColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, chipColor.copy(alpha = 0.45f))
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
                Text(dateShort.format(Date(record.date)), color = TextSecondary, fontSize = 12.sp)
            }

            // Description
            Text(record.description, color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)

            // Technician + Cert ref row
            if (record.technicianName.isNotBlank() || record.certificateRef.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (record.technicianName.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                            Text(record.technicianName, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    if (record.certificateRef.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Description, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(14.dp))
                            Text(record.certificateRef, color = BluePrimary, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Warranty badge
            AnimatedVisibility(visible = warrantyActive, enter = fadeIn(), exit = fadeOut()) {
                Surface(
                    color = GreenTertiary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, GreenTertiary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = GreenTertiary, modifier = Modifier.size(14.dp))
                        Text("Garantía extendida", color = GreenTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        record.warrantyExtendedUntil?.let { until ->
                            Text("hasta ${dateShort.format(Date(until))}", color = GreenTertiary.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                    }
                }
            }

            // Action row
            HorizontalDivider(color = OutlineVar.copy(alpha = 0.5f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCertificate,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BluePrimary),
                    border = BorderStroke(1.dp, BluePrimary.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Ver certificado", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = ErrorColor.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = SurfaceCont,
            title = { Text("Eliminar registro", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = { Text("¿Deseas eliminar este registro de mantenimiento? Esta acción no se puede deshacer.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("Eliminar", color = ErrorColor, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
//  Add Maintenance Bottom Sheet
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMaintenanceSheet(
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
            // Handle + title
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(36.dp, 4.dp)
                    .clip(CircleShape)
                    .background(OutlineVar)
            )

            Text("Nuevo registro de mantenimiento", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)

            // Type dropdown
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
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
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
                modifier = Modifier.fillMaxWidth().height(52.dp),
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
