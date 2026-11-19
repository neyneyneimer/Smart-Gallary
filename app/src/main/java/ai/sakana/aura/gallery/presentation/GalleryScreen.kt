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
import ai.sakana.aura.gallery.domain.recognition.SceneCluster
import ai.sakana.aura.gallery.platform.MediaAccessLevel
import ai.sakana.aura.gallery.platform.MediaPermissionPolicy
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import timber.log.Timber

private enum class Destination { PHOTOS, ALBUMS, EXPLORE, PEOPLE, AI_SETS, OPTIMIZE, SETTINGS }
private enum class LibraryFilter { ALL, PHOTOS, VIDEOS, FAVORITES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryRoute(viewModel: GalleryViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var access by remember { mutableStateOf(readMediaAccess(context)) }
    val items by viewModel.mediaItems.collectAsState()
    val trashedItems by viewModel.trashedItems.collectAsState()
    val refreshing by viewModel.isRefreshing.collectAsState()
    val error by viewModel.errorMessage.collectAsState()
    val sceneClusters by viewModel.sceneClusters.collectAsState()
    val savedAiSets by viewModel.aiSets.collectAsState()
    val detectedFaces by viewModel.detectedFaces.collectAsState()
    val recognitionProgress by viewModel.recognitionProgress.collectAsState()
    var destination by remember { mutableStateOf(Destination.PHOTOS) }
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activeAlbum by remember { mutableStateOf<ai.sakana.aura.gallery.domain.grouping.MediaAlbum?>(null) }
    var showRecentlyDeleted by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<MediaItem?>(null) }
    var editing by remember { mutableStateOf<MediaItem?>(null) }
    var pendingTrashUris by remember { mutableStateOf<List<String>?>(null) }
    var moreOpen by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50),
        viewModel::addPickedMedia,
    )
    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
        { access = readMediaAccess(context) },
    )
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) access = readMediaAccess(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(access) {
        if (access != MediaAccessLevel.DENIED) viewModel.refresh()
    }

    val chooseMedia = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }
    val requestAccess = {
        permissionRequest.launch(MediaPermissionPolicy.requiredPermissions(Build.VERSION.SDK_INT).toTypedArray())
    }

    if (pendingTrashUris != null) {
        AlertDialog(
            onDismissRequest = { pendingTrashUris = null },
            title = { Text("Move to Recently Deleted?") },
            text = { Text("This removes the item from Aura’s main library. The original file stays in its device folder and can be restored here.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingTrashUris.orEmpty().forEach(viewModel::moveToTrash)
                    pendingTrashUris = null
                    selected = emptySet()
                    viewing = null
                }) { Text("Move to Recently Deleted") }
            },
            dismissButton = { TextButton(onClick = { pendingTrashUris = null }) { Text("Cancel") } },
        )
    }

    if (editing != null) {
        BackHandler { editing = null }
        AuraEditorScreen(
            item = editing!!,
            onBack = { editing = null },
            onSaveCopy = { matrix ->
                runCatching { ai.sakana.aura.gallery.data.export.MediaEditorExporter.saveToneEdit(context, editing!!, matrix.values) }
                    .onSuccess {
                        android.widget.Toast.makeText(context, "Edited copy saved to Pictures/Aura Edits", android.widget.Toast.LENGTH_LONG).show()
                        viewModel.refresh()
                    }
                    .onFailure { error ->
                        Timber.e(error, "Unable to export edited photo")
                        android.widget.Toast.makeText(context, error.message ?: "Couldn't save edited copy", android.widget.Toast.LENGTH_LONG).show()
                    }
            },
        )
        return
    }

    if (viewing != null) {
        BackHandler { viewing = null }
        MediaViewerScreen(
            item = items.firstOrNull { it.contentUri == viewing!!.contentUri }
                ?: trashedItems.firstOrNull { it.contentUri == viewing!!.contentUri }
                ?: viewing!!,
            onBack = { viewing = null },
            onShare = { shareMedia(context, listOf(viewing!!)) },
            onFavorite = { viewModel.toggleFavorite(viewing!!.contentUri) },
            onEdit = { editing = viewing },
            onDelete = { pendingTrashUris = listOf(viewing!!.contentUri) },
        )
        return
    }

    if (showRecentlyDeleted) {
        BackHandler { showRecentlyDeleted = false }
        RecentlyDeletedScreen(
            items = trashedItems,
            onBack = { showRecentlyDeleted = false },
            onRestore = viewModel::restoreFromTrash,
            onOpen = { viewing = it },
        )
        return
    }

    if (activeAlbum != null) {
        BackHandler { activeAlbum = null }
        AlbumDetailScreen(
            album = activeAlbum!!,
            onBack = { activeAlbum = null },
            onOpen = { viewing = it },
        )
        return
    }

    Scaffold(
        containerColor = AuraTokens.Void,
        bottomBar = {
            AuraBottomBar(destination) {
                destination = it
                selected = emptySet()
            }
        },
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().background(AuraTokens.Void).padding(contentPadding)) {
            when (destination) {
                Destination.PHOTOS -> LibraryScreen(
                    items = items,
                    access = access,
                    refreshing = refreshing,
                    error = error,
                    selectedUris = selected,
                    filter = filter,
                    onFilter = { filter = it },
                    onOpenViewer = { viewing = it },
                    onToggleSelected = { item ->
                        selected = if (item.contentUri in selected) selected - item.contentUri else selected + item.contentUri
                    },
                    onShareSelected = { shareMedia(context, it); selected = emptySet() },
                    onFavoriteSelected = {
                        it.forEach { item -> if (!item.isFavorite) viewModel.toggleFavorite(item.contentUri) }
                        selected = emptySet()
                    },
                    onDeleteSelected = { pendingTrashUris = it.map(MediaItem::contentUri) },
                    onChooseMedia = chooseMedia,
                    onRequestAccess = requestAccess,
                    onDismissError = viewModel::clearError,
                    onMore = { moreOpen = true },
                )
                Destination.ALBUMS -> AlbumsScreen(
                    items = items,
                    onOpenAlbum = { activeAlbum = it },
                    onRecentlyDeleted = { showRecentlyDeleted = true },
                    deletedCount = trashedItems.size,
                    onMore = { moreOpen = true },
                )
                Destination.EXPLORE -> ExploreScreen(
                    onPeople = { destination = Destination.PEOPLE },
                    onAiSets = { destination = Destination.AI_SETS },
                    onOptimize = { destination = Destination.OPTIMIZE },
                    onSettings = { destination = Destination.SETTINGS },
                )
                Destination.PEOPLE -> PeopleScreen(
                    items = items,
                    faces = detectedFaces,
                    progress = recognitionProgress,
                    onAnalyze = { viewModel.analyzeLibrary(items) },
                )
                Destination.AI_SETS -> AiSetsScreen(
                    items = items,
                    clusters = sceneClusters,
                    savedAiSets = savedAiSets,
                    progress = recognitionProgress,
                    error = error,
                    onAnalyze = { viewModel.analyzeLibrary(items) },
                    onSaveAiSet = viewModel::saveAiSet,
                    onRenameAiSet = viewModel::renameAiSet,
                    onMergeAiSets = viewModel::mergeAiSets,
                    onRemoveMember = viewModel::removeMediaFromAiSet,
                    onDeleteAiSet = viewModel::deleteAiSet,
                    onDismissError = viewModel::clearError,
                )
                Destination.OPTIMIZE -> OptimizeScreen(items)
                Destination.SETTINGS -> SettingsScreen(context, items)
            }
        }
    }

    if (moreOpen) {
        ModalBottomSheet(
            onDismissRequest = { moreOpen = false },
            containerColor = AuraTokens.Surface1,
            contentColor = AuraTokens.Paper,
        ) {
            Text("Aura", Modifier.padding(horizontal = 22.dp, vertical = 8.dp), fontFamily = InstrumentSerif, fontSize = 25.sp)
            SheetNavigationRow("Explore", "People and AI Sets", Icons.Outlined.Explore) { destination = Destination.EXPLORE; moreOpen = false }
            SheetNavigationRow("Optimize", "Storage and duplicate analysis", Icons.Outlined.Tune) { destination = Destination.OPTIMIZE; moreOpen = false }
            SheetNavigationRow("Settings", "Backup, privacy and app settings", Icons.Outlined.Settings) { destination = Destination.SETTINGS; moreOpen = false }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun LibraryScreen(
    items: List<MediaItem>,
    access: MediaAccessLevel,
    refreshing: Boolean,
    error: String?,
    selectedUris: Set<String>,
    filter: LibraryFilter,
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
    Column(Modifier.fillMaxSize().background(AuraTokens.Void)) {
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
        if (error != null) ErrorRule(error, onDismissError, Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        when {
            access == MediaAccessLevel.DENIED && items.isEmpty() -> FirstRunLibrary(onChooseMedia, onRequestAccess)
            filtered.isEmpty() && refreshing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("SCANNING DEVICE LIBRARY", color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 10.sp)
            }
            filtered.isEmpty() -> EmptyGallery(onChooseMedia)
            else -> DateGroupedMediaGrid(
                items = filtered,
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
}

@Composable
private fun DateGroupedMediaGrid(
    items: List<MediaItem>,
    selectedUris: Set<String> = emptySet(),
    onOpen: (MediaItem) -> Unit,
    onToggleSelected: (MediaItem) -> Unit = {},
) {
    val groups = remember(items) {
        items.sortedByDescending(MediaItem::dateTakenEpochMillis).groupBy { media ->
            java.time.Instant.ofEpochMilli(media.dateTakenEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().background(AuraTokens.Hairline),
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
}

private fun dateHeading(date: java.time.LocalDate): String {
    val today = java.time.LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGridTile(item: MediaItem, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
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
private fun VideoFrameThumbnail(item: MediaItem, modifier: Modifier = Modifier) {
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

private fun decodeVideoFrame(context: Context, uri: Uri): Bitmap? {
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

private fun durationLabel(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

@Composable
private fun AlbumsScreen(
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
private fun AlbumDetailScreen(album: ai.sakana.aura.gallery.domain.grouping.MediaAlbum, onBack: () -> Unit, onOpen: (MediaItem) -> Unit) {
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

@Composable
private fun RecentlyDeletedScreen(items: List<MediaItem>, onBack: () -> Unit, onRestore: (String) -> Unit, onOpen: (MediaItem) -> Unit) {
    Column(Modifier.fillMaxSize().background(AuraTokens.Void)) {
        Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to albums", tint = AuraTokens.Paper) }
            Text("Recently deleted", color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        InfoRule(
            title = "Aura recovery area",
            message = "These items are hidden from Aura’s main timeline. The original files remain in their device folders; restoring them makes them visible here again.",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
        if (items.isEmpty()) {
            EmptyIndexState("Nothing here", "Items you move out of the main Aura library will appear here.")
        } else {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.contentUri }) { item ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = item.contentUri.toUri(), contentDescription = null, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(6.dp)).clickable { onOpen(item) }, contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(item.displayName ?: if (item.isVideo) "Video" else "Photo", color = AuraTokens.Paper, fontFamily = Manrope, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${item.bucketName} · ${Formatter.formatShortFileSize(LocalContext.current, item.sizeBytes)}", color = AuraTokens.PaperFaint, fontFamily = Manrope, fontSize = 10.sp)
                        }
                        TextButton(onClick = { onRestore(item.contentUri) }) { Text("Restore") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaViewerScreen(
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
private fun ViewerAction(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
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

@Composable
private fun ExploreScreen(onPeople: () -> Unit, onAiSets: () -> Unit, onOptimize: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().background(AuraTokens.Void).verticalScroll(rememberScrollState())) {
        PageHeader("Explore", "PEOPLE · AI SETS · TOOLS")
        ExploreAction("People", "Group faces and manage identities on-device", Icons.Outlined.PeopleOutline, onPeople)
        ExploreAction("AI Sets", "Find recurring scenes, outfits and visual setups", Icons.Outlined.AutoAwesome, onAiSets)
        ExploreAction("Optimize", "Review storage usage and duplicate scans", Icons.Outlined.Tune, onOptimize)
        ExploreAction("Settings", "Backup, permissions and privacy controls", Icons.Outlined.Settings, onSettings)
        Text("Confirmed AI Sets are available now. Scene-suggestion accuracy evaluation and person identity clustering are still under development.", Modifier.padding(horizontal = 20.dp, vertical = 16.dp), color = AuraTokens.PaperFaint, fontFamily = Manrope, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiSetsScreen(
    items: List<MediaItem>,
    clusters: List<SceneCluster>,
    savedAiSets: List<AiSet>,
    progress: RecognitionProgress,
    error: String?,
    onAnalyze: () -> Unit,
    onSaveAiSet: (String, List<String>) -> Unit,
    onRenameAiSet: (String, String) -> Unit,
    onMergeAiSets: (String, String) -> Unit,
    onRemoveMember: (String, String) -> Unit,
    onDeleteAiSet: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    val mediaByUri = remember(items) { items.associateBy(MediaItem::contentUri) }
    var pendingSaveUris by remember { mutableStateOf<List<String>?>(null) }
    var newSetName by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(AuraTokens.Void)) {
        PageHeader("AI Sets", "ON-DEVICE SCENE SIMILARITY")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoRule(
                title = "Scene similarity suggestions",
                message = "Suggestions use broad visual features and are not guaranteed to describe the same room, outfit or composition. Save only the groups you confirm. Person identity is analyzed separately and is not used to create these scene suggestions.",
                modifier = Modifier,
            )
            if (error != null) ErrorRule(error, onDismissError)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${savedAiSets.size} saved AI Sets", color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("${savedAiSets.sumOf { it.mediaUris.size }} memberships", color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 10.sp)
                }
                AmberButton(
                    if (progress.isRunning) "ANALYZING" else if (progress.total > 0) "RESCAN" else "ANALYZE LIBRARY",
                    onAnalyze,
                    enabled = !progress.isRunning && items.isNotEmpty(),
                    height = 42,
                )
            }

            if (savedAiSets.isNotEmpty()) {
                Text("CONFIRMED SETS", color = AuraTokens.PaperMuted, fontFamily = JetBrainsMono, fontSize = 9.sp, letterSpacing = 1.sp)
                savedAiSets.forEach { set ->
                    SavedAiSetCard(
                        set = set,
                        mediaByUri = mediaByUri,
                        otherSets = savedAiSets.filterNot { it.id == set.id },
                        onRename = { name -> onRenameAiSet(set.id, name) },
                        onMerge = { targetSetId -> onMergeAiSets(targetSetId, set.id) },
                        onRemoveMember = { uri -> onRemoveMember(set.id, uri) },
                        onDelete = { onDeleteAiSet(set.id) },
                    )
                }
            }

            if (progress.isRunning || progress.total > 0) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AuraTokens.Surface1).padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (progress.isRunning) "ANALYZING ON THIS DEVICE" else "LAST ANALYSIS", color = AuraTokens.PaperMuted, fontFamily = JetBrainsMono, fontSize = 9.sp)
                        Text("${progress.completed}/${progress.total}", color = AuraTokens.Paper, fontFamily = JetBrainsMono, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (progress.total == 0) 0f else progress.completed.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = AuraTokens.Safelight,
                        trackColor = AuraTokens.Surface3,
                    )
                    if (!progress.isRunning) {
                        Spacer(Modifier.height(7.dp))
                        Text("${progress.processed} analyzed · ${progress.alreadyAnalyzed} reused · ${progress.failed} failed", color = AuraTokens.PaperFaint, fontFamily = Manrope, fontSize = 10.sp)
                    }
                    progress.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                }
            }

            Text("SUGGESTIONS TO REVIEW", color = AuraTokens.PaperMuted, fontFamily = JetBrainsMono, fontSize = 9.sp, letterSpacing = 1.sp)
            if (clusters.isEmpty()) {
                EmptyIndexState(
                    if (progress.total > 0 && !progress.isRunning) "No matching scene groups yet" else "AI Sets are ready to analyze",
                    if (items.isEmpty()) "Add local photos or videos first. The scene indexer runs entirely on this device."
                    else "Run analysis to find visually related scenes across different dates. Suggestions are candidates for review, not proof that two files are identical.",
                )
            } else {
                clusters.forEachIndexed { index, cluster ->
                    SceneClusterCard(
                        title = "Suggested Set ${(index + 1).toString().padStart(2, '0')}",
                        cluster = cluster,
                        mediaByUri = mediaByUri,
                        onSave = {
                            pendingSaveUris = cluster.mediaUris
                            newSetName = ""
                        },
                    )
                }
            }
            Text(
                "LOCAL ANALYSIS · NO MEDIA UPLOAD · ORIGINAL FILES UNCHANGED",
                color = AuraTokens.PaperFaint,
                fontFamily = JetBrainsMono,
                fontSize = 9.sp,
                letterSpacing = 0.4.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }

    if (pendingSaveUris != null) {
        AlertDialog(
            onDismissRequest = { pendingSaveUris = null },
            title = { Text("Save confirmed AI Set") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Give this group a name. The selected media will be linked to a saved set; the original files will not be moved or changed.")
                    OutlinedTextField(
                        value = newSetName,
                        onValueChange = { newSetName = it.take(AiSetRules.MAX_NAME_LENGTH) },
                        label = { Text("AI Set name") },
                        placeholder = { Text("e.g. Bedroom setup") },
                        singleLine = true,
                    )
                    Text("${pendingSaveUris.orEmpty().distinct().size} different media items", color = AuraTokens.PaperMuted, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newSetName.isNotBlank() && pendingSaveUris.orEmpty().distinct().size >= 2,
                    onClick = {
                        onSaveAiSet(newSetName, pendingSaveUris.orEmpty())
                        pendingSaveUris = null
                    },
                ) { Text("Save AI Set") }
            },
            dismissButton = { TextButton(onClick = { pendingSaveUris = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SavedAiSetCard(
    set: AiSet,
    mediaByUri: Map<String, MediaItem>,
    otherSets: List<AiSet>,
    onRename: (String) -> Unit,
    onMerge: (String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember(set.id) { mutableStateOf(false) }
    var menuOpen by remember(set.id) { mutableStateOf(false) }
    var mergeMenuOpen by remember(set.id) { mutableStateOf(false) }
    var renameOpen by remember(set.id) { mutableStateOf(false) }
    var deleteOpen by remember(set.id) { mutableStateOf(false) }
    var renameValue by remember(set.id, set.name) { mutableStateOf(set.name) }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AuraTokens.Surface1).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(set.name, color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("${set.mediaUris.size} items · user confirmed", color = AuraTokens.PaperFaint, fontFamily = Manrope, fontSize = 10.sp)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, "AI Set actions", tint = AuraTokens.PaperMuted) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(if (expanded) "Hide members" else "Manage members") }, onClick = { expanded = !expanded; menuOpen = false })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { renameValue = set.name; renameOpen = true; menuOpen = false })
                    if (otherSets.isNotEmpty()) {
                        DropdownMenuItem(text = { Text("Merge into another set") }, onClick = { mergeMenuOpen = true; menuOpen = false })
                    }
                    DropdownMenuItem(text = { Text("Delete AI Set") }, onClick = { deleteOpen = true; menuOpen = false })
                }
                DropdownMenu(expanded = mergeMenuOpen, onDismissRequest = { mergeMenuOpen = false }) {
                    otherSets.forEach { target ->
                        DropdownMenuItem(
                            text = { Text(target.name) },
                            onClick = { onMerge(target.id); mergeMenuOpen = false },
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            set.mediaUris.take(5).forEach { uri ->
                val media = mediaByUri[uri]
                if (media != null) {
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(4.dp)).background(AuraTokens.Surface2)) {
                        if (media.isVideo) VideoFrameThumbnail(media, Modifier.fillMaxSize())
                        else AsyncImage(model = uri.toUri(), contentDescription = media.displayName, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
            }
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Text("REMOVE AN ITEM TO SPLIT IT OUT OF THIS SET", color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 8.sp, letterSpacing = 0.5.sp)
            set.mediaUris.forEach { uri ->
                val media = mediaByUri[uri]
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (media != null) {
                        AsyncImage(model = uri.toUri(), contentDescription = media.displayName, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.size(44.dp).background(AuraTokens.Surface2), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.ImageNotSupported, null, tint = AuraTokens.PaperFaint) }
                    }
                    Text(media?.displayName ?: "Unavailable media item", Modifier.weight(1f).padding(horizontal = 9.dp), color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { onRemoveMember(uri) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Outlined.Close, "Remove from AI Set", tint = AuraTokens.PaperMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename AI Set") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it.take(AiSetRules.MAX_NAME_LENGTH) },
                    singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = { TextButton(enabled = renameValue.isNotBlank(), onClick = { onRename(renameValue); renameOpen = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } },
        )
    }
    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text("Delete this AI Set?") },
            text = { Text("Only the grouping and its name will be deleted. Your photos and videos will stay where they are.") },
            confirmButton = { TextButton(onClick = { onDelete(); deleteOpen = false }) { Text("Delete set") } },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SceneClusterCard(
    title: String,
    cluster: SceneCluster,
    mediaByUri: Map<String, MediaItem>,
    onSave: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AuraTokens.Surface1).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("${cluster.mediaUris.size} media items", color = AuraTokens.PaperFaint, fontFamily = Manrope, fontSize = 10.sp)
            }
            Text("${(cluster.averageSimilarity * 100).toInt()}% avg", color = AuraTokens.Safelight, fontFamily = JetBrainsMono, fontSize = 10.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            cluster.mediaUris.take(4).forEach { uri ->
                val media = mediaByUri[uri]
                if (media != null) {
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(4.dp)).background(AuraTokens.Surface2)) {
                        if (media.isVideo) VideoFrameThumbnail(media, Modifier.fillMaxSize())
                        else AsyncImage(model = uri.toUri(), contentDescription = media.displayName, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Unconfirmed scene suggestion", Modifier.weight(1f), color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 9.sp)
            TextButton(onClick = onSave, enabled = cluster.mediaUris.distinct().size >= 2) { Text("SAVE AS AI SET") }
        }
    }
}

@Composable
private fun PeopleScreen(
    items: List<MediaItem>,
    faces: List<DetectedFace>,
    progress: RecognitionProgress,
    onAnalyze: () -> Unit,
) {
    val mediaByUri = remember(items) { items.associateBy(MediaItem::contentUri) }
    val detectedMedia = remember(faces) { faces.groupBy(DetectedFace::contentUri) }
    Column(Modifier.fillMaxSize().background(AuraTokens.Void)) {
        PageHeader("People", "ON-DEVICE FACE DETECTION")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            InfoRule(
                title = "Face detection is active",
                message = "Aura stores face locations from photos and sampled video frames. This detector finds face regions; it does not identify who a person is. Person grouping will only be enabled after a licensed identity-embedding model is validated.",
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${faces.size} face detections", color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("${detectedMedia.size} media items with faces", color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 9.sp)
                }
                AmberButton(
                    if (progress.isRunning) "ANALYZING" else "ANALYZE LIBRARY",
                    onAnalyze,
                    enabled = !progress.isRunning && items.isNotEmpty(),
                    height = 42,
                )
            }
            Spacer(Modifier.height(10.dp))
            if (detectedMedia.isEmpty()) {
                EmptyIndexState(
                    "No face detections yet",
                    if (items.isEmpty()) "Add local photos or videos first."
                    else "Run on-device analysis. Aura will locate faces, but will not claim that different images contain the same person.",
                )
            } else {
                detectedMedia.entries.sortedByDescending { it.value.size }.forEach { (uri, locations) ->
                    val media = mediaByUri[uri] ?: return@forEach
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(9.dp)).background(AuraTokens.Surface1).padding(9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (media.isVideo) VideoFrameThumbnail(media, Modifier.size(66.dp).clip(RoundedCornerShape(5.dp)))
                        else AsyncImage(model = uri.toUri(), contentDescription = null, modifier = Modifier.size(66.dp).clip(RoundedCornerShape(5.dp)), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(media.displayName ?: if (media.isVideo) "Video" else "Photo", color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${locations.size} face detections${if (media.isVideo) " · sampled frames" else ""}", color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 10.sp)
                        }
                    }
                }
            }
            Text("FACE LOCATIONS ONLY · NO IDENTITY LABELS", Modifier.padding(vertical = 12.dp), color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 9.sp)
        }
    }
}

@Composable
private fun ExploreAction(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(12.dp)).background(AuraTokens.Surface1)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AuraTokens.Safelight, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(description, color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 10.sp, lineHeight = 15.sp)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = AuraTokens.PaperFaint)
    }
}

@Composable
private fun SheetNavigationRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AuraTokens.PaperMuted)
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, color = AuraTokens.Paper, fontFamily = Manrope, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = AuraTokens.PaperFaint, fontFamily = Manrope, fontSize = 10.sp)
        }
    }
}

@Composable
private fun FirstRunLibrary(onChoosePhotos: () -> Unit, onAllowAccess: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No media yet", color = AuraTokens.Paper, fontFamily = InstrumentSerif, fontSize = 34.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Choose photos, or allow Aura to show your device library. You stay in control of what the app can see.",
            color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 14.sp, lineHeight = 22.sp,
        )
        Spacer(Modifier.height(24.dp))
        AmberButton("PICK PHOTOS & VIDEOS", onChoosePhotos)
        TextButton(onClick = onAllowAccess) {
            Text("ALLOW LIBRARY ACCESS", color = AuraTokens.PaperMuted, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun EmptyGallery(onChoosePhotos: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No media yet", color = AuraTokens.Paper, fontFamily = InstrumentSerif, fontSize = 34.sp)
        Spacer(Modifier.height(12.dp))
        Text("Your library is empty.", color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))
        AmberButton("PICK PHOTOS & VIDEOS", onChoosePhotos)
    }
}

@Composable
private fun EmptyIndexState(title: String, message: String) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center) {
        Text(title, color = AuraTokens.Paper, fontFamily = InstrumentSerif, fontSize = 32.sp)
        Spacer(Modifier.height(8.dp))
        Text(message, color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 14.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun PeopleScreen() {
    Column(Modifier.fillMaxSize().background(AuraTokens.Void)) {
        PageHeader("People", "ON-DEVICE INDEX · NOT YET ACTIVE")
        InfoRule(
            title = "Face grouping is not running",
            message = "This build does not create face clusters or identify people. No face data is being uploaded.",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("People, when ready.", color = AuraTokens.Paper, fontFamily = InstrumentSerif, fontSize = 32.sp, lineHeight = 38.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "On-device face detection and a validated embedding model are required before clusters or confidence scores can appear here.",
                color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 14.sp, lineHeight = 22.sp,
            )
            Spacer(Modifier.height(16.dp))
            Text("NO CLUSTERS · NO MODEL INFERENCE", color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun AuraEditorScreen(item: MediaItem, onBack: () -> Unit, onSaveCopy: (ColorMatrix) -> Unit) {
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
private fun ToneSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
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
private fun HairlineSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
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
private fun HoldCompareButton(text: String, onPressed: () -> Unit, onReleased: () -> Unit) {
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

private fun buildToneColorMatrix(brightness: Float, contrast: Float, saturation: Float, warmth: Float): ColorMatrix {
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

@Composable
private fun OptimizeScreen(items: List<MediaItem>) {
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
                Text("No duplicate report", color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text("Exact-duplicate scanning has not run. No files are marked for deletion.", color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 12.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(10.dp))
                Text("NO DELETE ACTIONS · SCAN ENGINE PLANNED", color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 10.sp)
            }
            MetricCard("Similar · dHash threshold 5/64") {
                Text("No similarity report", color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text("Perceptual matching and burst grouping are planned for the next feature milestone.", color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 12.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun SettingsScreen(context: Context, items: List<MediaItem>) {
    val bytes = remember(items) { items.sumOf { it.sizeBytes.coerceAtLeast(0L) } }
    val database = remember { context.getDatabasePath("aura_gallery.db") }
    val databaseBytes = remember(database.lastModified()) {
        database.length() + java.io.File(database.path + "-wal").length()
    }
    Column(Modifier.fillMaxSize().background(AuraTokens.Void)) {
        PageHeader("Settings", "BUILD · 0.1.0")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            SettingsGroup("BACKUP")
            SettingsRow("Google Drive", "No sign-in, upload or sync job is configured. Media remains on this device.", "NOT CONNECTED")
            SettingsRow("Wi-Fi only", "Available after Google Drive backup is implemented.", "NOT SET UP")
            SettingsRow("Auto backup", "No photos or videos are being uploaded to Drive.", "OFF")

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


private fun shareDiagnosticReport(context: Context) {
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
private fun PageHeader(title: String, metadata: String) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 15.dp, bottom = 16.dp)) {
        Text(title, color = AuraTokens.Paper, fontFamily = InstrumentSerif, fontSize = 36.sp, lineHeight = 40.sp)
        Spacer(Modifier.height(6.dp))
        Text(metadata, color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun MetricCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(AuraTokens.Surface1).border(1.dp, AuraTokens.Hairline).padding(16.dp),
    ) {
        Text(title.uppercase(), color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 1.1.sp)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SettingsGroup(title: String) {
    Text(title, Modifier.padding(top = 18.dp, bottom = 4.dp), color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 1.5.sp)
}

@Composable
private fun SettingsRow(title: String, hint: String, value: String) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 58.dp).drawBehind {
            drawLine(AuraTokens.Hairline, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 1.dp.toPx())
        }.padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(hint, color = AuraTokens.PaperFaint, fontFamily = Manrope, fontSize = 10.sp, lineHeight = 15.sp)
        }
        Text(value, color = AuraTokens.PaperMuted, fontFamily = JetBrainsMono, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun InfoRule(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().background(AuraTokens.Surface1)) {
        Box(Modifier.width(2.dp).height(72.dp).background(AuraTokens.Safelight))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title, color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(3.dp))
            Text(message, color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 11.sp, lineHeight = 17.sp)
            if (action != null && onAction != null) {
                TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
                    Text(action, color = AuraTokens.Safelight, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 0.7.sp)
                }
            }
        }
    }
}

@Composable
private fun ErrorRule(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().background(AuraTokens.Surface1), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(2.dp).height(50.dp).background(AuraTokens.Safelight))
        Text(message, color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 9.dp))
        IconButton(onClick = onDismiss, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Outlined.Close, "Dismiss error", tint = AuraTokens.PaperMuted)
        }
    }
}

@Composable
private fun AmberButton(text: String, onClick: () -> Unit, height: Int = 48, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(height.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AuraTokens.Safelight, contentColor = AuraTokens.InkOnAmber),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.2.sp)
    }
}

@Composable
private fun HairlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier.height(48.dp).border(1.dp, AuraTokens.HairlineStrong.copy(alpha = if (enabled) 1f else 0.45f), RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp)).then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = AuraTokens.Paper.copy(alpha = if (enabled) 1f else 0.38f), fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

@Composable
private fun AuraBottomBar(selected: Destination, onSelect: (Destination) -> Unit) {
    Column(Modifier.fillMaxWidth().background(AuraTokens.Void)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(AuraTokens.Hairline))
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem("Photos", selected == Destination.PHOTOS, Icons.Outlined.GridView) { onSelect(Destination.PHOTOS) }
            NavItem("Albums", selected == Destination.ALBUMS, Icons.Outlined.Folder) { onSelect(Destination.ALBUMS) }
            NavItem("Explore", selected == Destination.EXPLORE || selected == Destination.PEOPLE || selected == Destination.AI_SETS || selected == Destination.OPTIMIZE || selected == Destination.SETTINGS, Icons.Outlined.Explore) { onSelect(Destination.EXPLORE) }
        }
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, icon: ImageVector, onClick: () -> Unit) {
    Column(
        Modifier.width(72.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (selected) AuraTokens.Safelight else AuraTokens.PaperFaint, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (selected) AuraTokens.Safelight else AuraTokens.PaperFaint, fontFamily = Manrope, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun readMediaAccess(context: Context): MediaAccessLevel {
    val granted = MediaPermissionPolicy.requiredPermissions(Build.VERSION.SDK_INT).filter {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }.toSet()
    return MediaPermissionPolicy.resolveAccess(Build.VERSION.SDK_INT, granted)
}

private fun shareMedia(context: Context, items: List<MediaItem>) {
    if (items.isEmpty()) return
    val uris = ArrayList<Uri>().apply { items.forEach { add(it.contentUri.toUri()) } }
    val mimeTypes = items.map { it.mimeType?.takeIf(String::isNotBlank) ?: if (it.isVideo) "video/*" else "image/*" }.distinct()
    val mimeType = when {
        mimeTypes.size == 1 -> mimeTypes.first()
        items.all(MediaItem::isVideo) -> "video/*"
        items.all { !it.isVideo } -> "image/*"
        else -> "*/*"
    }
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply { type = mimeType; putExtra(Intent.EXTRA_STREAM, uris.first()) }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = mimeType; putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
    }.apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, "Aura media", uris.first()).also { clip ->
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Share media")) }
}
