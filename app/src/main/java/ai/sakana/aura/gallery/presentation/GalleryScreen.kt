package ai.sakana.aura.gallery.presentation

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import ai.sakana.aura.gallery.domain.model.MediaItem
import ai.sakana.aura.gallery.platform.MediaAccessLevel
import ai.sakana.aura.gallery.platform.MediaPermissionPolicy

@Composable
fun GalleryRoute(viewModel: GalleryViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var access by remember { mutableStateOf(readMediaAccess(context)) }
    val items by viewModel.mediaItems.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50),
        onResult = viewModel::addPickedMedia,
    )
    val permissionRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { access = readMediaAccess(context) },
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

    val launchPicker = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val requestAccess = {
        permissionRequest.launch(
            MediaPermissionPolicy.requiredPermissions(Build.VERSION.SDK_INT).toTypedArray(),
        )
    }

    Scaffold(
        containerColor = Color(0xFF101014),
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("AURA AI", color = Color(0xFFAAA7B4), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Photos", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = launchPicker) { Text("Add photos", color = Color(0xFFD4B6FF)) }
            }
        },
        bottomBar = {
            Surface(color = Color(0xFF19191F), tonalElevation = 4.dp) {
                Button(
                    onClick = launchPicker,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp).height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD4B6FF),
                        contentColor = Color(0xFF21172E),
                    ),
                ) {
                    Text("+   Add photos", fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (access == MediaAccessLevel.PARTIAL) {
                Surface(
                    color = Color(0xFF292333),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Showing selected photos", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Aura can only see the photos you selected. You can add more at any time.",
                            color = Color(0xFFBDB6C8),
                            fontSize = 12.sp,
                        )
                        TextButton(onClick = requestAccess, contentPadding = PaddingValues(0.dp)) {
                            Text("Manage photo access", color = Color(0xFFD4B6FF))
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Surface(
                    color = Color(0xFF40272C),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            errorMessage.orEmpty(),
                            color = Color(0xFFFFD9DE),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
                    }
                }
            }

            if (access == MediaAccessLevel.DENIED && items.isEmpty()) {
                FirstRunContent(onChoosePhotos = launchPicker, onAllowAccess = requestAccess)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Recently added", color = Color(0xFFBDBAC6), fontSize = 13.sp)
                    Text(items.size.toString() + " items", color = Color(0xFF8D8998), fontSize = 12.sp)
                }
                if (items.isEmpty() && isRefreshing) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFD4B6FF))
                    }
                } else if (items.isEmpty()) {
                    EmptyGallery(onChoosePhotos = launchPicker)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 112.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(items, key = MediaItem::contentUri) { item ->
                            MediaTile(item = item, onFavoriteClick = { viewModel.toggleFavorite(item.contentUri) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FirstRunContent(onChoosePhotos: () -> Unit, onAllowAccess: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(86.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF292333)),
            contentAlignment = Alignment.Center,
        ) { Text("▧", color = Color(0xFFD4B6FF), fontSize = 38.sp) }
        Spacer(Modifier.height(24.dp))
        Text("Your photos, together", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Choose photos to add, or allow Aura to show your device gallery. You stay in control of what the app can see.",
            color = Color(0xFFAAA7B4),
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onChoosePhotos,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFD4B6FF),
                contentColor = Color(0xFF21172E),
            ),
        ) { Text("Choose photos", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onAllowAccess) { Text("Allow gallery access", color = Color(0xFFE1D4F5)) }
        Spacer(Modifier.height(18.dp))
        Text("You can change access any time in Android settings.", color = Color(0xFF777380), fontSize = 12.sp)
    }
}

@Composable
private fun EmptyGallery(onChoosePhotos: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No photos found", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Choose photos to add them to Aura.", color = Color(0xFFAAA7B4), fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onChoosePhotos) { Text("Choose photos", color = Color(0xFFD4B6FF)) }
    }
}

@Composable
private fun MediaTile(item: MediaItem, onFavoriteClick: () -> Unit) {
    Box(modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(5.dp)).background(Color(0xFF29282F))) {
        AsyncImage(
            model = item.contentUri.toUri(),
            contentDescription = item.displayName ?: "Photo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Surface(
            color = Color(0x990E0E12),
            shape = CircleShape,
            modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(30.dp),
        ) {
            IconButton(onClick = onFavoriteClick) {
                Text(
                    if (item.isFavorite) "♥" else "♡",
                    color = if (item.isFavorite) Color(0xFFFFA8C1) else Color.White,
                    fontSize = 18.sp,
                )
            }
        }
        if (!item.displayName.isNullOrBlank()) {
            Text(
                item.displayName,
                color = Color.White,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Color(0x88000000)).padding(horizontal = 5.dp, vertical = 3.dp),
            )
        }
    }
}

private fun readMediaAccess(context: Context): MediaAccessLevel {
    val granted = MediaPermissionPolicy.requiredPermissions(Build.VERSION.SDK_INT)
        .filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        .toSet()
    return MediaPermissionPolicy.resolveAccess(Build.VERSION.SDK_INT, granted)
}
