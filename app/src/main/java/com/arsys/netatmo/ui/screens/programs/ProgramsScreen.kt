package com.arsys.netatmo.ui.screens.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.arsys.netatmo.ui.theme.*

@Composable
fun ProgramsScreen(
    navController: NavController,
    viewModel: ProgramsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var programToDeactivate by remember { mutableStateOf<ProgramUi?>(null) }
    var programToDelete by remember { mutableStateOf<ProgramUi?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.messageShown()
        }
    }

    programToDeactivate?.let { program ->
        AlertDialog(
            onDismissRequest = { programToDeactivate = null },
            containerColor = SurfaceContainerHigh,
            title = {
                Text(
                    "Desactivar programa",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    "El termostato solo mantendrá la temperatura antihielo hasta que actives un programa. " +
                        "\"${program.name}\" no se borra.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    programToDeactivate = null
                    viewModel.deactivate()
                }) {
                    Text("Desactivar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { programToDeactivate = null }) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    programToDelete?.let { program ->
        AlertDialog(
            onDismissRequest = { programToDelete = null },
            containerColor = SurfaceContainerHigh,
            title = {
                Text(
                    "Eliminar programa",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    "¿Eliminar \"${program.name}\"? Esta acción no se puede deshacer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    programToDelete = null
                    viewModel.delete(program)
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { programToDelete = null }) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Column {
                    Text(
                        text = "Calefacción",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Programas",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(color = OutlineVariant, thickness = 1.dp)

            when {
                uiState.isLoading && uiState.programs.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                uiState.error != null && uiState.programs.isEmpty() -> {
                    ProgramsMessageView(
                        icon = Icons.Default.ErrorOutline,
                        title = "No se pudieron cargar los programas",
                        body = uiState.error.orEmpty(),
                        actionLabel = "Reintentar",
                        onAction = { viewModel.retry() }
                    )
                }
                uiState.programs.isEmpty() -> {
                    ProgramsMessageView(
                        icon = Icons.Default.Schedule,
                        title = "No hay programas",
                        body = "Crea un programa para que el termostato siga un horario de calefacción.",
                        actionLabel = null,
                        onAction = {}
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 88.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!uiState.followsSchedule) {
                            item {
                                Text(
                                    text = "El termostato no está siguiendo ningún programa " +
                                        "(antihielo, ausente o apagado). Reanuda uno para volver al horario.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(uiState.programs, key = { it.id }) { program ->
                            ProgramCard(
                                program = program,
                                followsSchedule = uiState.followsSchedule,
                                enabled = !uiState.isWorking,
                                onActivate = { viewModel.activate(program) },
                                onDeactivate = { programToDeactivate = program },
                                onEdit = { navController.navigate("schedule/${program.id}") },
                                onDelete = { programToDelete = program }
                            )
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { navController.navigate("schedule/new") },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Crear programa")
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        )
    }
}

@Composable
private fun ProgramsMessageView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private fun formatTemp(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

private fun programSummary(program: ProgramUi): String {
    val zones = if (program.zoneCount == 1) "1 zona" else "${program.zoneCount} zonas"
    val slots = if (program.slotCount == 1) "1 tramo" else "${program.slotCount} tramos"
    val min = program.minTemp
    val max = program.maxTemp
    val range = when {
        min == null || max == null -> null
        min == max -> "${formatTemp(min)} °C"
        else -> "${formatTemp(min)}–${formatTemp(max)} °C"
    }
    return listOfNotNull(zones, slots, range).joinToString(" · ")
}

@Composable
private fun ProgramCard(
    program: ProgramUi,
    followsSchedule: Boolean,
    enabled: Boolean,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val running = program.isSelected && followsSchedule
    val paused = program.isSelected && !followsSchedule
    val accent = when {
        running -> ComfortColor
        paused -> AwayColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OutlineVariant, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainer
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(accent.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = program.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = programSummary(program),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (program.isSelected) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accent.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (running) "Activo" else "Pausado",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = OutlineVariant.copy(alpha = 0.5f)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    running -> TextButton(
                        onClick = onDeactivate,
                        enabled = enabled,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.PauseCircleOutline, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Desactivar")
                    }
                    else -> TextButton(
                        onClick = onActivate,
                        enabled = enabled,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PlayCircleOutline, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (paused) "Reanudar" else "Activar")
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit, enabled = enabled) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Modificar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete, enabled = enabled && !program.isSelected) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = if (program.isSelected) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        else MaterialTheme.colorScheme.error
                    )
                }
            }

            if (program.isSelected) {
                Text(
                    text = "El programa seleccionado no se puede eliminar. Activa otro programa antes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
