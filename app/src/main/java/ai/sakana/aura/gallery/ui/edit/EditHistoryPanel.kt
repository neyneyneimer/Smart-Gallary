package ai.sakana.aura.gallery.ui.edit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.sakana.aura.gallery.data.local.EditHistoryEntity
import ai.sakana.aura.gallery.design.AuraTokens
import ai.sakana.aura.gallery.design.JetBrainsMono
import ai.sakana.aura.gallery.design.Manrope
import ai.sakana.aura.gallery.domain.editing.EditHistoryRules

/**
 * Edit stack shown below the tone sliders. Stateless: Account 1 mounts it inside AuraEditorScreen
 * (that composable is private in GalleryScreen.kt) and wires the callbacks to EditHistoryDao.
 *
 * Tap = apply, long-press = "Save as new", trash = delete (confirm; the active preset is blocked).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditHistoryPanel(
    presets: List<EditHistoryEntity>,
    onSaveCurrent: (name: String) -> Unit,
    onApply: (EditHistoryEntity) -> Unit,
    onBranch: (EditHistoryEntity, newName: String) -> Unit,
    onDelete: (EditHistoryEntity) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var saving by remember { mutableStateOf(false) }
    var branching by remember { mutableStateOf<EditHistoryEntity?>(null) }
    var deleting by remember { mutableStateOf<EditHistoryEntity?>(null) }

    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("EDIT HISTORY", Modifier.weight(1f), color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 1.5.sp)
            TextButton(onClick = onReset) { Text("RESET", color = AuraTokens.PaperMuted, fontFamily = JetBrainsMono, fontSize = 10.sp) }
            TextButton(onClick = { saving = true }) { Text("SAVE PRESET", color = AuraTokens.Safelight, fontFamily = JetBrainsMono, fontSize = 10.sp) }
        }
        if (presets.isEmpty()) {
            Text("No saved edits yet. Adjust the sliders, then save a preset.", color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 11.sp)
        } else {
            LazyColumn(Modifier.heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(presets, key = { it.id }) { preset ->
                    Row(
                        Modifier.fillMaxWidth().background(AuraTokens.Surface1)
                            .combinedClickable(onClick = { onApply(preset) }, onLongClick = { branching = preset })
                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                preset.name + if (preset.isActive) "  ·  ACTIVE" else "",
                                color = if (preset.isActive) AuraTokens.Safelight else AuraTokens.Paper,
                                fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                            )
                            Text(EditHistoryRules.summary(preset), color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 9.sp)
                        }
                        IconButton(onClick = { deleting = preset }) { Icon(Icons.Outlined.Delete, "Delete preset", tint = AuraTokens.PaperMuted) }
                    }
                }
            }
        }
    }

    if (saving) {
        NameDialog("Save preset", "Name", EditHistoryRules.defaultName(presets.size), "SAVE", { saving = false }) { onSaveCurrent(it); saving = false }
    }
    branching?.let { source ->
        NameDialog("Save as new", "Name", source.name + " copy", "SAVE", { branching = null }) { onBranch(source, it); branching = null }
    }
    deleting?.let { target ->
        val blocked = EditHistoryRules.deleteBlockedReason(target)
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = AuraTokens.Surface2,
            title = { Text(if (blocked == null) "Delete preset?" else "Can't delete yet", color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.Bold) },
            text = { Text(blocked ?: "\"" + target.name + "\" will be removed. Your original photo is not affected.", color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 12.sp) },
            confirmButton = {
                if (blocked == null) TextButton(onClick = { onDelete(target); deleting = null }) { Text("DELETE", color = AuraTokens.Safelight, fontFamily = JetBrainsMono, fontSize = 11.sp) }
                else TextButton(onClick = { deleting = null }) { Text("OK", color = AuraTokens.Safelight, fontFamily = JetBrainsMono, fontSize = 11.sp) }
            },
            dismissButton = if (blocked == null) {
                { TextButton(onClick = { deleting = null }) { Text("CANCEL", color = AuraTokens.PaperMuted, fontFamily = JetBrainsMono, fontSize = 11.sp) } }
            } else null,
        )
    }
}

@Composable
private fun NameDialog(title: String, label: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuraTokens.Surface2,
        title = { Text(title, color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.Bold) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it.take(EditHistoryRules.MAX_NAME_LENGTH) }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirm, color = AuraTokens.Safelight, fontFamily = JetBrainsMono, fontSize = 11.sp) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = AuraTokens.PaperMuted, fontFamily = JetBrainsMono, fontSize = 11.sp) } },
    )
}

@Preview(widthDp = 380)
@Composable
private fun EditHistoryPanelPreview() {
    val uri = "content://media/external/images/media/1"
    EditHistoryPanel(
        presets = listOf(
            EditHistoryEntity(1, uri, "Warm", brightness = 0.1f, warmth = 0.2f, appliedAt = 2L, isActive = true),
            EditHistoryEntity(2, uri, "Punchy", contrast = 0.4f, saturation = 0.2f, appliedAt = 1L),
        ),
        onSaveCurrent = {}, onApply = {}, onBranch = { _, _ -> }, onDelete = {}, onReset = {},
    )
}
