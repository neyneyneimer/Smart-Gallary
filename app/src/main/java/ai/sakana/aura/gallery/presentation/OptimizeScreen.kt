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
internal fun OptimizeScreen(items: List<MediaItem>) {
    val context = LocalContext.current
    val totalBytes = remember(items) { items.sumOf { it.sizeBytes.coerceAtLeast(0L) } }
    Column(Modifier.fillMaxSize().background(AuraTokens.Void)) {
        PageHeader("Optimize", "ANALYSIS · NOT YET RUN")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard("Indexed device media") {
                Text(Formatter.formatShortFileSize(context, totalBytes), color = AuraTokens.Paper, fontFamily = InstrumentSerif, fontSize = 44.sp, lineHeight = 48.sp)
                Text(items.size.toString() + " PHOTOS + VIDEOS · CURRENTLY INDEXED", color = AuraTokens.PaperMuted, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(13.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).background(AuraTokens.Surface3)) {
                    Box(Modifier.fillMaxWidth().height(3.dp).background(AuraTokens.PaperMuted))
                }
                Spacer(Modifier.height(6.dp))
                Text("DEVICE MEDIA INDEX · ORIGINAL FILES PRESERVED", color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 10.sp)
            }
            MetricCard("Identical · SHA-256") {
                ComingSoonBadge()
                Text("No duplicate report", color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text("Exact-duplicate scanning has not run. No files are marked for deletion.", color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 12.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(10.dp))
                Text("NO DELETE ACTIONS · SCAN ENGINE PLANNED", color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 10.sp)
            }
            MetricCard("Similar · dHash threshold 5/64") {
                ComingSoonBadge()
                Text("No similarity report", color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text("Perceptual matching and burst grouping are planned for a later milestone. No duplicate scan is being performed from this screen.", color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 12.sp, lineHeight = 19.sp)
            }
            MetricCard("Video frame analysis") {
                if (FeatureFlags.videoFrameAnalysisEnabled) {
                    Text("Enabled", color = AuraTokens.Paper, fontFamily = Manrope, fontSize = 12.sp)
                } else {
                    ComingSoonBadge()
                    Text("Video-frame storage analysis is not available yet. No background video analysis is started here.", color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 12.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}

@Composable
private fun ComingSoonBadge() {
    Text(
        "COMING SOON",
        modifier = Modifier.padding(bottom = 8.dp)
            .background(AuraTokens.Safelight.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        color = AuraTokens.Safelight,
        fontFamily = JetBrainsMono,
        fontSize = 9.sp,
        letterSpacing = 0.7.sp,
    )
}
