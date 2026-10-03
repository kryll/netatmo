package com.arsys.netatmo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.arsys.netatmo.ui.navigation.AppNavGraph
import com.arsys.netatmo.ui.screens.auth.AuthViewModel
import com.arsys.netatmo.ui.theme.NetatmoTheme
import com.arsys.netatmo.util.AuthDebugLogger
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthIntent(intent)
        setContent {
            NetatmoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavGraph()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent) {
        val uri = intent.data
        AuthDebugLogger.log("handleAuthIntent: action=${intent.action} uri=$uri", this)
        if (uri == null) return
        if (uri.scheme == "com.arsys.netatmo" && uri.host == "oauth") {
            val code = uri.getQueryParameter("code")
            val error = uri.getQueryParameter("error")
            val errorDesc = uri.getQueryParameter("error_description")
            AuthDebugLogger.log("  deep link recibido · code=${code?.take(8)}... error=$error desc=$errorDesc", this)
            if (error != null) {
                AuthDebugLogger.log("  ❌ Netatmo devolvió error: $error – $errorDesc", this)
                return
            }
            if (code == null) {
                AuthDebugLogger.log("  ❌ Sin code ni error en el deep link", this)
                return
            }
            authViewModel.handleAuthCode(
                code = code,
                onSuccess = { AuthDebugLogger.log("  ✅ Login exitoso", this) },
                onError = { msg -> AuthDebugLogger.log("  ❌ handleAuthCode error: $msg", this) }
            )
        } else {
            AuthDebugLogger.log("  URI no reconocida: $uri", this)
        }
    }
}
