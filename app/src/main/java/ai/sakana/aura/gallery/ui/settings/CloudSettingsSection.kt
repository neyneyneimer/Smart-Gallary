package ai.sakana.aura.gallery.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.sakana.aura.gallery.data.cloud.CloudProviderType
import ai.sakana.aura.gallery.data.cloud.ConflictMode
import ai.sakana.aura.gallery.design.AuraTokens
import ai.sakana.aura.gallery.design.JetBrainsMono
import ai.sakana.aura.gallery.design.Manrope
import java.text.DateFormat
import java.util.Date

data class ProviderRowState(
    val type: CloudProviderType,
    /** False = no working implementation yet; shown as "COMING SOON" and cannot be selected. */
    val isAvailable: Boolean,
    val isConnected: Boolean = false,
    val lastSyncEpochMillis: Long = 0L,
    val syncedCount: Int = 0,
    val lastError: String? = null,
)

data class CloudSettingsUiState(
    val providers: List<ProviderRowState>,
    val primary: CloudProviderType = CloudProviderType.NONE,
    val conflictMode: ConflictMode = ConflictMode.KEEP_DEVICE,
    val wifiOnly: Boolean = true,
    val autoBackup: Boolean = false,
    val isSyncing: Boolean = false,
)

/** "CLOUD BACKUP" block for Settings. Honest by construction: unavailable providers are labelled, never faked. */
@Composable
fun CloudSettingsSection(
    state: CloudSettingsUiState,
    onSelectPrimary: (CloudProviderType) -> Unit,
    onConnect: (CloudProviderType) -> Unit,
    onDisconnect: (CloudProviderType) -> Unit,
    onSyncNow: () -> Unit,
    onConflictMode: (ConflictMode) -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onAutoBackup: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        SectionLabel("CLOUD BACKUP")
        state.providers.forEach { row ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(AuraTokens.Surface1).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = state.primary == row.type, onClick = { onSelectPrimary(row.type) }, enabled = row.isAvailable)
                    Column(Modifier.weight(1f)) {
                        Text(row.type.displayName, color = if (row.isAvailable) AuraTokens.Paper else AuraTokens.PaperFaint, fontFamily = Manrope, fontSize = 13.sp)
                        Text(statusLine(row), color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 9.sp)
                    }
                    if (row.isAvailable) {
                        TextButton(onClick = { if (row.isConnected) onDisconnect(row.type) else onConnect(row.type) }) {
                            Text(if (row.isConnected) "DISCONNECT" else "CONNECT", color = AuraTokens.Safelight, fontFamily = JetBrainsMono, fontSize = 10.sp)
                        }
                    }
                }
                row.lastError?.let { Text(it, color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 10.sp, modifier = Modifier.padding(start = 12.dp)) }
            }
        }
        TextButton(onClick = onSyncNow, enabled = !state.isSyncing && state.primary != CloudProviderType.NONE) {
            Text(if (state.isSyncing) "SYNCING…" else "SYNC NOW", color = AuraTokens.Safelight, fontFamily = JetBrainsMono, fontSize = 11.sp)
        }
        Text("When files differ", color = AuraTokens.Paper, fontFamily = Manrope, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        listOf(
            ConflictMode.KEEP_DEVICE to "Keep device version (default)",
            ConflictMode.KEEP_CLOUD to "Keep cloud version",
            ConflictMode.KEEP_BOTH to "Keep both",
            ConflictMode.ASK to "Ask me each time",
        ).forEach { (mode, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = state.conflictMode == mode, onClick = { onConflictMode(mode) })
                Text(label, color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 12.sp)
            }
        }
        ToggleRow("Wi-Fi only", "Large uploads wait for an unmetered network.", state.wifiOnly, onWifiOnly)
        ToggleRow("Automatic daily backup", "Runs around 2 AM when conditions allow.", state.autoBackup, onAutoBackup)
    }
}

private fun statusLine(row: ProviderRowState): String = when {
    !row.isAvailable -> "COMING SOON"
    !row.isConnected -> "NOT CONNECTED"
    row.lastSyncEpochMillis > 0L -> "LAST SYNC " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(row.lastSyncEpochMillis)) + " · " + row.syncedCount + " ITEMS"
    else -> "CONNECTED · NEVER SYNCED"
}

@Composable
private fun ToggleRow(title: String, caption: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AuraTokens.Paper, fontFamily = Manrope, fontSize = 13.sp)
            Text(caption, color = AuraTokens.PaperFaint, fontFamily = Manrope, fontSize = 10.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Preview(widthDp = 380)
@Composable
private fun CloudSettingsSectionPreview() {
    CloudSettingsSection(
        state = CloudSettingsUiState(
            providers = listOf(
                ProviderRowState(CloudProviderType.GOOGLE_DRIVE, true, true, 1_700_000_000_000, 1204),
                ProviderRowState(CloudProviderType.MEGA, false),
                ProviderRowState(CloudProviderType.TERABOX, false),
            ),
            primary = CloudProviderType.GOOGLE_DRIVE,
        ),
        onSelectPrimary = {}, onConnect = {}, onDisconnect = {}, onSyncNow = {}, onConflictMode = {}, onWifiOnly = {}, onAutoBackup = {},
    )
}
