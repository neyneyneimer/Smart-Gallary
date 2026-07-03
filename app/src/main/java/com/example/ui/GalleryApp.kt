package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.database.PhotoEntity
import com.example.data.database.PhotoEditVersionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryApp(viewModel: GalleryViewModel) {
    val photos by viewModel.allPhotos.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val selectedPhoto by viewModel.selectedPhoto.collectAsState()
    val importStatus by viewModel.importStatus.collectAsState()

    var currentTab by remember { mutableStateOf(0) }
    var selectedFaceAlbum by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Aura AI Gallery",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Smart Space & Edit Engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                },
                actions = {
                    if (isScanning) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = { viewModel.runFaceGrouping() },
                            modifier = Modifier.testTag("action_scan_faces")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Scan",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { 
                        currentTab = 0
                        selectedFaceAlbum = null 
                    },
                    icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery") },
                    label = { Text("Gallery") }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Face, contentDescription = "AI Albums") },
                    label = { Text("AI Albums") }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.CloudSync, contentDescription = "Cloud") },
                    label = { Text("Cloud") }
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.Storage, contentDescription = "Optimizer") },
                    label = { Text("Optimizer") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> {
                    if (selectedFaceAlbum != null) {
                        FaceAlbumGridScreen(
                            faceName = selectedFaceAlbum!!,
                            photos = photos,
                            onPhotoClick = { viewModel.selectPhoto(it) },
                            onBack = { selectedFaceAlbum = null }
                        )
                    } else {
                        GalleryFeedScreen(
                            photos = photos,
                            onPhotoClick = { viewModel.selectPhoto(it) }
                        )
                    }
                }
                1 -> AIAlbumsScreen(
                    photos = photos,
                    isScanning = isScanning,
                    scanProgress = scanProgress,
                    onAlbumClick = { face ->
                        selectedFaceAlbum = face
                        currentTab = 0 // jump to gallery with filtered view
                    },
                    onScanClick = { viewModel.runFaceGrouping() }
                )
                2 -> CloudSyncScreen(viewModel = viewModel)
                3 -> StorageOptimizerScreen(viewModel = viewModel)
            }

            // Dynamic Photo Details Modal (Full Screen non-destructive editor & history)
            selectedPhoto?.let { photo ->
                val versions by viewModel.selectedPhotoVersions.collectAsState()
                PhotoDetailsDialog(
                    photo = photo,
                    versions = versions,
                    viewModel = viewModel,
                    onDismiss = { viewModel.selectPhoto(null) }
                )
            }

            // Custom Import / Duplicate Warning Dialog
            importStatus?.let { status ->
                when (status) {
                    is ImportStatus.DuplicatePrevented -> {
                        DuplicateWarningDialog(
                            duplicatePhoto = status.duplicateOf,
                            onDismiss = { viewModel.clearImportStatus() }
                        )
                    }
                    is ImportStatus.Success -> {
                        ImportSuccessDialog(
                            photo = status.photo,
                            onDismiss = { viewModel.clearImportStatus() }
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// GALLERY FEED SCREEN
// -------------------------------------------------------------
@Composable
fun GalleryFeedScreen(
    photos: List<PhotoEntity>,
    onPhotoClick: (PhotoEntity) -> Unit
) {
    if (photos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var selectedFilterIndex by remember { mutableStateOf(0) } // 0: All, 1: Photos, 2: Videos
    val filteredPhotos = remember(photos, selectedFilterIndex) {
        when (selectedFilterIndex) {
            1 -> photos.filter { it.durationSeconds == 0 }
            2 -> photos.filter { it.durationSeconds > 0 }
            else -> photos
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = when (selectedFilterIndex) {
                1 -> "Photos"
                2 -> "Videos"
                else -> "All Media"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
        )

        // Filter chips for Media types
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilterIndex == 0,
                onClick = { selectedFilterIndex = 0 },
                label = { Text("All (${photos.size})") },
                leadingIcon = if (selectedFilterIndex == 0) {
                    { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
            FilterChip(
                selected = selectedFilterIndex == 1,
                onClick = { selectedFilterIndex = 1 },
                label = { Text("Photos (${photos.count { it.durationSeconds == 0 }})") },
                leadingIcon = if (selectedFilterIndex == 1) {
                    { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
            FilterChip(
                selected = selectedFilterIndex == 2,
                onClick = { selectedFilterIndex = 2 },
                label = { Text("Videos (${photos.count { it.durationSeconds > 0 }})") },
                leadingIcon = if (selectedFilterIndex == 2) {
                    { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }

        if (filteredPhotos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (selectedFilterIndex == 2) Icons.Default.PlayCircle else Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (selectedFilterIndex == 2) "No video files found" else "No photos found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(filteredPhotos) { photo ->
                    GalleryPhotoCard(photo = photo, onPhotoClick = onPhotoClick)
                }
            }
        }
    }
}

enum class SubFolderGroupingMode(val label: String, val icon: ImageVector) {
    NONE("All", Icons.Default.GridView),
    UNIFIED_SCENES("Smart Scenes", Icons.Default.AutoAwesome),
    BY_ATTIRE("By Attire", Icons.Default.Palette),
    BY_LOCATION("By Environment", Icons.Default.Place),
    BY_LIGHTING("By Lighting", Icons.Default.WbSunny)
}

@Composable
fun FaceAlbumGridScreen(
    faceName: String,
    photos: List<PhotoEntity>,
    onPhotoClick: (PhotoEntity) -> Unit,
    onBack: () -> Unit
) {
    val albumPhotos = remember(photos, faceName) {
        photos.filter { 
            if (faceName == "Uncategorized") it.faceTag == null || it.faceTag == "No Face" || it.faceTag == "Unknown"
            else it.faceTag == faceName 
        }
    }

    var groupingMode by remember { mutableStateOf(SubFolderGroupingMode.NONE) }
    var selectedSubFolder by remember { mutableStateOf<String?>(null) }

    // Reset selected folder if we switch grouping mode
    LaunchedEffect(groupingMode) {
        selectedSubFolder = null
    }

    // Group photos based on mode
    val groupedPhotos = remember(albumPhotos, groupingMode) {
        if (groupingMode == SubFolderGroupingMode.NONE) {
            emptyMap()
        } else {
            albumPhotos.groupBy { photo ->
                val key = when (groupingMode) {
                    SubFolderGroupingMode.UNIFIED_SCENES -> {
                        if (photo.attire != null && photo.environment != null) {
                            "${photo.attire} • ${photo.environment} (${photo.lighting ?: "Ambient"})"
                        } else null
                    }
                    SubFolderGroupingMode.BY_ATTIRE -> photo.attire
                    SubFolderGroupingMode.BY_LOCATION -> photo.environment
                    SubFolderGroupingMode.BY_LIGHTING -> photo.lighting
                    SubFolderGroupingMode.NONE -> null
                }
                key ?: "Uncategorized (Needs AI Scan)"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (selectedSubFolder != null) {
                    selectedSubFolder = null
                } else {
                    onBack()
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (selectedSubFolder != null) selectedSubFolder!! else "$faceName's Album",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                val currentPhotosText = if (selectedSubFolder != null) {
                    val folderItems = groupedPhotos[selectedSubFolder] ?: emptyList()
                    val pCount = folderItems.count { it.durationSeconds == 0 }
                    val vCount = folderItems.count { it.durationSeconds > 0 }
                    buildString {
                        if (pCount > 0) append("$pCount ${if (pCount == 1) "photo" else "photos"}")
                        if (pCount > 0 && vCount > 0) append(" and ")
                        if (vCount > 0) append("$vCount ${if (vCount == 1) "video" else "videos"}")
                        append(" in scene")
                    }
                } else {
                    val photosCount = albumPhotos.count { it.durationSeconds == 0 }
                    val videosCount = albumPhotos.count { it.durationSeconds > 0 }
                    buildString {
                        if (photosCount > 0) append("$photosCount ${if (photosCount == 1) "photo" else "photos"}")
                        if (photosCount > 0 && videosCount > 0) append(" and ")
                        if (videosCount > 0) append("$videosCount ${if (videosCount == 1) "video" else "videos"}")
                        append(" found")
                    }
                }
                
                Text(
                    text = currentPhotosText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Mode Selector Chips (only visible when not viewing inside a sub-folder)
        if (selectedSubFolder == null) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 16.dp)
            ) {
                items(SubFolderGroupingMode.values().toList()) { mode ->
                    FilterChip(
                        selected = groupingMode == mode,
                        onClick = { groupingMode = mode },
                        label = { Text(mode.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = mode.icon,
                                contentDescription = mode.label,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        } else {
            // Folder Breadcrumb banner
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "In Folder",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Subfolder inside $faceName's Album",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        onClick = { selectedSubFolder = null }
                    ) {
                        Text("Exit Folder", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Content
        if (albumPhotos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No photos in this album")
            }
        } else {
            if (groupingMode == SubFolderGroupingMode.NONE) {
                // All photos in standard grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(albumPhotos) { photo ->
                        GalleryPhotoCard(photo = photo, onPhotoClick = onPhotoClick)
                    }
                }
            } else if (selectedSubFolder != null) {
                // Photos inside a specific folder
                val folderPhotos = groupedPhotos[selectedSubFolder] ?: emptyList()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(folderPhotos) { photo ->
                        GalleryPhotoCard(photo = photo, onPhotoClick = onPhotoClick)
                    }
                }
            } else {
                // Folders list screen (2-column layout)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    val folders = groupedPhotos.keys.toList().sortedWith(compareBy {
                        if (it.startsWith("Uncategorized")) 1 else 0 // put uncategorized folder at the end!
                    })
                    
                    items(folders) { folderName ->
                        val folderItems = groupedPhotos[folderName] ?: emptyList()
                        val pCount = folderItems.count { it.durationSeconds == 0 }
                        val vCount = folderItems.count { it.durationSeconds > 0 }
                        
                        val isUncategorized = folderName.startsWith("Uncategorized")
                        
                        Card(
                            onClick = { selectedSubFolder = folderName },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUncategorized) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                }
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isUncategorized) {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .testTag("subfolder_card_${folderName.replace(" ", "_")}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isUncategorized) Icons.Default.FolderOpen else Icons.Default.Folder,
                                        contentDescription = "Folder",
                                        tint = if (isUncategorized) {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Open",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = folderName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    
                                    val itemsLabel = buildString {
                                        if (pCount > 0) append("$pCount ${if (pCount == 1) "photo" else "photos"}")
                                        if (pCount > 0 && vCount > 0) append(", ")
                                        if (vCount > 0) append("$vCount ${if (vCount == 1) "video" else "videos"}")
                                    }
                                    Text(
                                        text = itemsLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }

                                // Interactive mini image-previews of files in this folder
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    folderItems.take(3).forEach { p ->
                                        val context = LocalContext.current
                                        val previewResId = remember(p.resourceName) {
                                            val id = context.resources.getIdentifier(p.resourceName, "drawable", context.packageName)
                                            if (id != 0) id else R.drawable.ic_launcher_background
                                        }
                                        Card(
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.size(24.dp),
                                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.4f))
                                        ) {
                                            Image(
                                                painter = painterResource(id = previewResId),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                                colorFilter = getColorFilterFor(p.currentFilter)
                                            )
                                        }
                                    }
                                    
                                    if (folderItems.size > 3) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(4.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "+${folderItems.size - 3}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%d:%02d", m, s)
}

@Composable
fun GalleryPhotoCard(
    photo: PhotoEntity,
    onPhotoClick: (PhotoEntity) -> Unit
) {
    val context = LocalContext.current
    val resId = remember(photo.resourceName) {
        val id = context.resources.getIdentifier(photo.resourceName, "drawable", context.packageName)
        if (id != 0) id else R.drawable.ic_launcher_background
    }

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onPhotoClick(photo) }
            .testTag("photo_card_${photo.id}"),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = resId),
                contentDescription = photo.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = getColorFilterFor(photo.currentFilter)
            )

            // Video overlay indicators
            if (photo.durationSeconds > 0 || photo.mimeType.startsWith("video/")) {
                // Play overlay in the center
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Video",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Duration tag in the top-right
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = formatDuration(photo.durationSeconds.takeIf { it > 0 } ?: 15),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Indicators Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Name or Face tag
                if (photo.faceTag != null && photo.faceTag != "No Face" && photo.faceTag != "Unknown") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.PrimaryContainerColor(), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = "Face",
                            modifier = Modifier.size(10.dp),
                            tint = Color.PrimaryColor()
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = photo.faceTag,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = Color.PrimaryColor(),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Sync status or edits
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (photo.currentFilter != null) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edited",
                            tint = Color.Yellow,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (photo.hasBackupSynced) {
                        Icon(
                            Icons.Default.CloudDone,
                            contentDescription = "Synced",
                            tint = Color.Green,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// AI ALBUMS SCREEN
// -------------------------------------------------------------
@Composable
fun AIAlbumsScreen(
    photos: List<PhotoEntity>,
    isScanning: Boolean,
    scanProgress: Float,
    onAlbumClick: (String) -> Unit,
    onScanClick: () -> Unit
) {
    val groups = remember(photos) {
        photos.groupBy { it.faceTag ?: "Unscanned" }
    }

    val scannedCount = remember(photos) {
        photos.count { it.faceTag != null }
    }
    val unscannedCount = remember(photos) {
        photos.count { it.faceTag == null }
    }
    val unscannedPhotosCount = remember(photos) {
        photos.count { it.faceTag == null && it.durationSeconds == 0 }
    }
    val unscannedVideosCount = remember(photos) {
        photos.count { it.faceTag == null && it.durationSeconds > 0 }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "AI Smart Organization",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Face recognition organizes your gallery automatically into discrete folders, optimized with Gemini AI.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (isScanning) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Scanning with Gemini AI...",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${(scanProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { scanProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        }
                    } else if (unscannedCount > 0) {
                        Button(
                            onClick = onScanClick,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_run_ai_scan")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            val scanText = buildString {
                                append("Scan ")
                                if (unscannedPhotosCount > 0 && unscannedVideosCount > 0) {
                                    append("$unscannedPhotosCount Photos & $unscannedVideosCount Videos")
                                } else if (unscannedPhotosCount > 0) {
                                    append("$unscannedPhotosCount Unorganized Photos")
                                } else if (unscannedVideosCount > 0) {
                                    append("$unscannedVideosCount Unorganized Videos")
                                } else {
                                    append("$unscannedCount Media Items")
                                }
                            }
                            Text(scanText)
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color.Green.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = Color.Green)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "All media files organized beautifully!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Green,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Folders Section
        if (scannedCount == 0 && !isScanning) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.FaceRetouchingOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No Face Folders Created Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Click the scan button above to let AI analyze your pictures.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            // Display folders
            item {
                Text(
                    text = "People & Smart Categories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            val faceKeys = listOf("Alice", "Bob", "Clara", "No Face", "Unknown")
            items(faceKeys) { face ->
                val list = groups[face] ?: emptyList()
                if (list.isNotEmpty()) {
                    val displayName = when (face) {
                        "No Face", "Unknown" -> "Scenic & Landscapes"
                        else -> face
                    }
                    val displayIcon = when (face) {
                        "No Face", "Unknown" -> Icons.Default.FilterHdr
                        else -> Icons.Default.AccountCircle
                    }
                    val photosCountInAlbum = list.count { it.durationSeconds == 0 }
                    val videosCountInAlbum = list.count { it.durationSeconds > 0 }
                    val countText = buildString {
                        if (photosCountInAlbum > 0) append("$photosCountInAlbum ${if (photosCountInAlbum == 1) "photo" else "photos"}")
                        if (photosCountInAlbum > 0 && videosCountInAlbum > 0) append(", ")
                        if (videosCountInAlbum > 0) append("$videosCountInAlbum ${if (videosCountInAlbum == 1) "video" else "videos"}")
                    }
                    FaceAlbumCard(
                        name = displayName,
                        mediaCountText = countText,
                        previewPhoto = list.first(),
                        icon = displayIcon,
                        onClick = { onAlbumClick(face) }
                    )
                }
            }
        }
    }
}

@Composable
fun FaceAlbumCard(
    name: String,
    mediaCountText: String,
    previewPhoto: PhotoEntity,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val resId = remember(previewPhoto.resourceName) {
        val id = context.resources.getIdentifier(previewPhoto.resourceName, "drawable", context.packageName)
        if (id != 0) id else R.drawable.ic_launcher_background
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("album_card_$name"),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = resId),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon, 
                        contentDescription = null, 
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = mediaCountText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

// -------------------------------------------------------------
// CLOUD BACKUP SCREEN
// -------------------------------------------------------------
@Composable
fun CloudSyncScreen(viewModel: GalleryViewModel) {
    val photos by viewModel.allPhotos.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val syncStats by viewModel.syncStats.collectAsState()
    val activeCloud by viewModel.activeBackupCloud.collectAsState()

    val unsyncedCount = remember(photos) { photos.count { !it.hasBackupSynced } }
    val syncedCount = remember(photos) { photos.count { it.hasBackupSynced } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Multi-Cloud Backup Sync",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Prevent duplication while synchronizing your media across devices safely.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Cloud Select Cards
        item {
            Text(
                "Connect Cloud Provider",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Pair("AuraCloud", Icons.Default.CloudSync),
                    Pair("Google Photos", Icons.Default.Photo),
                    Pair("OneDrive", Icons.Default.Cloud),
                    Pair("Dropbox", Icons.Default.FolderZip)
                ).forEach { (name, icon) ->
                    val isSelected = activeCloud.contains(name)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.changeBackupCloud("$name Storage") }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Status Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Backup & Device Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Connected Devices", style = MaterialTheme.typography.bodyMedium)
                        Text("3 Active (Emulator, Phone, Tablet)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Synced to Cloud", style = MaterialTheme.typography.bodyMedium)
                        Text("$syncedCount photos", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Unsynced Local Media", style = MaterialTheme.typography.bodyMedium)
                        Text("$unsyncedCount files", style = MaterialTheme.typography.bodyMedium, color = if (unsyncedCount > 0) Color.Yellow else Color.Green, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Sync Action Card
        item {
            if (isSyncing) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Uploading to $activeCloud...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${syncStats.first}/${syncStats.second}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { syncProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else if (unsyncedCount > 0) {
                Button(
                    onClick = { viewModel.runCloudSync() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_sync_cloud")
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sync Unbacked Media (${unsyncedCount} files)")
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Green.copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, Color.Green.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color.Green)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Cloud synchronization up to date. No duplicate files created.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Green,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STORAGE OPTIMIZER SCREEN (WITH MOCK DUPLICATE IMPORT SIMULATOR!)
// -------------------------------------------------------------
@Composable
fun StorageOptimizerScreen(viewModel: GalleryViewModel) {
    val photos by viewModel.allPhotos.collectAsState()

    val totalStorageUsed = remember(photos) {
        photos.sumOf { it.sizeBytes }
    }
    // Non-destructive editing saves having to copy files. Every edit version would physically duplicate a file.
    // We count edits, each saving ~2MB of physical duplicate files!
    val estimatedSavedStorage = remember(photos) {
        photos.sumOf { photo ->
            // Let's query how many edits exist. We can approximate or mock.
            // If the photo is currently filtered, it represents at least 1 saved physical copy!
            if (photo.currentFilter != null) 2000000L else 0L
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Aura Storage Optimizer",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Prevent file duplication and compress edits non-destructively.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Metrics Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Active Size", style = MaterialTheme.typography.labelMedium)
                        Text(
                            formatBytes(totalStorageUsed),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("5 high-res files", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("AI Saved Space", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            formatBytes(estimatedSavedStorage),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text("via delta tracking", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                }
            }
        }

        // Feature Highlight
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Smart Media Deduplication Engine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "The Deduplication Engine computes unique Content Hashes (MD5 hashes) of incoming media files. " +
                        "If you attempt to import a file with the same content structure, the engine blocks it immediately to save storage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Duplication Protection Simulator Panel
        item {
            Card(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = Color.PrimaryColor()
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Duplicate Block Simulator",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Test our automatic protection system by simulating different file imports:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.tryImportNewPhoto(
                                resName = "img_face_alice",
                                title = "Alice Alternate Name.jpg",
                                sizeBytes = 2400000L,
                                isFaceOf = "Alice"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_simulate_duplicate_alice"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simulate Importing Duplicate Alice Portrait")
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            viewModel.tryImportNewPhoto(
                                resName = "img_scenic_mountain",
                                title = "Wallpaper Copy.jpg",
                                sizeBytes = 4500000L
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_simulate_duplicate_mountain"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simulate Importing Duplicate Mountain Sunset")
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            // Simulate importing a genuinely unique new photo (not in db!)
                            val uniqueName = "img_scenic_lake_unique_${System.currentTimeMillis()}"
                            viewModel.tryImportNewPhoto(
                                resName = "img_scenic_lake", // we can reuse the resource name but mock a unique hash in our VM/Repo by appending timestamp or using custom parameters
                                title = "Unique Forest Lake.jpg",
                                sizeBytes = 3450000L
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.PrimaryColor()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_simulate_unique_import"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simulate Importing Unique Lake Photo")
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            viewModel.tryImportNewPhoto(
                                resName = "img_scenic_mountain",
                                title = "Simulated Vlog Clip.mp4",
                                sizeBytes = 19500000L,
                                mimeType = "video/mp4",
                                durationSeconds = 30
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_simulate_unique_video_import"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.VideoCall, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simulate Importing Unique Video")
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            viewModel.tryImportNewPhoto(
                                resName = "img_scenic_mountain",
                                title = "Vlog Clip Copy.mp4",
                                sizeBytes = 19500000L,
                                mimeType = "video/mp4",
                                durationSeconds = 30
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_simulate_duplicate_video_import"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simulate Importing Duplicate Video")
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// DYNAMIC PHOTO DETAILS MODAL WITH FILTERING & VERSION HISTORY
// -------------------------------------------------------------
@Composable
fun PhotoDetailsDialog(
    photo: PhotoEntity,
    versions: List<PhotoEditVersionEntity>,
    viewModel: GalleryViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val resId = remember(photo.resourceName) {
        val id = context.resources.getIdentifier(photo.resourceName, "drawable", context.packageName)
        if (id != 0) id else R.drawable.ic_launcher_background
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false // full screen
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                    val isVideo = photo.durationSeconds > 0 || photo.mimeType.startsWith("video/")
                    Text(
                        if (isVideo) "AI Video Player" else "AI Image Editor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { 
                            viewModel.deletePhoto(photo)
                            onDismiss()
                        },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete photo")
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Preview Card
                    item {
                        val isVideo = photo.durationSeconds > 0 || photo.mimeType.startsWith("video/")
                        
                        // Video playback states
                        var isPlaying by remember { mutableStateOf(false) }
                        var videoProgress by remember { mutableStateOf(0f) }
                        var isMuted by remember { mutableStateOf(false) }
                        var playbackSpeed by remember { mutableStateOf(1f) }
                        
                        val totalSeconds = if (photo.durationSeconds > 0) photo.durationSeconds else 15

                        LaunchedEffect(isPlaying, playbackSpeed) {
                            if (isPlaying) {
                                while (isPlaying) {
                                    kotlinx.coroutines.delay(100)
                                    videoProgress += (0.1f * playbackSpeed) / totalSeconds.toFloat()
                                    if (videoProgress >= 1f) {
                                        videoProgress = 0f // Loop
                                    }
                                }
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.2f)
                                .clip(RoundedCornerShape(16.dp)),
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Image(
                                    painter = painterResource(id = resId),
                                    contentDescription = photo.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    colorFilter = getColorFilterFor(photo.currentFilter)
                                )

                                if (isVideo) {
                                    // Ambient player vignette
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Black.copy(alpha = 0.4f),
                                                        Color.Transparent,
                                                        Color.Black.copy(alpha = 0.7f)
                                                    )
                                                )
                                            )
                                    )

                                    // Top Video Indicator Tag
                                    Row(
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .align(Alignment.TopStart),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(Color.White, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isPlaying) "PLAYING" else "PAUSED",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Active filter overlay label if applied to video
                                    photo.currentFilter?.let { filter ->
                                        Box(
                                            modifier = Modifier
                                                .padding(12.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                                .align(Alignment.TopEnd)
                                        ) {
                                            Text(
                                                "LUT: $filter",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Large center Play/Pause toggle
                                    IconButton(
                                        onClick = { isPlaying = !isPlaying },
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(56.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }

                                    // Custom control bar at the bottom
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomStart)
                                            .padding(12.dp)
                                    ) {
                                        // Slider track
                                        Slider(
                                            value = videoProgress,
                                            onValueChange = { videoProgress = it },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(20.dp),
                                            colors = SliderDefaults.colors(
                                                thumbColor = MaterialTheme.colorScheme.primary,
                                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                            )
                                        )
                                        
                                        // Row of actions
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = { isPlaying = !isPlaying },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = Color.White
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                val currentSeconds = (videoProgress * totalSeconds).toInt()
                                                Text(
                                                    text = "${formatDuration(currentSeconds)} / ${formatDuration(totalSeconds)}",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                // Speed toggle
                                                TextButton(
                                                    onClick = {
                                                        playbackSpeed = when (playbackSpeed) {
                                                            1f -> 1.5f
                                                            1.5f -> 2f
                                                            2f -> 0.5f
                                                            else -> 1f
                                                        }
                                                    },
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text(
                                                        text = "${playbackSpeed}x",
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                // Mute button
                                                IconButton(
                                                    onClick = { isMuted = !isMuted },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                                        contentDescription = null,
                                                        tint = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Standard Image layout
                                    // Active filter label overlay
                                    photo.currentFilter?.let { filter ->
                                        Box(
                                            modifier = Modifier
                                                .padding(12.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                                .align(Alignment.TopEnd)
                                        ) {
                                            Text(
                                                "Filter: $filter",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Metadata info
                    item {
                        val isVideo = photo.durationSeconds > 0 || photo.mimeType.startsWith("video/")
                        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                            Text(
                                text = photo.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            // Size and Media Type indicators row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isVideo) "VIDEO" else "IMAGE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = formatBytes(photo.sizeBytes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (isVideo) {
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${photo.durationSeconds}s @ 60fps",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = photo.faceTag?.let { "AI Category: $it" } ?: "AI Category: Uncategorized",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Unique Hash: ${photo.originalContentHash}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            if (isVideo) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Codec: H.264 / AVC | Audio: AAC Stereo (48kHz)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Non-destructive Edit Filter Bar
                    item {
                        Text(
                            "Apply Non-Destructive Filters",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val filtersList = listOf("Original", "Vintage", "Mono", "Warm Glow", "Cool Mist", "Cyberpunk")
                            items(filtersList) { filter ->
                                val isSelected = (photo.currentFilter == filter) || (photo.currentFilter == null && filter == "Original")
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(72.dp)
                                        .clickable { 
                                            if (filter == "Original") {
                                                viewModel.revertToOriginal()
                                            } else {
                                                viewModel.applyFilterToSelected(filter)
                                            }
                                        }
                                        .testTag("filter_option_$filter")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .border(
                                                width = 2.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = CircleShape
                                            )
                                    ) {
                                        Image(
                                            painter = painterResource(id = resId),
                                            contentDescription = filter,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            colorFilter = if (filter == "Original") null else getColorFilterFor(filter)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        filter,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // Version History Control
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Non-Destructive Version Control",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (versions.isNotEmpty()) {
                                TextButton(onClick = { viewModel.revertToOriginal() }) {
                                    Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Revert Original")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        if (versions.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No edits made. Currently displaying Original File (Storage optimal: 0 B additional space used).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                versions.forEach { version ->
                                    val formattedTime = remember(version.timestamp) {
                                        SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(version.timestamp))
                                    }
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (photo.currentFilter == version.filterName) MaterialTheme.colorScheme.surfaceVariant
                                            else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    "Version ${version.versionNumber} (${version.filterName} Filter)",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    "Applied at $formattedTime (0 B physical replication)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            if (photo.currentFilter != version.filterName) {
                                                Button(
                                                    onClick = { viewModel.revertToVersion(version) },
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("Revert", style = MaterialTheme.typography.labelMedium)
                                                }
                                            } else {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = Color.Green, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Active", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.Green)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// HELPER COMPOSABLES & DIALOGS
// -------------------------------------------------------------
@Composable
fun DuplicateWarningDialog(
    duplicatePhoto: PhotoEntity,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Duplicate Media Blocked", color = MaterialTheme.colorScheme.error)
            }
        },
        text = {
            Column {
                Text(
                    text = "A file with the identical content hash has been detected in the library:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Existing File: ${duplicatePhoto.title}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("Size: ${formatBytes(duplicatePhoto.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
                        Text("MD5 Hash: ${duplicatePhoto.originalContentHash}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Import has been safely canceled to prevent storage bloat. Space saved: ${formatBytes(duplicatePhoto.sizeBytes)}!",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Awesome, Thanks!")
            }
        },
        modifier = Modifier.testTag("dialog_duplicate_warning")
    )
}

@Composable
fun ImportSuccessDialog(
    photo: PhotoEntity,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import Successful", color = Color.Green)
            }
        },
        text = {
            Column {
                Text(
                    text = "A new unique file has been analyzed and added to the gallery safely:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Card {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(photo.title, fontWeight = FontWeight.Bold)
                        Text("Hash: ${photo.originalContentHash}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        Text("Space allocated: ${formatBytes(photo.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        },
        modifier = Modifier.testTag("dialog_import_success")
    )
}

// Byte utility formatting
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

// Color and Theme helper definitions
fun getColorFilterFor(filterName: String?): ColorFilter? {
    return when (filterName) {
        "Vintage" -> {
            val sepiaMatrix = ColorMatrix(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f,     0f,     0f,     1f, 0f
            ))
            ColorFilter.colorMatrix(sepiaMatrix)
        }
        "Mono" -> {
            val matrix = ColorMatrix().apply { setToSaturation(0f) }
            ColorFilter.colorMatrix(matrix)
        }
        "Warm Glow" -> {
            val warmMatrix = ColorMatrix(floatArrayOf(
                1.2f, 0f, 0f, 0f, 10f,
                0f, 1.0f, 0f, 0f, 0f,
                0f, 0f, 0.8f, 0f, -10f,
                0f, 0f, 0f, 1.0f, 0f
            ))
            ColorFilter.colorMatrix(warmMatrix)
        }
        "Cool Mist" -> {
            val coolMatrix = ColorMatrix(floatArrayOf(
                0.8f, 0f, 0f, 0f, -10f,
                0f, 0.9f, 0f, 0f, 0f,
                0f, 0f, 1.2f, 0f, 10f,
                0f, 0f, 0f, 1.0f, 0f
            ))
            ColorFilter.colorMatrix(coolMatrix)
        }
        "Cyberpunk" -> {
            val cyberpunkMatrix = ColorMatrix(floatArrayOf(
                1.1f, 0f, 0.2f, 0f, 20f,
                0.1f, 0.8f, 0f, 0f, 10f,
                0.2f, 0f, 1.3f, 0f, 30f,
                0f, 0f, 0f, 1.0f, 0f
            ))
            ColorFilter.colorMatrix(cyberpunkMatrix)
        }
        else -> null
    }
}

// Hex colors / Custom color utilities
fun Color.Companion.PrimaryColor() = Color(0xFF6750A4)
fun Color.Companion.PrimaryContainerColor() = Color(0xFFEADDFF)
