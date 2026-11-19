package ai.sakana.aura.gallery.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.sakana.aura.gallery.design.AuraTokens

/**
 * Compose-compatible fallback surface for errors exposed through application state.
 *
 * Compose does not support catching exceptions around composable invocations with try/catch.
 * Event/repository failures must be converted to screen state by the ViewModel or caller.
 */
@Composable
internal fun ScreenErrorBoundary(
    screenName: String,
    errorMessage: String? = null,
    onRetry: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!errorMessage.isNullOrBlank()) {
        ScreenFailureFallback(screenName, errorMessage, onRetry)
    } else {
        content()
    }
}

@Composable
private fun ScreenFailureFallback(screenName: String, message: String, onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = AuraTokens.Void) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("$screenName needs attention", style = MaterialTheme.typography.headlineMedium, color = AuraTokens.Paper)
            Text(
                message,
                modifier = Modifier.padding(top = 10.dp, bottom = 18.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = AuraTokens.PaperMuted,
            )
            Button(onClick = onRetry) { Text("Try again") }
        }
    }
}
