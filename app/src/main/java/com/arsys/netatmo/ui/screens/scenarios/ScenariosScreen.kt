package com.arsys.netatmo.ui.screens.scenarios

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

private val Accent = Color(0xFF0284C7)
private val TextPrimary = Color(0xFF1E293B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenariosScreen(
    navController: NavController,
    viewModel: ScenariosViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("scenario/-1") },
                containerColor = Accent,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo escenario")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Text(
                    "Escenarios",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider(color = Color(0xFFE2E8F0))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(32.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = Accent
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Sin escenarios",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
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
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Row(modifier = Modifier.padding(16.dp)) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(msg)
                            }
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
                contentPadding = PaddingValues(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Accent
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Accent)
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isRunning) Modifier.border(2.dp, Accent, RoundedCornerShape(16.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning)
                Accent.copy(alpha = 0.06f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Accent strip on the left
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(72.dp)
                    .background(
                        Accent,
                        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = try {
                        Color(android.graphics.Color.parseColor(scenario.color))
                    } catch (e: Exception) {
                        Accent
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Accent
                    )
                } else {
                    IconButton(onClick = onRun) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Ejecutar",
                            tint = Accent
                        )
                    }
                }
                if (onPin != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    IconButton(onClick = onPin) {
                        Icon(
                            Icons.Default.AddToHomeScreen,
                            contentDescription = "Añadir al inicio",
                            tint = Accent
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
