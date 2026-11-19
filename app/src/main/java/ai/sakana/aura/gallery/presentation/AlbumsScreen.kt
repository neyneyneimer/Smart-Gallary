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
internal fun AlbumsScreen(
    items: List<MediaItem>,
    onOpenAlbum: (ai.sakana.aura.gallery.domain.grouping.MediaAlbum) -> Unit,
    onRecentlyDeleted: () -> Unit,
    deletedCount: Int,
    onMore: () -> Unit,
) {
    val albums = remember(items) { ai.sakana.aura.gallery.domain.grouping.GalleryCollectionGrouping.buildAlbums(items) }
    Column(Modifier.fillMaxSize().background(AuraTokens.Void)) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Albums", Modifier.weight(1f), color = AuraTokens.Paper, fontFamily = Manrope, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onMore) { Icon(Icons.Outlined.MoreVert, "More options", tint = AuraTokens.PaperMuted) }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(albums, key = { album -> (if (album.isVirtual) "virtual:" else "folder:") + (album.items.firstOrNull()?.relativePath?.ifBlank { album.title } ?: album.title) }) { album ->
                Column(Modifier.clickable { onOpenAlbum(album) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(3.dp)).background(AuraTokens.Surface1)) {
                        album.items.firstOrNull()?.let { cover ->
                            AsyncImage(model = cover.contentUri.toUri(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        if (album.title == "Videos") Icon(Icons.Outlined.PlayCircleOutline, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(22.dp))
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(album.title, color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${album.items.size} items", color = AuraTokens.PaperFaint, fontFamily = Manrope, fontSize = 10.sp)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(AuraTokens.Surface1)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onRecentlyDeleted).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.DeleteOutline, null, tint = AuraTokens.PaperMuted)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("Recently deleted", color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("$deletedCount items · recoverable in Aura", color = AuraTokens.PaperFaint, fontFamily = Manrope, fontSize = 10.sp)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = AuraTokens.PaperFaint)
            }
        }
    }
}

@Composable
internal fun AlbumDetailScreen(album: ai.sakana.aura.gallery.domain.grouping.MediaAlbum, onBack: () -> Unit, onOpen: (MediaItem) -> Unit) {
    Column(Modifier.fillMaxSize().background(AuraTokens.Void)) {
        Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to albums", tint = AuraTokens.Paper) }
            Column(Modifier.weight(1f)) {
                Text(album.title, color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text("${album.items.size} items", color = AuraTokens.PaperFaint, fontFamily = Manrope, fontSize = 10.sp)
            }
        }
        if (album.items.isEmpty()) EmptyIndexState("No media yet", "Items saved into this folder will appear here.")
        else DateGroupedMediaGrid(album.items, onOpen = onOpen)
    }
}
