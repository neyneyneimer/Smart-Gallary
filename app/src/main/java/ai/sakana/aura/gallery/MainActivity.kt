package ai.sakana.aura.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import ai.sakana.aura.gallery.diagnostics.CrashRecoveryStore
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import ai.sakana.aura.gallery.design.AuraTheme
import ai.sakana.aura.gallery.presentation.GalleryRoute
import ai.sakana.aura.gallery.presentation.GalleryViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        val recoverFromPreviousCrash = CrashRecoveryStore(this).consumeFatalCrash()
        setContent {
            var showCrashRecovery by remember { mutableStateOf(recoverFromPreviousCrash) }
            AuraTheme {
                val viewModel: GalleryViewModel = hiltViewModel()
                GalleryRoute(viewModel)
                if (showCrashRecovery) {
                    AlertDialog(
                        onDismissRequest = { showCrashRecovery = false },
                        title = { Text("Aura restarted") },
                        text = { Text("Aura closed unexpectedly last time. Your original photos and videos were not changed. The app has restarted safely; please try the action again.") },
                        confirmButton = {
                            TextButton(onClick = { showCrashRecovery = false }) { Text("Continue") }
                        },
                    )
                }
            }
        }
    }
}
