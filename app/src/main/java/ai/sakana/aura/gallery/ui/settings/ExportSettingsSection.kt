package ai.sakana.aura.gallery.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.sakana.aura.gallery.data.export.ExportLocation
import ai.sakana.aura.gallery.data.export.ExportSettings
import ai.sakana.aura.gallery.design.AuraTokens
import ai.sakana.aura.gallery.design.JetBrainsMono
import ai.sakana.aura.gallery.design.Manrope
import kotlin.math.roundToInt

/** "EXPORT" block for the Settings screen. SettingsScreen is private in GalleryScreen.kt; Account 1 embeds this. */
@Composable
fun ExportSettingsSection(settings: ExportSettings, onChange: (ExportSettings) -> Unit, modifier: Modifier = Modifier) {
    val isCustom = settings.relativePath != ExportLocation.DEFAULT && settings.relativePath != ExportLocation.AURA_GALLERY
    var customText by remember { mutableStateOf(if (isCustom) settings.relativePath else "Pictures/") }
    Column(modifier.fillMaxWidth()) {
        SectionLabel("EXPORT")
        Text("Quality " + settings.quality + "%", color = AuraTokens.Paper, fontFamily = Manrope, fontSize = 13.sp)
        Slider(
            value = settings.quality.toFloat(),
            onValueChange = { onChange(settings.copy(quality = it.roundToInt()).normalized()) },
            valueRange = ExportSettings.MIN_QUALITY.toFloat()..ExportSettings.MAX_QUALITY.toFloat(),
            steps = ExportSettings.MAX_QUALITY - ExportSettings.MIN_QUALITY - 1,
        )
        Text("Location", color = AuraTokens.Paper, fontFamily = Manrope, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        LocationRow("Pictures/Aura Edits (default)", settings.relativePath == ExportLocation.DEFAULT) { onChange(settings.copy(relativePath = ExportLocation.DEFAULT)) }
        LocationRow("Pictures/AuraGallery", settings.relativePath == ExportLocation.AURA_GALLERY) { onChange(settings.copy(relativePath = ExportLocation.AURA_GALLERY)) }
        LocationRow("Custom folder", isCustom) { onChange(settings.copy(relativePath = customText).normalized()) }
        if (isCustom) {
            OutlinedTextField(
                value = customText,
                onValueChange = { customText = it; onChange(settings.copy(relativePath = it).normalized()) },
                singleLine = true,
                label = { Text("Folder under Pictures/ or DCIM/") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Include edit history", color = AuraTokens.Paper, fontFamily = Manrope, fontSize = 13.sp)
                Text("Writes preset and parameters into the photo's EXIF comment.", color = AuraTokens.PaperFaint, fontFamily = Manrope, fontSize = 10.sp)
            }
            Switch(checked = settings.includeEditHistory, onCheckedChange = { onChange(settings.copy(includeEditHistory = it)) })
        }
    }
}

@Composable
private fun LocationRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 12.sp)
    }
}

@Composable
internal fun SectionLabel(title: String) {
    Text(title, Modifier.padding(top = 18.dp, bottom = 4.dp), color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 1.5.sp)
}

@Preview(widthDp = 380)
@Composable
private fun ExportSettingsSectionPreview() {
    ExportSettingsSection(ExportSettings(), onChange = {})
}
