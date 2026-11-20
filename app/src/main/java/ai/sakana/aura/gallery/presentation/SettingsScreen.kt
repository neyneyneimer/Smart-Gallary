package ai.sakana.aura.gallery.presentation

import android.content.ClipData
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import ai.sakana.aura.gallery.BuildConfig
import ai.sakana.aura.gallery.diagnostics.DiagnosticReportExporter
import ai.sakana.aura.gallery.design.AuraTokens
import ai.sakana.aura.gallery.design.AuraToneColorMatrix
import ai.sakana.aura.gallery.design.InstrumentSerif
import ai.sakana.aura.gallery.design.JetBrainsMono
import ai.sakana.aura.gallery.design.Manrope
import ai.sakana.aura.gallery.domain.model.MediaItem
import ai.sakana.aura.gallery.domain.recognition.AiSet
import ai.sakana.aura.gallery.domain.recognition.AiSetRules
import ai.sakana.aura.gallery.domain.recognition.DetectedFace
import ai.sakana.aura.gallery.domain.recognition.PersonCluster
import ai.sakana.aura.gallery.domain.recognition.SceneCluster
import ai.sakana.aura.gallery.platform.MediaAccessLevel
import ai.sakana.aura.gallery.platform.MediaPermissionPolicy
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import timber.log.Timber

@Composable
internal fun SettingsScreen(context: Context, items: List<MediaItem>) {
    val bytes = remember(items) { items.sumOf { it.sizeBytes.coerceAtLeast(0L) } }
    val database = remember { context.getDatabasePath("aura_gallery.db") }
    val databaseBytes = remember(database.lastModified()) {
        database.length() + java.io.File(database.path + "-wal").length()
    }
    Column(Modifier.fillMaxSize().background(AuraTokens.Void)) {
        PageHeader("Settings", "BUILD · ${BuildConfig.VERSION_NAME}")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            SettingsGroup("BACKUP")
            FeatureSettingRow(
                title = "Google Drive",
                hint = "No sign-in, upload or sync job is configured. Media remains on this device.",
                enabled = FeatureFlags.cloudBackupEnabled,
            )
            FeatureSettingRow(
                title = "MEGA",
                hint = "Cloud backup is not connected. Your media stays on this device.",
                enabled = FeatureFlags.megaEnabled,
            )
            FeatureSettingRow(
                title = "TeraBox",
                hint = "Cloud backup is not connected. Your media stays on this device.",
                enabled = FeatureFlags.teraboxEnabled,
            )
            SettingsRow("Wi-Fi only", "Available after a cloud backup provider is implemented.", "NOT SET UP")
            SettingsRow("Auto backup", "No photos or videos are being uploaded to the cloud.", "OFF")

            SettingsGroup("ON-DEVICE ANALYSIS")
            SettingsRow("Face grouping", "Face detection and licensed embeddings are not active.", "PLANNED M4")
            SettingsRow("Duplicate scan", "SHA-256 and dHash analysis has not run.", "PLANNED M2")

            if (BuildConfig.DEBUG) {
                SettingsGroup("DIAGNOSTICS")
                InfoRule(
                    title = "Export debug report",
                    message = "Share app logs and basic device details as a text file. Nothing is sent until you choose a sharing destination.",
                    modifier = Modifier.padding(vertical = 8.dp),
                    action = "SHARE REPORT",
                    onAction = { shareDiagnosticReport(context) },
                )
            }

            SettingsGroup("STORAGE")
            SettingsRow("Device library", items.size.toString() + " indexed photos/videos · " + Formatter.formatShortFileSize(context, bytes), "ON DEVICE")
            SettingsRow("Database", "Local media index", Formatter.formatShortFileSize(context, databaseBytes))
            SettingsRow("Export folder", "New edited JPEG copies are saved here.", "Pictures/Aura Edits")
            Spacer(Modifier.height(24.dp))
        }
    }
}


internal fun shareDiagnosticReport(context: Context) {
    try {
        val reportFile = DiagnosticReportExporter.create(context)
        val reportUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            reportFile,
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Aura AI Gallery diagnostic report")
            putExtra(Intent.EXTRA_TEXT, "Review the attached diagnostic report before sharing.")
            putExtra(Intent.EXTRA_STREAM, reportUri)
            clipData = ClipData.newUri(context.contentResolver, "Aura diagnostic report", reportUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share diagnostic report"))
    } catch (error: Exception) {
        Timber.e(error, "Unable to create or share diagnostic report")
    }
}

@Composable
private fun FeatureSettingRow(title: String, hint: String, enabled: Boolean) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp)
            .drawBehind {
                drawLine(AuraTokens.Hairline, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 1.dp.toPx())
            }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(hint, color = AuraTokens.PaperFaint, fontFamily = Manrope, fontSize = 10.sp, lineHeight = 15.sp)
            if (!enabled) Text("COMING SOON", color = AuraTokens.Safelight, fontFamily = JetBrainsMono, fontSize = 9.sp, letterSpacing = 0.7.sp)
        }
        Switch(checked = enabled, onCheckedChange = null, enabled = enabled)
    }
}
