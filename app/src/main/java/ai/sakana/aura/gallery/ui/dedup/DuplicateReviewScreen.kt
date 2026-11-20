package ai.sakana.aura.gallery.ui.dedup

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import ai.sakana.aura.gallery.data.deduplication.ExactDuplicateGroup
import ai.sakana.aura.gallery.data.deduplication.HammingDistance
import ai.sakana.aura.gallery.data.deduplication.SimilarGroup
import ai.sakana.aura.gallery.data.deduplication.SimilarMember
import ai.sakana.aura.gallery.data.local.MediaItemEntity
import ai.sakana.aura.gallery.design.AuraTokens
import ai.sakana.aura.gallery.design.JetBrainsMono
import ai.sakana.aura.gallery.design.Manrope
import coil3.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

/** Plain state holder. Account 1 maps its ViewModel state into this and wires the callbacks. */
data class DuplicateReviewUiState(
    val isScanning: Boolean = false,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val progressLabel: String = "Hashing",
    val exactGroups: List<ExactDuplicateGroup> = emptyList(),
    val similarGroups: List<SimilarGroup> = emptyList(),
    val hasScannedExact: Boolean = false,
    val hasScannedSimilar: Boolean = false,
    val errorMessage: String? = null,
) {
    val totalWastedBytes: Long get() = exactGroups.sumOf { it.wastedBytes }
}

enum class DuplicateTab(val label: String) { EXACT("Exact Duplicates"), SIMILAR("Similar Photos") }

/**
 * Duplicate review with two tabs: "Exact Duplicates" (SHA-256) and "Similar Photos" (dHash).
 * The user picks which copy to keep; the other copies move to the app's reversible Trash only after
 * an explicit confirmation. Nothing is auto-deleted.
 */
@Composable
fun DuplicateReviewScreen(
    state: DuplicateReviewUiState,
    onBack: () -> Unit,
    onScanExact: () -> Unit,
    onScanSimilar: () -> Unit,
    onMoveToTrash: (contentUris: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(DuplicateTab.EXACT) }
    val hasScanned = if (tab == DuplicateTab.EXACT) state.hasScannedExact else state.hasScannedSimilar
    Column(modifier.fillMaxSize().background(AuraTokens.Void)) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 20.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = AuraTokens.Paper) }
            Column(Modifier.weight(1f)) {
                Text("Duplicates", color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    if (tab == DuplicateTab.EXACT) "EXACT · SHA-256 BYTE MATCH" else "SIMILAR · PERCEPTUAL HASH",
                    color = AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 1.sp,
                )
            }
            TextButton(onClick = { if (tab == DuplicateTab.EXACT) onScanExact() else onScanSimilar() }, enabled = !state.isScanning) {
                Text(if (hasScanned) "RESCAN" else "SCAN", color = if (state.isScanning) AuraTokens.PaperFaint else AuraTokens.Safelight, fontFamily = JetBrainsMono, fontSize = 11.sp)
            }
        }
        TabRow(selectedTabIndex = tab.ordinal, containerColor = AuraTokens.Void, contentColor = AuraTokens.Safelight) {
            DuplicateTab.values().forEach { entry ->
                Tab(selected = tab == entry, onClick = { tab = entry }, text = { Text(entry.label, fontFamily = Manrope, fontSize = 12.sp) })
            }
        }
        if (state.isScanning) {
            val fraction = if (state.progressTotal > 0) state.progressDone / state.progressTotal.toFloat() else 0f
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = AuraTokens.Safelight, trackColor = AuraTokens.Surface2)
            Text(state.progressLabel + " " + state.progressDone + " of " + state.progressTotal, Modifier.padding(16.dp), color = AuraTokens.PaperMuted, fontFamily = JetBrainsMono, fontSize = 10.sp)
        }
        state.errorMessage?.let {
            Text(it, Modifier.fillMaxWidth().background(AuraTokens.Surface1).padding(12.dp), color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 11.sp)
        }
        when (tab) {
            DuplicateTab.EXACT -> when {
                !state.hasScannedExact && !state.isScanning -> EmptyNote("No scan has run yet. Tap SCAN to look for byte-identical photos and videos. Nothing is deleted automatically.")
                state.hasScannedExact && !state.isScanning && state.exactGroups.isEmpty() -> EmptyNote("No exact duplicates found.")
                state.exactGroups.isNotEmpty() -> {
                    Text(
                        state.exactGroups.size.toString() + " GROUPS · " + Formatter.formatShortFileSize(context, state.totalWastedBytes) + " RECLAIMABLE",
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = AuraTokens.PaperMuted, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 1.sp,
                    )
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.exactGroups, key = { it.sha256 }) { group ->
                            CopyGroupCard(
                                title = group.count.toString() + " IDENTICAL COPIES · " + Formatter.formatShortFileSize(context, group.wastedBytes) + " WASTED",
                                members = group.items.map { SimilarMember(it, 0) },
                                recommendedKeepUri = group.recommendedKeepUri,
                                showDistance = false,
                                groupKey = group.sha256,
                                onMoveToTrash = onMoveToTrash,
                            )
                        }
                    }
                }
            }
            DuplicateTab.SIMILAR -> when {
                !state.hasScannedSimilar && !state.isScanning -> EmptyNote("No scan has run yet. Tap SCAN to find resized, recompressed or lightly edited versions of the same photo. Review each group before removing anything.")
                state.hasScannedSimilar && !state.isScanning && state.similarGroups.isEmpty() -> EmptyNote("No similar photos found.")
                state.similarGroups.isNotEmpty() -> {
                    Text(
                        state.similarGroups.size.toString() + " GROUPS",
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = AuraTokens.PaperMuted, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 1.sp,
                    )
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.similarGroups, key = { it.recommendedKeepUri }) { group ->
                            CopyGroupCard(
                                title = group.count.toString() + (if (group.isNearExact) " NEAR-IDENTICAL" else " SIMILAR") + " · MAX DISTANCE " + group.maxDistance,
                                members = group.members,
                                recommendedKeepUri = group.recommendedKeepUri,
                                showDistance = true,
                                groupKey = group.recommendedKeepUri,
                                onMoveToTrash = onMoveToTrash,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(message: String) {
    Text(message, Modifier.padding(20.dp), color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 12.sp, lineHeight = 18.sp)
}

@Composable
private fun CopyGroupCard(
    title: String,
    members: List<SimilarMember>,
    recommendedKeepUri: String,
    showDistance: Boolean,
    groupKey: String,
    onMoveToTrash: (List<String>) -> Unit,
) {
    var keepUri by remember(groupKey) { mutableStateOf(recommendedKeepUri) }
    var confirming by remember(groupKey) { mutableStateOf(false) }
    val toTrash = members.filter { it.item.contentUri != keepUri }.map { it.item.contentUri }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).background(AuraTokens.Surface1).padding(12.dp)) {
        Text(title, color = AuraTokens.Paper, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(members, key = { it.item.contentUri }) { member ->
                CopyTile(
                    item = member.item,
                    isKept = member.item.contentUri == keepUri,
                    isRecommended = member.item.contentUri == recommendedKeepUri,
                    distanceLabel = if (showDistance) distanceLabel(member.distanceToKeep, member.item.contentUri == recommendedKeepUri) else null,
                    onSelect = { keepUri = member.item.contentUri },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { confirming = true }, enabled = toTrash.isNotEmpty()) {
            Text("MOVE " + toTrash.size + " OTHER COPIES TO TRASH", color = AuraTokens.Safelight, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 0.7.sp)
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = AuraTokens.Surface2,
            title = { Text("Move " + toTrash.size + " copies to Trash?", color = AuraTokens.Paper, fontFamily = Manrope, fontWeight = FontWeight.Bold) },
            text = { Text("The selected copy stays in your library. The others go to Trash, where they can be restored.", color = AuraTokens.PaperMuted, fontFamily = Manrope, fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = { confirming = false; onMoveToTrash(toTrash) }) { Text("MOVE TO TRASH", color = AuraTokens.Safelight, fontFamily = JetBrainsMono, fontSize = 11.sp) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("CANCEL", color = AuraTokens.PaperMuted, fontFamily = JetBrainsMono, fontSize = 11.sp) }
            },
        )
    }
}

private fun distanceLabel(distance: Int, isReference: Boolean): String {
    if (isReference) return "REFERENCE"
    val kind = when (HammingDistance.classify(distance)) {
        HammingDistance.Match.DUPLICATE -> "DUPLICATE"
        HammingDistance.Match.SIMILAR -> "SIMILAR"
        HammingDistance.Match.UNRELATED -> "LINKED"
    }
    return "DISTANCE $distance · $kind"
}

@Composable
private fun CopyTile(item: MediaItemEntity, isKept: Boolean, isRecommended: Boolean, distanceLabel: String?, onSelect: () -> Unit) {
    val context = LocalContext.current
    val border = if (isKept) BorderStroke(2.dp, AuraTokens.Safelight) else BorderStroke(1.dp, AuraTokens.Hairline)
    Column(Modifier.width(150.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).border(border).clickable(onClick = onSelect).background(AuraTokens.Surface2)) {
        Box(Modifier.fillMaxWidth().height(120.dp).background(AuraTokens.Void)) {
            AsyncImage(item.contentUri.toUri(), contentDescription = item.displayName ?: "Duplicate copy", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.padding(8.dp)) {
            Text(if (isKept) "KEEP" else "REMOVE", color = if (isKept) AuraTokens.Safelight else AuraTokens.PaperFaint, fontFamily = JetBrainsMono, fontSize = 9.sp, letterSpacing = 1.sp)
            Text(item.displayName ?: "Unnamed", color = AuraTokens.Paper, fontFamily = Manrope, fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(Formatter.formatShortFileSize(context, item.sizeBytes) + " · " + item.width + "×" + item.height, color = AuraTokens.PaperMuted, fontFamily = JetBrainsMono, fontSize = 9.sp)
            Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.dateTakenEpochMillis)), color = AuraTokens.PaperMuted, fontFamily = JetBrainsMono, fontSize = 9.sp)
            if (distanceLabel != null) Text(distanceLabel, color = AuraTokens.AuraB, fontFamily = JetBrainsMono, fontSize = 9.sp, letterSpacing = 0.8.sp)
            if (isRecommended) Text("RECOMMENDED", color = AuraTokens.AuraA, fontFamily = JetBrainsMono, fontSize = 9.sp, letterSpacing = 0.8.sp)
        }
    }
}

@Preview(widthDp = 380, heightDp = 700)
@Composable
private fun DuplicateReviewScreenPreview() {
    fun e(id: Long, name: String, w: Int, h: Int) = MediaItemEntity(
        id = id, volumeName = "external", mediaStoreId = id, contentUri = "content://media/external/images/media/$id",
        displayName = name, mimeType = "image/jpeg", sizeBytes = 3_400_000, dateTakenEpochMillis = 1_700_000_000_000,
        width = w, height = h, sourceType = "MEDIA_STORE",
    )
    val items = listOf(e(1, "IMG_0001.jpg", 4000, 3000), e(2, "IMG_0001 (1).jpg", 4000, 3000), e(3, "copy.jpg", 4000, 3000))
    DuplicateReviewScreen(
        state = DuplicateReviewUiState(
            hasScannedExact = true,
            exactGroups = listOf(ExactDuplicateGroup("abc123", items, items.first().contentUri)),
            similarGroups = listOf(SimilarGroup(listOf(SimilarMember(items[0], 0), SimilarMember(items[1], 3), SimilarMember(items[2], 9)), items.first().contentUri)),
            hasScannedSimilar = true,
        ),
        onBack = {}, onScanExact = {}, onScanSimilar = {}, onMoveToTrash = {},
    )
}
