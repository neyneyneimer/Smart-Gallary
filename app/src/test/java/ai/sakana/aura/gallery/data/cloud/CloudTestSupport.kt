package ai.sakana.aura.gallery.data.cloud

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

fun sha256Of(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** In-memory cloud provider. [rateLimitUploads] makes the first N upload calls fail with a 429-style error. */
class FakeCloudApi(var connected: Boolean = true, override val supportsHashLookup: Boolean = false) : CloudApi {
    val files = LinkedHashMap<String, ByteArray>()
    val names = LinkedHashMap<String, String>()
    var manifest: String? = null
    var rateLimitUploads = 0
    var uploadCalls = 0
    var manifestWrites = 0
    val renamed = mutableListOf<Pair<String, String>>()
    var corruptDownloads = false
    private var nextId = 1

    override suspend fun connect(credentials: CloudCredentials?): Result<Unit> { connected = true; return Result.success(Unit) }
    override suspend fun disconnect() { connected = false }
    override suspend fun isConnected() = connected
    override suspend fun ensureFolders() {}
    override suspend fun readManifest() = manifest
    override suspend fun writeManifest(json: String) { manifest = json; manifestWrites++ }

    override suspend fun upload(request: UploadRequest, openStream: () -> InputStream): RemoteFile {
        uploadCalls++
        if (rateLimitUploads > 0) { rateLimitUploads--; throw CloudRateLimitException() }
        val bytes = openStream().use { it.readBytes() }
        val id = request.existingRemoteId ?: run {
            var candidate: String
            do { candidate = "r${nextId++}" } while (candidate in files)
            candidate
        }
        files[id] = bytes
        names[id] = request.displayName
        return RemoteFile(id, 1_000L + uploadCalls, bytes.size.toLong())
    }

    override suspend fun download(remoteId: String, sink: OutputStream) {
        val bytes = files[remoteId] ?: error("missing $remoteId")
        sink.write(if (corruptDownloads) bytes.reversedArray() else bytes)
    }

    override suspend fun rename(remoteId: String, newName: String) { names[remoteId] = newName; renamed += remoteId to newName }
    override suspend fun delete(remoteId: String) { files.remove(remoteId) }
    override suspend fun findByHash(sha256: String): RemoteFile? =
        files.entries.firstOrNull { sha256Of(it.value) == sha256 }?.let { RemoteFile(it.key, 1L, it.value.size.toLong()) }
}

class FakeGateway : LocalMediaGateway {
    val localFiles = LinkedHashMap<String, ByteArray>()
    val saved = mutableListOf<String>()
    val trashed = mutableListOf<String>()
    val renamedLocal = mutableListOf<Pair<String, String>>()
    var freeBytes = Long.MAX_VALUE
    private var next = 1

    override fun openRead(contentUri: String): InputStream? = localFiles[contentUri]?.let { ByteArrayInputStream(it) }

    override suspend fun saveDownloaded(entry: ManifestEntry, fileNameOverride: String?, writer: suspend (OutputStream) -> Unit): String {
        val out = ByteArrayOutputStream()
        writer(out)                       // a throwing writer leaves nothing behind
        val uri = "content://local/new${next++}"
        localFiles[uri] = out.toByteArray()
        saved += (fileNameOverride ?: entry.displayName)
        return uri
    }

    override suspend fun deleteDownloaded(contentUri: String) { localFiles.remove(contentUri) }
    override suspend fun renameLocal(contentUri: String, newName: String) { renamedLocal += contentUri to newName }
    override suspend fun trashLocal(contentUri: String) { trashed += contentUri }
    override fun freeSpaceBytes() = freeBytes
}

fun snapshot(uri: String, name: String, bytes: ByteArray, modified: Long = 100L, mediaType: String = "IMAGE", favorite: Boolean = false, hash: String? = null) =
    LocalMediaSnapshot(uri, name, "image/jpeg", mediaType, bytes.size.toLong(), 50L, modified, favorite, "Camera", hash)

fun entry(uri: String, remoteId: String, name: String, bytes: ByteArray, local: Long = 100L, remote: Long = 100L, mediaType: String = "IMAGE", favorite: Boolean = false) =
    ManifestEntry(uri, remoteId, name, "image/jpeg", mediaType, bytes.size.toLong(), local, remote, sha256Of(bytes), 50L, favorite, "Camera")
