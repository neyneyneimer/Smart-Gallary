package ai.sakana.aura.gallery.data.cloud

import java.io.InputStream
import java.io.OutputStream

/**
 * The ONLY provider-specific seam. Everything else (manifest, incremental sync, conflicts, retry,
 * integrity checks) is shared in CloudSyncEngine. A provider adapter implements this against its
 * SDK or REST API. Implementations throw CloudRateLimitException on HTTP 429-style limits and
 * CloudAuthException on auth failures.
 */
interface CloudApi {
    /** True when the provider can look a file up by SHA-256 (TeraBox preupload) instead of uploading it. */
    val supportsHashLookup: Boolean get() = false

    suspend fun connect(credentials: CloudCredentials?): Result<Unit>
    suspend fun disconnect()
    suspend fun isConnected(): Boolean

    /** Creates "Aura AI Gallery" with Photos/, Videos/, Metadata/ if missing. Idempotent. */
    suspend fun ensureFolders()

    suspend fun readManifest(): String?
    suspend fun writeManifest(json: String)

    /** [openStream] is called again on every retry so a failed attempt never reuses a half-read stream. */
    suspend fun upload(request: UploadRequest, openStream: () -> InputStream): RemoteFile

    /** Streams the file into [sink]; must NOT close it. */
    suspend fun download(remoteId: String, sink: OutputStream)

    suspend fun rename(remoteId: String, newName: String)
    suspend fun delete(remoteId: String)
    suspend fun findByHash(sha256: String): RemoteFile? = null
}

/** Local device side of a sync: reading originals and writing downloaded media. */
interface LocalMediaGateway {
    fun openRead(contentUri: String): InputStream?

    /**
     * Writes a downloaded file into the device library, restoring name/date/bucket/favorite from [entry],
     * and returns its new content URI. Must leave NOTHING behind if [writer] throws.
     */
    suspend fun saveDownloaded(entry: ManifestEntry, fileNameOverride: String?, writer: suspend (OutputStream) -> Unit): String

    suspend fun deleteDownloaded(contentUri: String)
    suspend fun renameLocal(contentUri: String, newName: String)

    /** Moves to the app's reversible Trash; never a permanent delete. */
    suspend fun trashLocal(contentUri: String)

    fun freeSpaceBytes(): Long
}
