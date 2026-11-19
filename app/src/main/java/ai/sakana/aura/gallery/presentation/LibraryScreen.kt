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
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import ai.sakana.aura.gallery.diagnostics.PerformanceMetrics

internal enum class LibraryFilter { ALL, PHOTOS, VIDEOS, FAVORITES }

@Composable
internal fun LibraryScreen(
    items: List<MediaItem>,
    access: MediaAccessLevel,
    accessPermanentlyDenied: Boolean = false,
    refreshing: Boolean,
    error: String?,
    selectedUris: Set<String>,
    filter: LibraryFilter,
    onRefresh: () -> Unit,
    onFilter: (LibraryFilter) -> Unit,
    onOpenViewer: (MediaItem) -> Unit,
    onToggleSelected: (MediaItem) -> Unit,
    onShareSelected: (List<MediaItem>) -> Unit,
    onFavoriteSelected: (List<MediaItem>) -> Unit,
    onDeleteSelected: (List<MediaItem>) -> Unit,
    onChooseMedia: () -> Unit,
    onRequestAccess: () -> Unit,
    onDismissError: () -> Unit,
    onMore: () -> Unit,
) {
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filterMenuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = "Retry",
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) onRefresh()
        onDismissError()
    }
    LaunchedEffect(items.isNotEmpty()) {
        if (items.isNotEmpty()) PerformanceMetrics.recordFirstGalleryContent(items.size)
    }
    val filtered = remember(items, query, filter) {
        items.filter { item ->
            val matchesQuery = query.isBlank() || item.displayName.orEmpty().contains(query, true) || item.bucketName.contains(query, true)
            val matchesType = when (filter) {
                LibraryFilter.ALL -> true
                LibraryFilter.PHOTOS -> !item.isVideo
                LibraryFilter.VIDEOS -> item.isVideo
                LibraryFilter.FAVORITES -> item.isFavorite
            }
            matchesQuery && matchesType
        }.sortedByDescending(MediaItem::dateTakenEpochMillis)
    }
    Box(Modifier.fillMaxSize().background(AuraTokens.Void)) {
        Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(start = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Aura", Modifier.weight(1f), color = AuraTokens.Paper, fontFamily = InstrumentSerif, fontSize = 27.sp, lineHeight = 32.sp)
            IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) {
                Icon(if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search, "Search media", tint = AuraTokens.PaperMuted)
            }
            Box {
                IconButton(onClick = { filterMenuOpen = true }) { Icon(Icons.Outlined.FilterList, "Filter media", tint = AuraTokens.PaperMuted) }
                DropdownMenu(expanded = filterMenuOpen, onDismissRequest = { filterMenuOpen = false }) {
                    listOf(LibraryFilter.ALL to "All media", LibraryFilter.PHOTOS to "Photos", LibraryFilter.VIDEOS to "Videos", LibraryFilter.FAVORITES to "Favorites").forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { onFilter(value); filterMenuOpen = false })
                    }
                }
            }
            IconButton(onClick = onMore) { Icon(Icons.Outlined.MoreVert, "More options", tint = AuraTokens.PaperMuted) }
        }
        if (searchOpen) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                placeholder = { Text("Times, folders, file names…") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AuraTokens.Paper,
                    unfocusedTextColor = AuraTokens.Paper,
                    focusedBorderColor = AuraTokens.Safelight,
                    unfocusedBorderColor = AuraTokens.Hairline,
                    cursorColor = AuraTokens.Safelight,
                ),
            )
        }
        if (access == MediaAccessLevel.PARTIAL) {
            InfoRule(
                title = "Limited media access",
                message = "Aura can only show the photos and videos Android has allowed. You can manage access at any time.",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                action = "MANAGE ACCESS",
                onAction = onRequestAccess,
            )
        }
        when {
            access == MediaAccessLevel.DENIED && items.isEmpty() -> FirstRunLibrary(
                onChooseMedia,
                onRequestAccess,
                accessPermanentlyDenied = accessPermanentlyDenied,
            )
            filtered.isEmpty() && refreshing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("SCANNING DEVICE LIBRARY", color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 10.sp)
            }
            filtered.isEmpty() -> EmptyGallery(onChooseMedia)
            else -> DateGroupedMediaGrid(
                items = filtered,
                refreshing = refreshing,
                onRefresh = onRefresh,
                selectedUris = selectedUris,
                onOpen = onOpenViewer,
                onToggleSelected = onToggleSelected,
            )
        }
        if (selectedUris.isNotEmpty()) {
            val selectedItems = items.filter { it.contentUri in selectedUris }
            Row(
                Modifier.fillMaxWidth().background(AuraTokens.Surface2).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${selectedItems.size} selected", color = AuraTokens.Paper, fontFamily = Manrope, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onShareSelected(selectedItems) }) { Text("Share") }
                    TextButton(onClick = { onFavoriteSelected(selectedItems) }) { Text("Favorite") }
                    TextButton(onClick = { onDeleteSelected(selectedItems) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun DateGroupedMediaGrid(
    items: List<MediaItem>,
    selectedUris: Set<String> = emptySet(),
    onOpen: (MediaItem) -> Unit,
    onToggleSelected: (MediaItem) -> Unit = {},
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    val pullDistance = remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val refreshThreshold = with(density) { 72.dp.toPx() }
    val maxPullDistance = with(density) { 160.dp.toPx() }
    val pullConnection = remember(refreshing, onRefresh, refreshThreshold, maxPullDistance) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!refreshing && gridState.firstVisibleItemIndex == 0 &&
                    gridState.firstVisibleItemScrollOffset == 0 &&
                    source == NestedScrollSource.UserInput && available.y > 0f
                ) {
                    pullDistance.floatValue = (pullDistance.floatValue + available.y).coerceIn(0f, maxPullDistance)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val shouldRefresh = !refreshing && pullDistance.floatValue >= refreshThreshold
                pullDistance.floatValue = 0f
                if (shouldRefresh) onRefresh()
                return Velocity.Zero
            }
        }
    }
    LaunchedEffect(refreshing) {
        if (refreshing) pullDistance.floatValue = 0f
    }
    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) PerformanceMetrics.startScrollFrameMonitor()
        else PerformanceMetrics.stopScrollFrameMonitor()
    }
    DisposableEffect(Unit) {
        onDispose { PerformanceMetrics.stopScrollFrameMonitor() }
    }
    val groups = remember(items) {
        items.sortedByDescending(MediaItem::dateTakenEpochMillis).groupBy { media ->
            java.time.Instant.ofEpochMilli(media.dateTakenEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }
    Box(Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().background(AuraTokens.Hairline).nestedScroll(pullConnection),
        contentPadding = PaddingValues(bottom = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        groups.forEach { (date, dayItems) ->
            item(key = "date-${date}", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.fillMaxWidth().background(AuraTokens.Void).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(dateHeading(date), color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(dayItems.size.toString(), color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 10.sp)
                }
            }
            items(dayItems, key = { it.contentUri }) { item ->
                MediaGridTile(
                    item = item,
                    selected = item.contentUri in selectedUris,
                    onClick = { if (selectedUris.isNotEmpty()) onToggleSelected(item) else onOpen(item) },
                    onLongClick = { onToggleSelected(item) },
                )
            }
        }
    }
    if (refreshing) {
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                .background(AuraTokens.Surface2, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = AuraTokens.Safelight,
                )
                Text("REFRESHING LIBRARY", color = AuraTokens.Safelight, fontFamily = JetBrainsMono, fontSize = 9.sp)
            }
        }
    }
    }
}

internal fun dateHeading(date: java.time.LocalDate): String {
    val today = java.time.LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MediaGridTile(item: MediaItem, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).background(AuraTokens.Surface1)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .drawBehind { if (selected) drawRect(AuraTokens.Safelight, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())) },
    ) {
        if (item.isVideo) {
            VideoFrameThumbnail(item = item, modifier = Modifier.fillMaxSize())
        } else {
            AsyncImage(
                model = item.contentUri.toUri(),
                contentDescription = item.displayName ?: "Photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        if (item.isVideo) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(5.dp).clip(RoundedCornerShape(5.dp))
                    .background(Color(0xCC000000)).padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(durationLabel(item.durationMillis), color = Color.White, fontFamily = JetBrainsMono, fontSize = 9.sp)
            }
        } else if (item.isFavorite) {
            Icon(Icons.Outlined.Favorite, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(15.dp))
        }
        if (selected) {
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).background(AuraTokens.Safelight, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Check, null, tint = AuraTokens.InkOnAmber, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
internal fun VideoFrameThumbnail(item: MediaItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = item.contentUri) {
        value = withContext(Dispatchers.IO) { decodeVideoFrame(context, item.contentUri.toUri()) }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = item.displayName ?: "Video thumbnail", modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(AuraTokens.Surface2), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Videocam, null, tint = AuraTokens.PaperFaint, modifier = Modifier.size(30.dp))
        }
    }
}

internal fun decodeVideoFrame(context: Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        if (Build.VERSION.SDK_INT >= 27) {
            retriever.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 640, 640)
        } else {
            retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.let { frame ->
                    val scale = minOf(1f, 640f / maxOf(frame.width, frame.height))
                    if (scale < 1f) Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt().coerceAtLeast(1), (frame.height * scale).toInt().coerceAtLeast(1), true).also { frame.recycle() }
                    else frame
                }
        }
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

internal fun durationLabel(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
