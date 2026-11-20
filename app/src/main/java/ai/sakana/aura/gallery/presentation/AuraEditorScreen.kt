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
internal fun AuraEditorScreen(item: MediaItem, onBack: () -> Unit, onSaveCopy: (ColorMatrix) -> Unit) {
    var brightness by remember(item.contentUri) { mutableFloatStateOf(0f) }
    var contrast by remember(item.contentUri) { mutableFloatStateOf(0f) }
    var saturation by remember(item.contentUri) { mutableFloatStateOf(0f) }
    var warmth by remember(item.contentUri) { mutableFloatStateOf(0f) }
    var compareOriginal by remember { mutableStateOf(false) }
    var showAiEditInfo by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val matrix = remember(brightness, contrast, saturation, warmth) {
        AuraToneColorMatrix.create(brightness, contrast, saturation, warmth)
    }
    Column(Modifier.fillMaxSize().background(AuraTokens.Void)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to library", tint = AuraTokens.Paper) }
            Column(Modifier.weight(1f)) {
                Text(item.displayName ?: "Photo", color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.width.toString() + " × " + item.height + " · " + Formatter.formatShortFileSize(context, item.sizeBytes),
                    color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 10.sp,
                )
            }
        }
        Box(Modifier.fillMaxWidth().weight(0.58f).background(Color.Black)) {
            AsyncImage(
                model = item.contentUri.toUri(),
                contentDescription = "Photo preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = if (compareOriginal) null else ColorFilter.colorMatrix(matrix),
            )
            Text(
                if (compareOriginal) "ORIGINAL" else "PREVIEW",
                Modifier.align(Alignment.TopStart).padding(16.dp)
                    .background(Color(0x99000000), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
                color = AuraTokens.Paper, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 1.sp,
            )
        }
        Column(
            Modifier.fillMaxWidth().weight(0.42f).background(AuraTokens.Surface1).padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(3.dp).background(AuraTokens.HairlineStrong, CircleShape))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Tone", color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("ORIGINAL PRESERVED", color = AuraTokens.Safelight, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 0.4.sp)
            }
            ToneSlider("Brightness", brightness) { brightness = it }
            ToneSlider("Contrast", contrast) { contrast = it }
            ToneSlider("Saturation", saturation) { saturation = it }
            ToneSlider("Warmth", warmth) { warmth = it }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ORIGINAL", color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 10.sp)
                Text("LIVE PREVIEW", color = AuraTokens.Safelight, fontFamily = JetBrainsMono, fontSize = 10.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) {
                    HoldCompareButton("Hold to compare", { compareOriginal = true }, { compareOriginal = false })
                }
                Box(Modifier.weight(1f)) {
                    HairlineButton("Save copy", { onSaveCopy(matrix) }, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(6.dp))
            HairlineButton("AI Edit", { showAiEditInfo = true }, modifier = Modifier.fillMaxWidth())
        }
    }
    if (showAiEditInfo) {
        AlertDialog(
            onDismissRequest = { showAiEditInfo = false },
            title = { Text("AI Edit is not connected") },
            text = { Text("The AI editing model and processing service have not been configured yet. This action does not upload your photo or change the original.") },
            confirmButton = { TextButton(onClick = { showAiEditInfo = false }) { Text("OK") } },
        )
    }
}

@Composable
internal fun ToneSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 11.sp, modifier = Modifier.width(82.dp))
        HairlineSlider(value, onValueChange, Modifier.weight(1f).height(48.dp))
        Text(
            (if (value > 0f) "+" else "") + String.format(Locale.US, "%.2f", value),
            color = AuraTokens.Paper,
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
internal fun HairlineSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    fun fromX(x: Float, width: Float): Float {
        val fraction = (x / width).coerceIn(0f, 1f)
        return ((if (rtl) 1f - fraction else fraction) * 2f - 1f).coerceIn(-1f, 1f)
    }
    Canvas(
        modifier.pointerInput(rtl) {
            detectTapGestures { onValueChange(fromX(it.x, size.width.toFloat())) }
        }.pointerInput(rtl) {
            detectDragGestures { change, _ ->
                change.consume()
                onValueChange(fromX(change.position.x, size.width.toFloat()))
            }
        },
    ) {
        val y = size.height / 2f
        val zero = size.width / 2f
        val visualValue = if (rtl) -value else value
        val knob = (visualValue + 1f) * 0.5f * size.width
        drawLine(AuraTokens.HairlineStrong, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        drawLine(AuraTokens.Safelight, Offset(zero, y), Offset(knob, y), 1.dp.toPx())
        drawLine(AuraTokens.PaperFaint, Offset(zero, y - 5.dp.toPx()), Offset(zero, y + 5.dp.toPx()), 1.dp.toPx())
        drawCircle(AuraTokens.Paper, 5.dp.toPx(), Offset(knob, y))
    }
}

@Composable
internal fun HoldCompareButton(text: String, onPressed: () -> Unit, onReleased: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(48.dp).border(1.dp, AuraTokens.HairlineStrong, RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onPressed()
                    tryAwaitRelease()
                    onReleased()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    }
}

internal fun buildToneColorMatrix(brightness: Float, contrast: Float, saturation: Float, warmth: Float): ColorMatrix {
    fun identity() = Array(5) { row -> FloatArray(5) { col -> if (row == col) 1f else 0f } }
    fun multiply(a: Array<FloatArray>, b: Array<FloatArray>) = Array(5) { row ->
        FloatArray(5) { col -> (0..4).sumOf { k -> (a[row][k] * b[k][col]).toDouble() }.toFloat() }
    }
    val sat = identity()
    val s = (1f + saturation * 0.4f).coerceAtLeast(0f)
    val r = 0.213f * (1f - s)
    val g = 0.715f * (1f - s)
    val b = 0.072f * (1f - s)
    sat[0][0] = r + s; sat[0][1] = g; sat[0][2] = b
    sat[1][0] = r; sat[1][1] = g + s; sat[1][2] = b
    sat[2][0] = r; sat[2][1] = g; sat[2][2] = b + s

    val ctr = identity()
    val c = (1f + contrast * 0.45f).coerceAtLeast(0.05f)
    for (i in 0..2) { ctr[i][i] = c; ctr[i][4] = 0.5f * (1f - c) * 255f }

    val warm = identity()
    warm[0][0] = 1f + warmth * 0.12f
    warm[2][2] = 1f - warmth * 0.12f

    val bright = identity()
    val offset = brightness * 0.16f * 255f
    for (i in 0..2) bright[i][4] = offset

    val matrix = multiply(bright, multiply(warm, multiply(ctr, sat)))
    val values = FloatArray(20)
    for (row in 0..3) for (col in 0..4) values[row * 5 + col] = matrix[row][col]
    return ColorMatrix(values)
}
