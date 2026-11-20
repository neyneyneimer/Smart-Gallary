package ai.sakana.aura.gallery.ui.cloud

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import ai.sakana.aura.gallery.data.cloud.ConflictMode
import ai.sakana.aura.gallery.data.cloud.ConflictType
import ai.sakana.aura.gallery.data.cloud.PendingConflict
import ai.sakana.aura.gallery.design.AuraTokens
import ai.sakana.aura.gallery.design.JetBrainsMono
import ai.sakana.aura.gallery.design.Manrope
import coil3.compose.AsyncImage

/**
 * Shown for one conflict at a time when the user chose "Ask me each time". The device side shows a
 * thumbnail; the cloud side shows file details only (a thumbnail would require downloading the file).
 * Nothing is deleted permanently by any choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictResolutionSheet(
    conflict: PendingConflict,
    onChoose: (ConflictMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AuraTokens.Surface2) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title(conflict.type), color = AuraTokens.Paper, fontFamily = Manrope, fontSize = 15.sp)
            Text(explanation(conflict.type), color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                conflict.local?.let { local ->
                    Column(Modifier.weight(1f).background(AuraTokens.Surface1).padding(8.dp)) {
                        Text("THIS DEVICE", color = AuraTokens.Safelight, fontFamily = JetBrainsMono, fontSize = 9.sp)
                        AsyncImage(local.contentUri.toUri(), local.displayName, Modifier.fillMaxWidth().height(110.dp), contentScale = ContentScale.Crop)
                        Text(local.displayName, color = AuraTokens.Paper, fontFamily = Manrope, fontSize = 11.sp, maxLines = 1)
                        Text(Formatter.formatShortFileSize(context, local.sizeBytes), color = AuraTokens.PaperMuted, fontFamily = JetBrainsMono, fontSize = 9.sp)
                    }
                }
                conflict.entry?.let { cloud ->
                    Column(Modifier.weight(1f).background(AuraTokens.Surface1).padding(8.dp)) {
                        Text("CLOUD", color = AuraTokens.AuraA, fontFamily = JetBrainsMono, fontSize = 9.sp)
                        Text(cloud.displayName, color = AuraTokens.Paper, fontFamily = Manrope, fontSize = 11.sp, maxLines = 2, modifier = Modifier.padding(vertical = 4.dp))
                        Text(Formatter.formatShortFileSize(context, cloud.sizeBytes), color = AuraTokens.PaperMuted, fontFamily = JetBrainsMono, fontSize = 9.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onChoose(ConflictMode.KEEP_DEVICE) }) { Text("KEEP DEVICE", color = AuraTokens.Safelight, fontFamily = JetBrainsMono, fontSize = 10.sp) }
                TextButton(onClick = { onChoose(ConflictMode.KEEP_CLOUD) }) { Text("KEEP CLOUD", color = AuraTokens.Safelight, fontFamily = JetBrainsMono, fontSize = 10.sp) }
                TextButton(onClick = { onChoose(ConflictMode.KEEP_BOTH) }) { Text("KEEP BOTH", color = AuraTokens.Safelight, fontFamily = JetBrainsMono, fontSize = 10.sp) }
            }
            TextButton(onClick = onDismiss) { Text("DECIDE LATER", color = AuraTokens.PaperMuted, fontFamily = JetBrainsMono, fontSize = 10.sp) }
        }
    }
}

private fun title(type: ConflictType) = when (type) {
    ConflictType.SAME_NAME_DIFFERENT_HASH -> "Same name, different content"
    ConflictType.SAME_HASH_DIFFERENT_NAME -> "Same photo, different name"
    ConflictType.DELETED_LOCALLY_EXISTS_IN_CLOUD -> "Removed here, still in the cloud"
    ConflictType.DELETED_IN_CLOUD_EXISTS_LOCALLY -> "Removed from the cloud, still here"
}

private fun explanation(type: ConflictType) = when (type) {
    ConflictType.SAME_NAME_DIFFERENT_HASH -> "Keep device uploads this file over the cloud copy. Keep cloud downloads the cloud copy and moves yours to Trash. Keep both renames the cloud copy with _cloud."
    ConflictType.SAME_HASH_DIFFERENT_NAME -> "The content is identical. Choose which name to use; nothing is lost either way."
    ConflictType.DELETED_LOCALLY_EXISTS_IN_CLOUD -> "Keep device leaves the cloud copy alone. Keep cloud or both restores it to this device."
    ConflictType.DELETED_IN_CLOUD_EXISTS_LOCALLY -> "Keep device or both uploads this file again. Keep cloud moves it to Trash on this device."
}
