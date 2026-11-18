package ai.sakana.aura.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        setContent {
            AuraTheme {
                val viewModel: GalleryViewModel = hiltViewModel()
                GalleryRoute(viewModel)
            }
        }
    }
}
