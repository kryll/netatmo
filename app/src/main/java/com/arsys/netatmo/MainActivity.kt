package com.arsys.netatmo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.arsys.netatmo.domain.model.ThemeMode
import com.arsys.netatmo.ui.navigation.AppNavGraph
import com.arsys.netatmo.ui.screens.auth.AuthViewModel
import com.arsys.netatmo.ui.screens.settings.SettingsViewModel
import com.arsys.netatmo.ui.theme.NetatmoTheme
import com.arsys.netatmo.util.AuthDebugLogger
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthIntent(intent)
        resolveShortcutIntent(intent)
        setContent {
            val settingsState by settingsViewModel.uiState.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (settingsState.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }
            NetatmoTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavGraph(
                        pendingRoute = pendingRoute,
                        onRoutePending = { pendingRoute = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
        resolveShortcutIntent(intent)
    }

    private fun resolveShortcutIntent(intent: Intent) {
        if (intent.action == "com.arsys.netatmo.OPEN_SCENARIOS") {
            pendingRoute = "scenarios"
        }
    }

    private fun handleAuthIntent(intent: Intent) {
        val uri = intent.data
        if (BuildConfig.DEBUG) {
            AuthDebugLogger.log("handleAuthIntent: action=${intent.action} uri=$uri")
        }
        if (uri == null) return
        if (uri.scheme == "com.arsys.netatmo" && uri.host == "oauth") {
            val code = uri.getQueryParameter("code")
            val receivedState = uri.getQueryParameter("state")
            val error = uri.getQueryParameter("error")
            val errorDesc = uri.getQueryParameter("error_description")
            if (BuildConfig.DEBUG) {
                AuthDebugLogger.log("  deep link recibido · code=${code?.take(8)}... error=$error desc=$errorDesc")
            }
            if (error != null) {
                if (BuildConfig.DEBUG) {
                    AuthDebugLogger.log("  ❌ Netatmo devolvió error: $error – $errorDesc")
                }
                intent.data = null
                return
            }
            if (code == null) {
                if (BuildConfig.DEBUG) {
                    AuthDebugLogger.log("  ❌ Sin code ni error en el deep link")
                }
                return
            }
            // Consume the deep link: an activity recreation (rotation, process death) would
            // otherwise re-exchange the same one-time code and surface a bogus login error.
            intent.data = null
            authViewModel.handleAuthCode(
                code = code,
                receivedState = receivedState,
                onSuccess = {
                    if (BuildConfig.DEBUG) {
                        AuthDebugLogger.log("  ✅ Login exitoso")
                    }
                },
                onError = { msg ->
                    if (BuildConfig.DEBUG) {
                        AuthDebugLogger.log("  ❌ handleAuthCode error: $msg")
                    }
                }
            )
        } else {
            if (BuildConfig.DEBUG) {
                AuthDebugLogger.log("  URI no reconocida: $uri")
            }
        }
    }
}
