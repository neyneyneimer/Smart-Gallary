package ai.sakana.aura.gallery.ui.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.sakana.aura.gallery.design.AuraTokens
import ai.sakana.aura.gallery.design.JetBrainsMono
import ai.sakana.aura.gallery.design.Manrope

/** Bottom sheet for MediaEditorExporter.saveToneEdit's onProgress(0..100) callback. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportProgressSheet(percent: Int, errorMessage: String?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AuraTokens.Surface2) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
            val done = percent >= 100 && errorMessage == null
            Text(
                when { errorMessage != null -> "Export failed"; done -> "Saved"; else -> "Exporting edited copy" },
                color = AuraTokens.Paper, fontFamily = Manrope, fontSize = 15.sp,
            )
            Text(errorMessage ?: (percent.coerceIn(0, 100).toString() + "%"), color = AuraTokens.PaperMuted, fontFamily = JetBrainsMono, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            if (errorMessage == null) {
                LinearProgressIndicator(progress = { percent.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth(), color = AuraTokens.Safelight, trackColor = AuraTokens.Surface3)
            }
            if (done || errorMessage != null) TextButton(onClick = onDismiss) { Text("CLOSE", color = AuraTokens.Safelight, fontFamily = JetBrainsMono, fontSize = 11.sp) }
        }
    }
}
