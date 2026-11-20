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
internal fun MediaViewerScreen(
    item: MediaItem,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    var moreOpen by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).background(Color(0xFF171717)).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to gallery", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(dateHeading(java.time.Instant.ofEpochMilli(item.dateTakenEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()), color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                val timestamp = DateTimeFormatter.ofPattern("h:mm a", locale).withZone(ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(item.dateTakenEpochMillis))
                Text("$timestamp · ${if (item.isVideo) "Video" else "Photo"}", color = Color.LightGray, fontFamily = Manrope, fontSize = 10.sp)
            }
            IconButton(onClick = { showDetails = true }) { Icon(Icons.Outlined.Info, "Media details", tint = Color.White) }
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black), contentAlignment = Alignment.Center) {
            if (item.isVideo) {
                key(item.contentUri) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            android.widget.VideoView(viewContext).apply {
                                val controller = android.widget.MediaController(viewContext)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                                setVideoURI(item.contentUri.toUri())
                                setOnPreparedListener { player -> player.isLooping = false; start() }
                            }
                        },
                    )
                }
            } else {
                AsyncImage(model = item.contentUri.toUri(), contentDescription = item.displayName ?: "Photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        Row(
            Modifier.fillMaxWidth().height(76.dp).background(Color(0xFF1D1D1D)).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ViewerAction(Icons.Outlined.Share, "Share", onShare)
            ViewerAction(if (item.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Favorite", onFavorite)
            ViewerAction(Icons.Outlined.Edit, "Edit", onEdit, enabled = !item.isVideo)
            ViewerAction(Icons.Outlined.DeleteOutline, "Delete", onDelete)
            Box {
                ViewerAction(Icons.Outlined.MoreVert, "More", onClick = { moreOpen = true })
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                    DropdownMenuItem(text = { Text("Details") }, onClick = { showDetails = true; moreOpen = false })
                    DropdownMenuItem(text = { Text("Open in another app") }, onClick = {
                        moreOpen = false
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(item.contentUri.toUri(), item.mimeType ?: if (item.isVideo) "video/*" else "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        }
                    })
                }
            }
        }
    }
    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text("Media details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.displayName ?: "Untitled media")
                    Text("Folder: ${item.bucketName}")
                    Text("Type: ${item.mimeType ?: if (item.isVideo) "Video" else "Image"}")
                    Text("Size: ${Formatter.formatShortFileSize(context, item.sizeBytes)}")
                    if (item.width > 0 && item.height > 0) Text("Dimensions: ${item.width} × ${item.height}")
                    if (item.isVideo) Text("Duration: ${durationLabel(item.durationMillis)}")
                    Text("Stored on this device")
                }
            },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text("Close") } },
        )
    }
}

@Composable
internal fun ViewerAction(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Column(
        Modifier.width(64.dp).fillMaxHeight().clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.35f), fontFamily = Manrope, fontSize = 9.sp)
    }
}
