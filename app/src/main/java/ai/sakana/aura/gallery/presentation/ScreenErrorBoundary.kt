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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.sakana.aura.gallery.design.AuraTokens
import timber.log.Timber

/** Synchronous composition fallback; deferred layout/draw and process crashes are handled separately. */
@Composable
internal fun ScreenErrorBoundary(
    screenName: String,
    onRetry: () -> Unit,
    content: @Composable () -> Unit,
) {
    var failed by remember(screenName) { mutableStateOf(false) }
    if (failed) {
        ScreenFailureFallback(screenName, onRetry)
    } else {
        try {
            content()
        } catch (error: Exception) {
            Timber.e(error, "Screen composition failed: %s", screenName)
            SideEffect { failed = true }
            ScreenFailureFallback(screenName, onRetry)
        }
    }
}

@Composable
private fun ScreenFailureFallback(screenName: String, onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = AuraTokens.Void) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("This screen needs a restart", style = MaterialTheme.typography.headlineMedium, color = AuraTokens.Paper)
            Text(
                "$screenName could not be displayed. Your original media has not been changed.",
                modifier = Modifier.padding(top = 10.dp, bottom = 18.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = AuraTokens.PaperMuted,
            )
            Button(onClick = onRetry) { Text("Try again") }
        }
    }
}
