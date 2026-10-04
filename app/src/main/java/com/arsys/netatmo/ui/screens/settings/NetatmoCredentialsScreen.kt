package com.arsys.netatmo.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

private val Accent = Color(0xFF0284C7)
private val TextPrimary = Color(0xFF1E293B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetatmoCredentialsScreen(
    navController: NavController,
    viewModel: NetatmoCredentialsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var secretVisible by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) navController.popBackStack()
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Eliminar credenciales") },
            text = { Text("Se eliminarán las credenciales guardadas. La próxima vez que inicies sesión necesitarás introducirlas de nuevo.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    viewModel.clearCredentials()
                    navController.popBackStack()
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Volver",
                        tint = Accent
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Credenciales Netatmo",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        HorizontalDivider(color = Color(0xFFE2E8F0))

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Info card
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Accent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "¿Por qué necesito esto?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        "Para conectar la app a tu cuenta Netatmo necesitas unas credenciales de desarrollador. " +
                        "Son gratuitas y se obtienen en el portal de Netatmo en menos de 2 minutos.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://dev.netatmo.com/apps/createanapp")
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Crear app en dev.netatmo.com")
                    }
                }
            }

            // Steps card
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Pasos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    listOf(
                        "Entra en dev.netatmo.com e inicia sesión",
                        "Pulsa \"Create an app\"",
                        "Pon un nombre (ej: Mi Termostato) y guarda",
                        "Copia el Client ID y el Client secret",
                        "Pégalos aquí abajo y guarda"
                    ).forEachIndexed { i, step ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                "${i + 1}. ",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Accent
                            )
                            Text(step, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Current status card
            if (uiState.hasStoredCredentials) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Accent
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Credenciales guardadas",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Client ID: ${uiState.storedClientIdPreview}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(onClick = { showClearDialog = true }) {
                            Text("Eliminar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            HorizontalDivider()

            Text(
                if (uiState.hasStoredCredentials) "Actualizar credenciales" else "Introducir credenciales",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            // Input fields card
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.clientId,
                        onValueChange = { viewModel.updateClientId(it) },
                        label = { Text("Client ID") },
                        placeholder = { Text("ej: tu_client_id") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) }
                    )

                    OutlinedTextField(
                        value = uiState.clientSecret,
                        onValueChange = { viewModel.updateClientSecret(it) },
                        label = { Text("Client Secret") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { secretVisible = !secretVisible }) {
                                Icon(
                                    if (secretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (secretVisible) "Ocultar" else "Mostrar"
                                )
                            }
                        }
                    )
                }
            }

            uiState.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = { viewModel.save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guardar credenciales")
            }
        }
    }
}
