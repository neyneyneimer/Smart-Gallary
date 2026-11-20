package ai.sakana.aura.gallery.data.cloud

enum class CloudProviderType(val displayName: String) {
    GOOGLE_DRIVE("Google Drive"),
    MEGA("MEGA"),
    TERABOX("TeraBox"),
    NONE("None"),
}

enum class ConflictMode { KEEP_DEVICE, KEEP_CLOUD, KEEP_BOTH, ASK }

enum class ConflictType {
    SAME_NAME_DIFFERENT_HASH,
    SAME_HASH_DIFFERENT_NAME,
    DELETED_LOCALLY_EXISTS_IN_CLOUD,
    DELETED_IN_CLOUD_EXISTS_LOCALLY,
}

enum class ImportScope { FULL, PHOTOS_ONLY, FAVORITES_ONLY, SINCE_DATE }

enum class CloudPhase { PREPARING, HASHING, UPLOADING, DOWNLOADING, FINALIZING }

/** Same folder layout on every provider. */
object CloudFolders {
    const val ROOT = "Aura AI Gallery"
    const val PHOTOS = "Photos"
    const val VIDEOS = "Videos"
    const val METADATA = "Metadata"
    const val MANIFEST_FILE = "backup_manifest.json"
}

open class CloudException(message: String, cause: Throwable? = null) : Exception(message, cause)
class CloudRateLimitException(message: String = "Rate limited by the cloud provider") : CloudException(message)
class CloudAuthException(message: String = "Not signed in to the cloud provider") : CloudException(message)

data class CloudCredentials(val email: String? = null, val password: String? = null)

/** What the device knows about one local item. [sha256Hash] may be null until hashed. */
data class LocalMediaSnapshot(
    val contentUri: String,
    val displayName: String,
    val mimeType: String?,
    val mediaType: String,
    val sizeBytes: Long,
    val dateTakenEpochMillis: Long,
    val dateModifiedEpochMillis: Long,
    val isFavorite: Boolean,
    val bucketName: String,
    val sha256Hash: String? = null,
)

/** One backed-up file as recorded in Metadata/backup_manifest.json. */
data class ManifestEntry(
    val contentUri: String,
    val remoteId: String,
    val displayName: String,
    val mimeType: String?,
    val mediaType: String,
    val sizeBytes: Long,
    val localModifiedTime: Long,
    val remoteModifiedTime: Long,
    val sha256Hash: String?,
    val dateTakenEpochMillis: Long,
    val isFavorite: Boolean,
    val bucketName: String,
)

data class BackupManifest(
    val version: Int = 1,
    val updatedAtEpochMillis: Long = 0L,
    val entries: List<ManifestEntry> = emptyList(),
)

data class UploadRequest(
    val displayName: String,
    val mimeType: String?,
    val mediaType: String,
    val sizeBytes: Long,
    val sha256Hash: String,
    val dateTakenEpochMillis: Long,
    val isFavorite: Boolean,
    val bucketName: String,
    val existingRemoteId: String? = null,
)

data class RemoteFile(val remoteId: String, val modifiedTimeEpochMillis: Long, val sizeBytes: Long)

data class PendingConflict(
    val type: ConflictType,
    val local: LocalMediaSnapshot?,
    val entry: ManifestEntry?,
    val localSha256: String?,
)

enum class ConflictAction {
    UPLOAD_LOCAL_OVERWRITE,
    DOWNLOAD_AND_TRASH_LOCAL,
    RENAME_CLOUD_AND_UPLOAD,
    RENAME_REMOTE_TO_LOCAL_NAME,
    RENAME_LOCAL_TO_REMOTE_NAME,
    DOWNLOAD_RESTORE,
    REUPLOAD,
    TRASH_LOCAL,
    LEAVE_AS_IS,
    DEFER,
}

data class ConflictRecord(val conflict: PendingConflict, val mode: ConflictMode, val action: ConflictAction)

data class FailedItem(val name: String, val message: String)

data class CloudProgress(
    val phase: CloudPhase,
    val currentName: String?,
    val done: Int,
    val total: Int,
    val bytesTransferred: Long,
)

enum class SyncDirection { TWO_WAY, UP_ONLY }

data class SyncOptions(
    val conflictMode: ConflictMode = ConflictMode.KEEP_DEVICE,
    val direction: SyncDirection = SyncDirection.TWO_WAY,
    /** Manifest keys this device already knew about at the last sync; used to detect deletions. */
    val previouslySyncedKeys: Set<String> = emptySet(),
    /** Set when free space is below the threshold (see BackupPolicy). */
    val skipVideoDownloads: Boolean = false,
    val batchSize: Int = 10,
    /** Called per conflict when [conflictMode] is ASK. Return null to leave it unresolved. */
    val askResolver: (suspend (PendingConflict) -> ConflictMode?)? = null,
)

data class ImportOptions(
    val scope: ImportScope = ImportScope.FULL,
    val sinceEpochMillis: Long = 0L,
    val skipVideos: Boolean = false,
)

data class SyncReport(
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val metadataUpdated: Int = 0,
    val unchanged: Int = 0,
    val dedupedByHash: Int = 0,
    val skippedVideos: Int = 0,
    val bytesUploaded: Long = 0L,
    val bytesDownloaded: Long = 0L,
    val conflicts: List<ConflictRecord> = emptyList(),
    val pendingConflicts: List<PendingConflict> = emptyList(),
    val failures: List<FailedItem> = emptyList(),
    val syncedKeys: Set<String> = emptySet(),
)

data class ImportReport(
    val downloaded: Int = 0,
    val alreadyPresent: Int = 0,
    val skippedVideos: Int = 0,
    val bytesDownloaded: Long = 0L,
    val failures: List<FailedItem> = emptyList(),
    val syncedKeys: Set<String> = emptySet(),
)

data class BackupInfo(
    val id: String,
    val updatedAtEpochMillis: Long,
    val itemCount: Int,
    val totalBytes: Long,
    val photoCount: Int,
    val videoCount: Int,
    val favoriteCount: Int,
)

data class CloudStatus(
    val providerType: CloudProviderType,
    val isConnected: Boolean,
    val lastSyncEpochMillis: Long,
    val syncedCount: Int,
    val isSyncing: Boolean,
    val lastError: String?,
)

data class CloudSyncState(
    val lastSyncEpochMillis: Long = 0L,
    val syncedKeys: Set<String> = emptySet(),
    val syncedCount: Int = 0,
    val lastError: String? = null,
)
