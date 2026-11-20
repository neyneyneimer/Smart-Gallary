package ai.sakana.aura.gallery.data.cloud

import ai.sakana.aura.gallery.data.deduplication.Sha256Hasher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.security.DigestOutputStream
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException

/**
 * Provider-agnostic bidirectional sync over a [CloudApi]:
 * manifest diffing, incremental upload (size/mtime first, SHA-256 only when needed), import,
 * conflict handling, batching + backoff, and SHA-256 integrity checks after every download.
 *
 * Contract: nothing is ever permanently deleted on either side. Local removals go to Trash.
 */
class CloudSyncEngine(
    private val api: CloudApi,
    private val gateway: LocalMediaGateway,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val retry: RetryPolicy = RetryPolicy(),
) {
    private class UploadJob(val local: LocalMediaSnapshot, val sha: String, val replaces: ManifestEntry?)
    private class DownloadJob(val entry: ManifestEntry, val nameOverride: String?, val trashAfter: String?)

    private class Plan {
        val uploads = ArrayList<UploadJob>()
        val downloads = ArrayList<DownloadJob>()
        val metadata = ArrayList<ManifestEntry>()
        val remoteRenames = ArrayList<Pair<ManifestEntry, String>>()
        val localRenames = ArrayList<Pair<String, String>>()
        val trashes = ArrayList<String>()
        val conflicts = ArrayList<ConflictRecord>()
        val pending = ArrayList<PendingConflict>()
        val failures = ArrayList<FailedItem>()
        val syncedKeys = HashSet<String>()
        var unchanged = 0
        var skippedVideos = 0
    }

    // ---------------------------------------------------------------- sync

    suspend fun sync(
        locals: List<LocalMediaSnapshot>,
        options: SyncOptions = SyncOptions(),
        onProgress: (CloudProgress) -> Unit = {},
    ): SyncReport {
        api.ensureFolders()
        val manifest = readManifest()
        val plan = Plan()
        onProgress(CloudProgress(CloudPhase.PREPARING, null, 0, locals.size, 0))
        buildPlan(locals, manifest, options, plan, onProgress)
        return execute(plan, manifest, options, onProgress)
    }

    /** Applies the user's choice for a conflict that an earlier sync left pending (ASK mode). */
    suspend fun resolveConflict(conflict: PendingConflict, mode: ConflictMode, options: SyncOptions = SyncOptions()): SyncReport {
        require(mode != ConflictMode.ASK) { "Choose a concrete resolution." }
        api.ensureFolders()
        val manifest = readManifest()
        val plan = Plan()
        applyConflict(conflict, mode, plan)
        return execute(plan, manifest, options, {})
    }

    private suspend fun buildPlan(
        locals: List<LocalMediaSnapshot>,
        manifest: BackupManifest,
        options: SyncOptions,
        plan: Plan,
        onProgress: (CloudProgress) -> Unit,
    ) {
        val byKey = manifest.entries.associateBy { it.contentUri }
        val byHash = manifest.entries.filter { it.sha256Hash != null }.groupBy { it.sha256Hash!! }
        val byName = manifest.entries.groupBy { it.displayName to it.bucketName }
        val matched = HashSet<String>()
        val queuedHashes = HashSet<String>()
        val mode = options.conflictMode

        suspend fun conflict(type: ConflictType, local: LocalMediaSnapshot?, entry: ManifestEntry?, sha: String?) {
            val pending = PendingConflict(type, local, entry, sha)
            var chosen = mode
            if (mode == ConflictMode.ASK) chosen = options.askResolver?.invoke(pending) ?: ConflictMode.ASK
            applyConflict(pending, chosen, plan)
        }

        locals.forEachIndexed { index, local ->
            currentCoroutineContext().ensureActive()
            onProgress(CloudProgress(CloudPhase.HASHING, local.displayName, index, locals.size, 0))
            val entry = byKey[local.contentUri]
            if (entry != null) {
                matched += entry.contentUri
                if (local.dateModifiedEpochMillis <= entry.localModifiedTime && local.sizeBytes == entry.sizeBytes) {
                    plan.unchanged++; plan.syncedKeys += entry.contentUri
                    return@forEachIndexed
                }
                val sha = hashOf(local, plan) ?: return@forEachIndexed
                when {
                    sha == entry.sha256Hash -> {
                        plan.metadata += entry.copy(localModifiedTime = local.dateModifiedEpochMillis, isFavorite = local.isFavorite, bucketName = local.bucketName)
                        plan.syncedKeys += entry.contentUri
                    }
                    else -> { plan.uploads += UploadJob(local, sha, entry); plan.syncedKeys += entry.contentUri }
                }
                return@forEachIndexed
            }
            val sha = hashOf(local, plan) ?: return@forEachIndexed
            val hashMatch = byHash[sha]?.firstOrNull()
            val nameMatch = byName[local.displayName to local.bucketName]?.firstOrNull()
            when {
                hashMatch != null -> {
                    matched += hashMatch.contentUri
                    if (hashMatch.displayName == local.displayName) { plan.unchanged++; plan.syncedKeys += hashMatch.contentUri }
                    else conflict(ConflictType.SAME_HASH_DIFFERENT_NAME, local, hashMatch, sha)
                }
                nameMatch != null -> { matched += nameMatch.contentUri; conflict(ConflictType.SAME_NAME_DIFFERENT_HASH, local, nameMatch, sha) }
                local.contentUri in options.previouslySyncedKeys -> conflict(ConflictType.DELETED_IN_CLOUD_EXISTS_LOCALLY, local, null, sha)
                sha !in queuedHashes -> {
                    queuedHashes += sha
                    plan.uploads += UploadJob(local, sha, null)
                }
                else -> plan.unchanged++ // identical bytes already queued from another local file
            }
        }

        for (entry in manifest.entries) {
            if (entry.contentUri in matched) continue
            if (entry.contentUri in options.previouslySyncedKeys) {
                conflict(ConflictType.DELETED_LOCALLY_EXISTS_IN_CLOUD, null, entry, entry.sha256Hash)
            } else if (options.direction == SyncDirection.TWO_WAY) {
                if (entry.mediaType == "VIDEO" && options.skipVideoDownloads) plan.skippedVideos++
                else plan.downloads += DownloadJob(entry, null, null)
            }
        }
    }

    private fun applyConflict(conflict: PendingConflict, mode: ConflictMode, plan: Plan) {
        val action = CloudConflictResolver.plan(conflict.type, mode)
        plan.conflicts += ConflictRecord(conflict, mode, action)
        val local = conflict.local
        val entry = conflict.entry
        val sha = conflict.localSha256
        // Keep tracking both sides so the conflict is not mistaken for a plain add/delete next time.
        entry?.let { plan.syncedKeys += it.contentUri }
        local?.let { plan.syncedKeys += it.contentUri }
        when (action) {
            ConflictAction.DEFER -> plan.pending += conflict
            ConflictAction.LEAVE_AS_IS -> Unit
            ConflictAction.UPLOAD_LOCAL_OVERWRITE -> plan.uploads += UploadJob(local!!, sha!!, entry)
            ConflictAction.REUPLOAD -> plan.uploads += UploadJob(local!!, sha!!, null)
            ConflictAction.RENAME_CLOUD_AND_UPLOAD -> {
                plan.remoteRenames += entry!! to CloudConflictResolver.cloudCopyName(entry.displayName)
                plan.uploads += UploadJob(local!!, sha!!, null)
            }
            ConflictAction.RENAME_REMOTE_TO_LOCAL_NAME -> plan.remoteRenames += entry!! to local!!.displayName
            ConflictAction.RENAME_LOCAL_TO_REMOTE_NAME -> plan.localRenames += local!!.contentUri to entry!!.displayName
            ConflictAction.DOWNLOAD_RESTORE -> plan.downloads += DownloadJob(entry!!, null, null)
            ConflictAction.DOWNLOAD_AND_TRASH_LOCAL -> plan.downloads += DownloadJob(entry!!, null, local!!.contentUri)
            ConflictAction.TRASH_LOCAL -> { plan.trashes += local!!.contentUri; plan.syncedKeys -= local.contentUri }
        }
    }

    private fun hashOf(local: LocalMediaSnapshot, plan: Plan): String? {
        local.sha256Hash?.let { return it }
        return try {
            gateway.openRead(local.contentUri)?.use { Sha256Hasher.hash(it) }
                ?: run { plan.failures += FailedItem(local.displayName, "Original could not be opened"); null }
        } catch (error: IOException) {
            plan.failures += FailedItem(local.displayName, error.message ?: "Read failed"); null
        } catch (error: SecurityException) {
            plan.failures += FailedItem(local.displayName, error.message ?: "Access denied"); null
        }
    }

    // ------------------------------------------------------------- execute

    private suspend fun execute(plan: Plan, manifest: BackupManifest, options: SyncOptions, onProgress: (CloudProgress) -> Unit): SyncReport {
        val entries = LinkedHashMap<String, ManifestEntry>()
        manifest.entries.forEach { entries[it.contentUri] = it }
        var uploaded = 0
        var downloaded = 0
        var deduped = 0
        var bytesUp = 0L
        var bytesDown = 0L
        val total = plan.uploads.size + plan.downloads.size

        for (e in plan.metadata) entries[e.contentUri] = e

        for ((entry, newName) in plan.remoteRenames) {
            try {
                retry.run { api.rename(entry.remoteId, newName) }
                entries[entry.contentUri] = entry.copy(displayName = newName)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { plan.failures += FailedItem(entry.displayName, error.message ?: "Rename failed") }
        }
        for ((uri, newName) in plan.localRenames) {
            try { gateway.renameLocal(uri, newName) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { plan.failures += FailedItem(newName, error.message ?: "Local rename failed") }
        }

        // Uploads in batches; the manifest is checkpointed after each batch so a crash loses at most one batch.
        val batchSize = options.batchSize.coerceAtLeast(1)
        plan.uploads.chunked(batchSize).forEach { batch ->
            for (job in batch) {
                currentCoroutineContext().ensureActive()
                onProgress(CloudProgress(CloudPhase.UPLOADING, job.local.displayName, uploaded + deduped, total, bytesUp))
                try {
                    val existingFromHash = if (job.replaces == null && api.supportsHashLookup) retry.run { api.findByHash(job.sha) } else null
                    val remote = existingFromHash ?: retry.run {
                        api.upload(requestFor(job)) { gateway.openRead(job.local.contentUri) ?: throw IOException("Original could not be opened") }
                    }
                    if (existingFromHash != null) deduped++ else { uploaded++; bytesUp += job.local.sizeBytes }
                    job.replaces?.let { entries.remove(it.contentUri) }
                    entries[job.local.contentUri] = ManifestEntry(
                        contentUri = job.local.contentUri, remoteId = remote.remoteId, displayName = job.local.displayName,
                        mimeType = job.local.mimeType, mediaType = job.local.mediaType, sizeBytes = job.local.sizeBytes,
                        localModifiedTime = job.local.dateModifiedEpochMillis, remoteModifiedTime = remote.modifiedTimeEpochMillis,
                        sha256Hash = job.sha, dateTakenEpochMillis = job.local.dateTakenEpochMillis,
                        isFavorite = job.local.isFavorite, bucketName = job.local.bucketName,
                    )
                    plan.syncedKeys += job.local.contentUri
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) { plan.failures += FailedItem(job.local.displayName, error.message ?: "Upload failed") }
            }
            writeManifest(entries.values.toList())
        }

        for (job in plan.downloads) {
            currentCoroutineContext().ensureActive()
            onProgress(CloudProgress(CloudPhase.DOWNLOADING, job.entry.displayName, downloaded, total, bytesDown))
            try {
                downloadVerified(job.entry, job.nameOverride)
                downloaded++; bytesDown += job.entry.sizeBytes
                plan.syncedKeys += job.entry.contentUri
                job.trashAfter?.let { gateway.trashLocal(it) } // only after the verified copy is safely saved
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { plan.failures += FailedItem(job.entry.displayName, error.message ?: "Download failed") }
        }

        for (uri in plan.trashes) {
            try { gateway.trashLocal(uri) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { plan.failures += FailedItem(uri, error.message ?: "Trash failed") }
        }

        onProgress(CloudProgress(CloudPhase.FINALIZING, null, total, total, bytesUp + bytesDown))
        val changed = plan.uploads.isNotEmpty() || plan.metadata.isNotEmpty() || plan.remoteRenames.isNotEmpty()
        if (changed && plan.uploads.isEmpty()) writeManifest(entries.values.toList())

        return SyncReport(
            uploaded = uploaded, downloaded = downloaded, metadataUpdated = plan.metadata.size, unchanged = plan.unchanged,
            dedupedByHash = deduped, skippedVideos = plan.skippedVideos, bytesUploaded = bytesUp, bytesDownloaded = bytesDown,
            conflicts = plan.conflicts, pendingConflicts = plan.pending, failures = plan.failures, syncedKeys = plan.syncedKeys,
        )
    }

    private fun requestFor(job: UploadJob) = UploadRequest(
        displayName = job.local.displayName, mimeType = job.local.mimeType, mediaType = job.local.mediaType,
        sizeBytes = job.local.sizeBytes, sha256Hash = job.sha, dateTakenEpochMillis = job.local.dateTakenEpochMillis,
        isFavorite = job.local.isFavorite, bucketName = job.local.bucketName, existingRemoteId = job.replaces?.remoteId,
    )

    /** Downloads into the device library and verifies SHA-256; a mismatch removes the saved copy. */
    private suspend fun downloadVerified(entry: ManifestEntry, nameOverride: String?): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val uri = retry.run {
            digest.reset()
            gateway.saveDownloaded(entry, nameOverride) { out -> api.download(entry.remoteId, DigestOutputStream(out, digest)) }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        val expected = entry.sha256Hash
        if (expected != null && !expected.equals(actual, ignoreCase = true)) {
            gateway.deleteDownloaded(uri)
            throw CloudException("Integrity check failed for ${entry.displayName}: the downloaded file does not match its SHA-256.")
        }
        return uri
    }

    // -------------------------------------------------------------- import

    suspend fun importFromCloud(
        options: ImportOptions,
        locals: List<LocalMediaSnapshot>,
        onProgress: (CloudProgress) -> Unit = {},
    ): ImportReport {
        api.ensureFolders()
        val manifest = readManifest()
        val failures = ArrayList<FailedItem>()
        val synced = HashSet<String>()
        var present = 0
        var skippedVideos = 0
        val wanted = manifest.entries.filter { inScope(it, options) }.filter { entry ->
            val here = locals.any { l ->
                l.contentUri == entry.contentUri ||
                    (l.sha256Hash != null && l.sha256Hash == entry.sha256Hash) ||
                    (l.displayName == entry.displayName && l.bucketName == entry.bucketName && l.sizeBytes == entry.sizeBytes)
            }
            if (here) { present++; synced += entry.contentUri }
            !here
        }.filter { entry ->
            if (options.skipVideos && entry.mediaType == "VIDEO") { skippedVideos++; false } else true
        }
        var downloaded = 0
        var bytes = 0L
        wanted.forEachIndexed { index, entry ->
            currentCoroutineContext().ensureActive()
            onProgress(CloudProgress(CloudPhase.DOWNLOADING, entry.displayName, index, wanted.size, bytes))
            try {
                downloadVerified(entry, null)
                downloaded++; bytes += entry.sizeBytes; synced += entry.contentUri
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { failures += FailedItem(entry.displayName, error.message ?: "Download failed") }
        }
        return ImportReport(downloaded, present, skippedVideos, bytes, failures, synced)
    }

    private fun inScope(entry: ManifestEntry, options: ImportOptions): Boolean = when (options.scope) {
        ImportScope.FULL -> true
        ImportScope.PHOTOS_ONLY -> entry.mediaType != "VIDEO"
        ImportScope.FAVORITES_ONLY -> entry.isFavorite
        ImportScope.SINCE_DATE -> entry.dateTakenEpochMillis >= options.sinceEpochMillis
    }

    // ------------------------------------------------------------ backups

    suspend fun listBackups(): List<BackupInfo> {
        api.ensureFolders()
        val manifest = readManifest()
        if (manifest.entries.isEmpty()) return emptyList()
        return listOf(
            BackupInfo(
                id = "manifest", updatedAtEpochMillis = manifest.updatedAtEpochMillis, itemCount = manifest.entries.size,
                totalBytes = manifest.entries.sumOf { it.sizeBytes },
                photoCount = manifest.entries.count { it.mediaType != "VIDEO" },
                videoCount = manifest.entries.count { it.mediaType == "VIDEO" },
                favoriteCount = manifest.entries.count { it.isFavorite },
            ),
        )
    }

    // ----------------------------------------------------------- manifest

    private suspend fun readManifest(): BackupManifest {
        val text = retry.run { api.readManifest() } ?: return BackupManifest()
        return BackupManifestJson.fromJson(text)
    }

    private suspend fun writeManifest(entries: List<ManifestEntry>) {
        val json = BackupManifestJson.toJson(BackupManifest(updatedAtEpochMillis = clock(), entries = entries))
        retry.run { api.writeManifest(json) }
    }
}
