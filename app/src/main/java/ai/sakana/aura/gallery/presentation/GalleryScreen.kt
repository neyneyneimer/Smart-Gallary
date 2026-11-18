package ai.sakana.aura.gallery.presentation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import ai.sakana.aura.gallery.design.AuraTokens
import ai.sakana.aura.gallery.design.AuraToneColorMatrix
import ai.sakana.aura.gallery.design.InstrumentSerif
import ai.sakana.aura.gallery.design.JetBrainsMono
import ai.sakana.aura.gallery.design.Manrope
import ai.sakana.aura.gallery.domain.model.MediaItem
import ai.sakana.aura.gallery.platform.MediaAccessLevel
import ai.sakana.aura.gallery.platform.MediaPermissionPolicy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.text.NumberFormat
import java.util.Locale

private enum class Destination { LIBRARY, PEOPLE, OPTIMIZE, SETTINGS }
private enum class LibraryFilter { ALL, PHOTOS, PEOPLE, PLACES, VIDEO }

@Composable
fun GalleryRoute(viewModel: GalleryViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var access by remember { mutableStateOf(readMediaAccess(context)) }
    val items by viewModel.mediaItems.collectAsState()
    val refreshing by viewModel.isRefreshing.collectAsState()
    val error by viewModel.errorMessage.collectAsState()
    var destination by remember { mutableStateOf(Destination.LIBRARY) }
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var editing by remember { mutableStateOf<MediaItem?>(null) }

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

    val choosePhotos = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val requestAccess = {
        permissionRequest.launch(MediaPermissionPolicy.requiredPermissions(Build.VERSION.SDK_INT).toTypedArray())
    }

    if (editing != null) {
        Scaffold(
            containerColor = AuraTokens.Void,
            bottomBar = {
                AuraBottomBar(destination) {
                    editing = null
                    destination = it
                    selected = emptySet()
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AuraEditorScreen(editing!!, onBack = { editing = null })
            }
        }
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
        Column(
            Modifier.fillMaxSize().background(AuraTokens.Void).padding(contentPadding),
        ) {
            when (destination) {
                Destination.LIBRARY -> LibraryScreen(
                    items = items,
                    access = access,
                    refreshing = refreshing,
                    error = error,
                    selectedUris = selected,
                    onToggleSelected = { item ->
                        selected = if (item.contentUri in selected) selected - item.contentUri
                        else selected + item.contentUri
                    },
                    onOpenEditor = { editing = it; selected = emptySet() },
                    onClearSelection = { selected = emptySet() },
                    onShare = { shareMedia(context, it) },
                    onKeep = { selectedItems ->
                        selectedItems.filterNot { it.isFavorite }.forEach { viewModel.toggleFavorite(it.contentUri) }
                        selected = emptySet()
                    },
                    onChoosePhotos = choosePhotos,
                    onRequestAccess = requestAccess,
                    onDismissError = viewModel::clearError,
                    filter = filter,
                    onFilter = {
                        if (it == LibraryFilter.PEOPLE) {
                            destination = Destination.PEOPLE
                            filter = LibraryFilter.ALL
                        } else filter = it
                    },
                )
                Destination.PEOPLE -> PeopleScreen()
                Destination.OPTIMIZE -> OptimizeScreen(items)
                Destination.SETTINGS -> SettingsScreen(context, items)
            }
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
    onToggleSelected: (MediaItem) -> Unit,
    onOpenEditor: (MediaItem) -> Unit,
    onClearSelection: () -> Unit,
    onShare: (List<MediaItem>) -> Unit,
    onKeep: (List<MediaItem>) -> Unit,
    onChoosePhotos: () -> Unit,
    onRequestAccess: () -> Unit,
    onDismissError: () -> Unit,
    filter: LibraryFilter,
    onFilter: (LibraryFilter) -> Unit,
) {
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var newestFirst by remember { mutableStateOf(true) }
    val sorted = remember(items, newestFirst) { if (newestFirst) items else items.reversed() }
    val visible = remember(sorted, query) {
        if (query.isBlank()) sorted else sorted.filter { it.displayName.orEmpty().contains(query, true) }
    }
    val selectedItems = items.filter { it.contentUri in selectedUris }
    val locale = LocalLocale.current.platformLocale

    Column(Modifier.fillMaxSize().background(AuraTokens.Void)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Aura", color = AuraTokens.Paper, fontFamily = InstrumentSerif, fontSize = 32.sp, lineHeight = 36.sp)
            Row {
                IconButton(onClick = { searchOpen = !searchOpen }) {
                    Icon(Icons.Outlined.Search, "Search photos", tint = AuraTokens.PaperMuted)
                }
                IconButton(onClick = { newestFirst = !newestFirst }) {
                    Icon(Icons.AutoMirrored.Outlined.Sort, "Change photo order", tint = AuraTokens.PaperMuted)
                }
            }
        }
        if (searchOpen) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search filenames", color = AuraTokens.PaperFaint) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = AuraTokens.Paper),
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = AuraTokens.Surface1,
                        unfocusedContainerColor = AuraTokens.Surface1,
                        focusedIndicatorColor = AuraTokens.Safelight,
                        unfocusedIndicatorColor = AuraTokens.Hairline,
                        cursorColor = AuraTokens.Safelight,
                    ),
                )
                IconButton(onClick = { searchOpen = false; query = "" }) {
                    Icon(Icons.Outlined.Close, "Close search", tint = AuraTokens.PaperMuted)
                }
            }
        }

        Text(
            NumberFormat.getIntegerInstance(locale).format(items.size) + " photographs",
            Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp),
            color = AuraTokens.Paper,
            fontFamily = InstrumentSerif,
            fontSize = 33.sp,
            lineHeight = 38.sp,
        )
        Text(
            when (access) {
                MediaAccessLevel.FULL -> if (refreshing) "ON DEVICE · SCANNING LIBRARY" else "ON DEVICE · FULL LIBRARY ACCESS"
                MediaAccessLevel.PARTIAL -> "ON DEVICE · SELECTED PHOTOS ONLY"
                MediaAccessLevel.DENIED -> "ON DEVICE · LIBRARY ACCESS NOT GRANTED"
            },
            Modifier.padding(start = 20.dp, end = 20.dp, top = 7.dp, bottom = 15.dp),
            color = AuraTokens.PaperFaint,
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
        )
        FilterRail(filter, onFilter)

        if (access == MediaAccessLevel.PARTIAL) {
            InfoRule(
                title = "Showing selected photos",
                message = "Aura can only see the photos you selected. Manage access at any time.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                action = "MANAGE ACCESS",
                onAction = onRequestAccess,
            )
        }
        if (error != null) {
            ErrorRule(error, onDismissError, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }

        when {
            access == MediaAccessLevel.DENIED && items.isEmpty() -> FirstRunLibrary(onChoosePhotos, onRequestAccess)
            filter == LibraryFilter.PLACES -> EmptyIndexState("Places", "Place grouping is not part of this build yet.")
            filter == LibraryFilter.VIDEO -> EmptyIndexState("0 videos", "This build currently indexes device photos only.")
            visible.isEmpty() && refreshing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("SCANNING", color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 10.sp)
            }
            visible.isEmpty() -> EmptyGallery(onChoosePhotos)
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).fillMaxWidth().background(AuraTokens.Hairline),
                contentPadding = PaddingValues(bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                itemsIndexed(
                    visible,
                    key = { _, item -> item.contentUri },
                    span = { index, _ -> if (index % 14 == 4) GridItemSpan(2) else GridItemSpan(1) },
                ) { index, item ->
                    val isSelected = item.contentUri in selectedUris
                    ContactSheetTile(
                        item = item,
                        frameNumber = index + 1,
                        totalCount = items.size,
                        selected = isSelected,
                        onClick = { if (isSelected) onOpenEditor(item) else onToggleSelected(item) },
                        onLongClick = { onOpenEditor(item) },
                    )
                }
            }
        }

        if (selectedItems.isNotEmpty()) {
            SelectionActionBar(
                selectedItems.size,
                onShare = { onShare(selectedItems) },
                onKeep = { onKeep(selectedItems) },
                onClear = onClearSelection,
            )
        }
    }
}

@Composable
private fun FilterRail(selected: LibraryFilter, onSelect: (LibraryFilter) -> Unit) {
    val entries = listOf(
        LibraryFilter.ALL to "All",
        LibraryFilter.PHOTOS to "Photos",
        LibraryFilter.PEOPLE to "People",
        LibraryFilter.PLACES to "Places",
        LibraryFilter.VIDEO to "Video",
    )
    Row(
        Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(23.dp),
        verticalAlignment = Alignment.Top,
    ) {
        entries.forEach { (filter, label) ->
            Column(
                Modifier.clickable { onSelect(filter) }.defaultMinSize(minHeight = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    label,
                    color = if (selected == filter) AuraTokens.Paper else AuraTokens.PaperFaint,
                    fontFamily = Manrope,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (selected == filter) Box(Modifier.fillMaxWidth().height(1.5.dp).background(AuraTokens.Safelight))
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(AuraTokens.Hairline))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactSheetTile(
    item: MediaItem,
    frameNumber: Int,
    totalCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    val pressedSource = remember { MutableInteractionSource() }
    val pressed by pressedSource.collectIsPressedAsState()
    val date = remember(item.dateTakenEpochMillis, locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .format(Instant.ofEpochMilli(item.dateTakenEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate())
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).background(AuraTokens.Surface1)
            .drawBehind {
                if (selected) drawRect(AuraTokens.Safelight, style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx()))
            }
            .combinedClickable(
                interactionSource = pressedSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics {
                contentDescription = "Photo " + frameNumber + " of " + totalCount + ", date " + date +
                    (if (item.isFavorite) ", kept" else ", not kept") + ", double tap or long press to edit"
            },
    ) {
        AsyncImage(
            model = item.contentUri.toUri(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Color(0x66000000)).padding(horizontal = 7.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                frameNumber.toString().padStart(4, '0'),
                color = if (selected) AuraTokens.Safelight else AuraTokens.Paper.copy(alpha = 0.82f),
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                letterSpacing = 0.7.sp,
            )
            if (item.isFavorite) Icon(Icons.Outlined.Favorite, null, tint = AuraTokens.Paper, modifier = Modifier.size(12.dp))
        }
        if (pressed) {
            Box(Modifier.fillMaxSize().background(AuraTokens.Safelight.copy(alpha = 0.06f)))
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(7.dp).size(14.dp).background(AuraTokens.Safelight, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", color = AuraTokens.InkOnAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SelectionActionBar(
    count: Int,
    onShare: () -> Unit,
    onKeep: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(AuraTokens.Surface2).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(count.toString() + " SELECTED", color = AuraTokens.Safelight, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 0.6.sp)
            IconButton(onClick = onClear, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Outlined.Close, "Clear selection", tint = AuraTokens.PaperMuted, modifier = Modifier.size(17.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HairlineButton("Share", onShare)
            AmberButton("Keep", onKeep, height = 40)
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
        Text("0 photographs", color = AuraTokens.Paper, fontFamily = InstrumentSerif, fontSize = 34.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Choose photos, or allow Aura to show your device library. You stay in control of what the app can see.",
            color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 14.sp, lineHeight = 22.sp,
        )
        Spacer(Modifier.height(24.dp))
        AmberButton("PICK PHOTOS", onChoosePhotos)
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
        Text("0 photographs", color = AuraTokens.Paper, fontFamily = InstrumentSerif, fontSize = 34.sp)
        Spacer(Modifier.height(12.dp))
        Text("Your library is empty.", color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))
        AmberButton("PICK PHOTOS", onChoosePhotos)
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
private fun AuraEditorScreen(item: MediaItem, onBack: () -> Unit) {
    var brightness by remember(item.contentUri) { mutableFloatStateOf(0f) }
    var contrast by remember(item.contentUri) { mutableFloatStateOf(0f) }
    var saturation by remember(item.contentUri) { mutableFloatStateOf(0f) }
    var warmth by remember(item.contentUri) { mutableFloatStateOf(0f) }
    var compareOriginal by remember { mutableStateOf(false) }
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
                    HairlineButton("Export · M2", {}, enabled = false, modifier = Modifier.fillMaxWidth())
                }
            }
        }
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
            MetricCard("Indexed device images") {
                Text(Formatter.formatShortFileSize(context, totalBytes), color = AuraTokens.Paper, fontFamily = InstrumentSerif, fontSize = 44.sp, lineHeight = 48.sp)
                Text(items.size.toString() + " PHOTOS · CURRENTLY INDEXED", color = AuraTokens.PaperMuted, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(13.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).background(AuraTokens.Surface3)) {
                    Box(Modifier.fillMaxWidth().height(3.dp).background(AuraTokens.PaperMuted))
                }
                Spacer(Modifier.height(6.dp))
                Text("IMAGES ONLY · VIDEO AND OTHER FILES EXCLUDED", color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 10.sp)
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
            SettingsRow("Google Drive", "Provider connection is not available yet.", "NOT CONNECTED")
            SettingsRow("Wi-Fi only", "Backup controls appear with cloud sync.", "PLANNED M3")
            SettingsRow("Auto backup", "No background upload jobs are running.", "OFF")

            SettingsGroup("ON-DEVICE ANALYSIS")
            SettingsRow("Face grouping", "Face detection and licensed embeddings are not active.", "PLANNED M4")
            SettingsRow("Duplicate scan", "SHA-256 and dHash analysis has not run.", "PLANNED M2")

            SettingsGroup("STORAGE")
            SettingsRow("Device library", items.size.toString() + " indexed images · " + Formatter.formatShortFileSize(context, bytes), "ON DEVICE")
            SettingsRow("Database", "Local media index", Formatter.formatShortFileSize(context, databaseBytes))
            SettingsRow("Export folder", "Edited copies will preserve the original.", "PLANNED M2")
            Spacer(Modifier.height(24.dp))
        }
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
private fun AmberButton(text: String, onClick: () -> Unit, height: Int = 48) {
    Button(
        onClick = onClick,
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
            NavItem("Library", selected == Destination.LIBRARY, Icons.Outlined.GridView) { onSelect(Destination.LIBRARY) }
            NavItem("People", selected == Destination.PEOPLE, Icons.Outlined.PeopleOutline) { onSelect(Destination.PEOPLE) }
            NavItem("Optimize", selected == Destination.OPTIMIZE, Icons.Outlined.Tune) { onSelect(Destination.OPTIMIZE) }
            NavItem("Settings", selected == Destination.SETTINGS, Icons.Outlined.Settings) { onSelect(Destination.SETTINGS) }
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
    val uris = ArrayList<Uri>().apply { items.forEach { add(it.contentUri.toUri()) } }
    if (uris.isEmpty()) return
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply { type = "image/*"; putExtra(Intent.EXTRA_STREAM, uris.first()) }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "image/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
    }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Share photos")) }
}
