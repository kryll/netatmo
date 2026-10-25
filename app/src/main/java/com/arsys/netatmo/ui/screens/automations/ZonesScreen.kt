package com.arsys.netatmo.ui.screens.automations

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.arsys.netatmo.ui.theme.SurfaceContainerLow

/**
 * Saved places ("zonas") that advanced automations can use as arrival/departure triggers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZonesScreen(
    navController: NavController,
    viewModel: ZonesViewModel = hiltViewModel()
) {
    val zones by viewModel.zones.collectAsState()
    val form by viewModel.form.collectAsState()
    val pendingDelete by viewModel.pendingDelete.collectAsState()
    val context = LocalContext.current

    // Foreground location is needed to read the current position; background location is what
    // lets Android report arrivals and departures while the app is closed.
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) {
            viewModel.useCurrentLocation()
            val bg = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            if (bg != PackageManager.PERMISSION_GRANTED) backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zonas") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.startNew() }) {
                Icon(Icons.Default.Add, contentDescription = "Añadir zona")
            }
        }
    ) { padding ->
        if (zones.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Sin zonas", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Añade lugares (casa, trabajo, colegio...) para usarlos como disparador \"Zona\" en las automatizaciones avanzadas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(zones, key = { it.id }) { zone ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceContainerLow, RoundedCornerShape(16.dp))
                            .clickable { viewModel.startEdit(zone) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(zone.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Radio ${zone.radius.toInt()} m · %.5f, %.5f".format(zone.latitude, zone.longitude),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.askDelete(zone) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar zona")
                        }
                    }
                }
            }
        }
    }

    form?.let { f ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissForm() },
            title = { Text(if (f.id == null) "Nueva zona" else "Editar zona") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = f.name,
                        onValueChange = viewModel::updateName,
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = f.latitude,
                        onValueChange = viewModel::updateLatitude,
                        label = { Text("Latitud") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = f.longitude,
                        onValueChange = viewModel::updateLongitude,
                        label = { Text("Longitud") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            locationLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        },
                        enabled = !f.isLoadingLocation,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (f.isLoadingLocation) {
                            CircularProgressIndicator(modifier = Modifier.height(18.dp))
                        } else {
                            Text("Usar mi ubicación actual")
                        }
                    }
                    Text("Radio: ${f.radius.toInt()} m", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = f.radius,
                        onValueChange = viewModel::updateRadius,
                        valueRange = 50f..2000f
                    )
                    f.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.save() }, enabled = f.isValid) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissForm() }) { Text("Cancelar") }
            }
        )
    }

    pendingDelete?.let { (zone, usedBy) ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("Eliminar zona") },
            text = {
                Text(
                    if (usedBy.isEmpty()) "¿Eliminar \"${zone.name}\"?"
                    else "\"${zone.name}\" la usan: ${usedBy.joinToString(", ")}. Esos disparadores dejarán de funcionar. ¿Eliminar?"
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.confirmDelete() }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { viewModel.cancelDelete() }) { Text("Cancelar") } }
        )
    }
}
