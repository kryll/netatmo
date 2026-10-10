package com.arsys.netatmo.ui.screens.maintenance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private val BgColor = Color(0xFF101419)
private val CardColor = Color(0xFF1C2025)
private val BorderColor = Color(0xFF3F4850)
private val TextPrimary = Color(0xFFE0E2EA)
private val TextSecondary = Color(0xFFBFC7D2)
private val BlueAccent = Color(0xFF93CCFF)
private val GreenOk = Color(0xFF62DF7D)
private val OrangeWarn = Color(0xFFFFB599)

private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES"))

private fun MaintenanceType.chipColor() = when (this) {
    MaintenanceType.ANNUAL_REVISION -> GreenOk
    MaintenanceType.PURGE -> BlueAccent
    MaintenanceType.PRESSURE_CHECK -> OrangeWarn
    MaintenanceType.VALVE_CALIBRATION -> Color(0xFFBFC7D2)
    MaintenanceType.OTHER -> Color(0xFF89929B)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceHistoryScreen(
    onBack: () -> Unit,
    onCertificate: (Long) -> Unit,
    viewModel: MaintenanceViewModel = hiltViewModel()
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de Mantenimiento", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E13))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = BlueAccent,
                contentColor = Color(0xFF001D31)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir registro")
            }
        },
        containerColor = BgColor
    ) { padding ->
        if (records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.Assignment, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(64.dp))
                    Text("Sin registros de mantenimiento", color = TextSecondary, fontSize = 16.sp)
                    Text("Pulsa + para añadir el primer registro", color = Color(0xFF89929B), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    MaintenanceRecordCard(
                        record = record,
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

@Composable
private fun MaintenanceRecordCard(
    record: MaintenanceRecordEntity,
    onCertificate: () -> Unit,
    onDelete: () -> Unit
) {
    val type = try { MaintenanceType.valueOf(record.type) } catch (e: Exception) { MaintenanceType.OTHER }
    val chipColor = type.chipColor()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = chipColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, chipColor.copy(alpha = 0.5f))
                ) {
                    Text(type.displayName, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = chipColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(dateFormatter.format(Date(record.date)), color = TextSecondary, fontSize = 12.sp)
            }

            Text(record.description, color = TextPrimary, fontSize = 14.sp)

            if (record.technicianName.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                    Text(record.technicianName, color = TextSecondary, fontSize = 12.sp)
                }
            }

            if (record.certificateRef.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(14.dp))
                    Text(record.certificateRef, color = BlueAccent, fontSize = 12.sp)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCertificate,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BlueAccent),
                    border = BorderStroke(1.dp, BlueAccent.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Certificado", fontSize = 12.sp)
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color(0xFFFFB4AB))
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar registro", color = TextPrimary) },
            text = { Text("¿Eliminar este registro de mantenimiento?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("Eliminar", color = Color(0xFFFFB4AB))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar", color = TextSecondary) }
            },
            containerColor = CardColor
        )
    }
}

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C2025)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Nuevo registro", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tipo", color = TextSecondary) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BlueAccent, unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Color(0xFF262A30)) {
                    MaintenanceType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName, color = TextPrimary) },
                            onClick = { selectedType = type; expanded = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BlueAccent, unfocusedBorderColor = BorderColor,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                )
            )
            OutlinedTextField(
                value = techName,
                onValueChange = { techName = it },
                label = { Text("Técnico (opcional)", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BlueAccent, unfocusedBorderColor = BorderColor,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                )
            )
            OutlinedTextField(
                value = certRef,
                onValueChange = { certRef = it },
                label = { Text("Ref. certificado (opcional)", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BlueAccent, unfocusedBorderColor = BorderColor,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                )
            )

            Button(
                onClick = { if (description.isNotBlank()) onConfirm(selectedType, description, techName, certRef) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = description.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Color(0xFF001D31)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Guardar registro", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
