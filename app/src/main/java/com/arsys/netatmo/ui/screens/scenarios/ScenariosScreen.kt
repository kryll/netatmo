package com.arsys.netatmo.ui.screens.scenarios

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.arsys.netatmo.MainActivity
import com.arsys.netatmo.R
import com.arsys.netatmo.data.local.entities.ScenarioEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenariosScreen(
    navController: NavController,
    viewModel: ScenariosViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Escenarios", fontWeight = FontWeight.Bold) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("scenario/-1") }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo escenario")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Quick actions row
            item {
                Text(
                    "Accesos rápidos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                QuickActionsRow(onAction = { viewModel.executeQuickAction(it) })
            }

            item {
                Text(
                    "Mis escenarios",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (uiState.scenarios.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Sin escenarios", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Crea escenarios para aplicar múltiples ajustes de temperatura con un toque",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(uiState.scenarios, key = { it.id }) { scenario ->
                    ScenarioCard(
                        scenario = scenario,
                        isRunning = uiState.runningScenarioId == scenario.id,
                        onRun = { viewModel.runScenario(scenario) },
                        onEdit = { navController.navigate("scenario/${scenario.id}") },
                        onDelete = { viewModel.deleteScenario(scenario) },
                        onPin = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val sm = context.getSystemService(ShortcutManager::class.java)
                                if (sm.isRequestPinShortcutSupported) {
                                    val intent = Intent(context, MainActivity::class.java).apply {
                                        action = "com.arsys.netatmo.OPEN_SCENARIOS"
                                        putExtra("scenario_id", scenario.id)
                                    }
                                    val info = ShortcutInfo.Builder(context, "sc_${scenario.id}")
                                        .setShortLabel(scenario.name)
                                        .setLongLabel(scenario.name)
                                        .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                                        .setIntent(intent)
                                        .build()
                                    sm.requestPinShortcut(info, null)
                                }
                            }
                        }
                    )
                }
            }

            uiState.successMessage?.let { msg ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(modifier = Modifier.padding(16.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(msg)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionsRow(onAction: (String) -> Unit) {
    val actions = listOf(
        Triple("Confort", Icons.Default.Thermostat, "comfort"),
        Triple("Eco", Icons.Default.EnergySavingsLeaf, "eco"),
        Triple("Ausente", Icons.Default.DirectionsWalk, "away"),
        Triple("Apagado", Icons.Default.PowerOff, "off")
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { (label, icon, action) ->
            OutlinedButton(
                onClick = { onAction(action) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                    Text(label, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun ScenarioCard(
    scenario: ScenarioEntity,
    isRunning: Boolean,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPin: (() -> Unit)? = null
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar escenario") },
            text = { Text("¿Eliminar \"${scenario.name}\"?") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = try { Color(android.graphics.Color.parseColor(scenario.color)) }
                        catch (e: Exception) { MaterialTheme.colorScheme.primaryContainer }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    scenario.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Toca ▶ para aplicar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                IconButton(onClick = onRun) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Ejecutar",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (onPin != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                IconButton(onClick = onPin) {
                    Icon(Icons.Default.AddToHomeScreen, contentDescription = "Añadir al inicio",
                        tint = MaterialTheme.colorScheme.secondary)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar")
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
