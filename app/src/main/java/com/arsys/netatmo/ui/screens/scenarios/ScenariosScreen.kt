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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.arsys.netatmo.MainActivity
import com.arsys.netatmo.R
import com.arsys.netatmo.data.local.entities.ScenarioEntity
import com.arsys.netatmo.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenariosScreen(
    navController: NavController,
    viewModel: ScenariosViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("scenario/-1") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo escenario")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Column {
                        Text(
                            text = "Control Global",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(
                                0.1f,
                                androidx.compose.ui.unit.TextUnitType.Em
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Escenarios",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                HorizontalDivider(color = OutlineVariant, thickness = 1.dp)
            }

            // Quick actions section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 20.dp)
                ) {
                    Text(
                        text = "Accesos rápidos",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    QuickActionsRow(onAction = { viewModel.executeQuickAction(it) })
                }
            }

            // Success message
            uiState.successMessage?.let { msg ->
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f))
                            .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // Scenarios section header
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mis escenarios",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (uiState.scenarios.isNotEmpty()) {
                        Text(
                            text = "${uiState.scenarios.size} guardados",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (uiState.scenarios.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceContainer)
                            .border(1.dp, OutlineVariant, RoundedCornerShape(16.dp))
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Sin escenarios",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Crea escenarios para aplicar múltiples ajustes de temperatura con un toque",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun QuickActionsRow(onAction: (String) -> Unit) {
    data class QuickAction(
        val label: String,
        val icon: ImageVector,
        val action: String,
        val activeColor: Color
    )

    val actions = listOf(
        QuickAction("Confort", Icons.Default.Thermostat, "comfort", ComfortColor),
        QuickAction("Eco", Icons.Default.EnergySavingsLeaf, "eco", MaterialTheme.colorScheme.primary),
        QuickAction("Ausente", Icons.Default.DirectionsWalk, "away", AwayColor),
        QuickAction("Apagado", Icons.Default.PowerOff, "off", MaterialTheme.colorScheme.onSurfaceVariant)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { action ->
            OutlinedButton(
                onClick = { onAction(action.action) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = SurfaceContainerLow,
                    contentColor = action.activeColor
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        action.icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = action.activeColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        action.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
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
            containerColor = SurfaceContainerHigh,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "Eliminar escenario",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    "¿Eliminar \"${scenario.name}\"?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    val scenarioColor = try {
        Color(android.graphics.Color.parseColor(scenario.color))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primaryContainer
    }

    val cardBorderColor = if (isRunning)
        scenarioColor.copy(alpha = 0.5f)
    else
        OutlineVariant

    val cardBackground = if (isRunning)
        scenarioColor.copy(alpha = 0.08f)
    else
        SurfaceContainer

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardBackground)
            .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color strip (4dp left)
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(76.dp)
                .background(
                    scenarioColor,
                    RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                )
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color icon square 48dp
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(scenarioColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    scenario.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Toca ▶ para aplicar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action buttons
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(onClick = onRun) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Ejecutar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (onPin != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                IconButton(onClick = onPin) {
                    Icon(
                        Icons.Default.AddToHomeScreen,
                        contentDescription = "Añadir al inicio",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Editar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
