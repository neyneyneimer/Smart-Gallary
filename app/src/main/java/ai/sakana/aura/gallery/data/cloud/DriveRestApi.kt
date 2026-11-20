package ai.sakana.aura.gallery.data.cloud

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

/** Supplies Google OAuth access tokens (drive.file scope). The Sign-In UI lives in the app, not here. */
interface AccessTokenProvider {
    suspend fun signIn(): Result<Unit>
    suspend fun signOut()
    suspend fun isSignedIn(): Boolean
    /** A currently valid bearer token; throws CloudAuthException when the user must sign in again. */
    suspend fun accessToken(): String
}

/**
 * Google Drive v3 over plain HTTPS (no Google client library). Layout: "Aura AI Gallery"/{Photos,Videos,Metadata}.
 * Uploads use the resumable protocol so large videos stream instead of loading into memory.
 */
class DriveRestApi(
    private val tokens: AccessTokenProvider,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : CloudApi {
    private var folderIds: Map<String, String> = emptyMap()

    override suspend fun connect(credentials: CloudCredentials?) = tokens.signIn()
    override suspend fun disconnect() { folderIds = emptyMap(); tokens.signOut() }
    override suspend fun isConnected() = tokens.isSignedIn()

    override suspend fun ensureFolders() {
        if (folderIds.isNotEmpty()) return
        val root = findOrCreateFolder(CloudFolders.ROOT, null)
        folderIds = mapOf(CloudFolders.ROOT to root) + listOf(CloudFolders.PHOTOS, CloudFolders.VIDEOS, CloudFolders.METADATA)
            .associateWith { findOrCreateFolder(it, root) }
    }

    override suspend fun readManifest(): String? {
        val id = findFileId(CloudFolders.MANIFEST_FILE, folder(CloudFolders.METADATA)) ?: return null
        val out = java.io.ByteArrayOutputStream()
        download(id, out)
        return out.toString(Charsets.UTF_8.name())
    }

    override suspend fun writeManifest(json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val metadataFolder = folder(CloudFolders.METADATA)
        val existing = findFileId(CloudFolders.MANIFEST_FILE, metadataFolder)
        val metadata: Map<String, Any?> = if (existing == null) {
            mapOf("name" to CloudFolders.MANIFEST_FILE, "mimeType" to "application/json", "parents" to listOf(metadataFolder))
        } else {
            mapOf("mimeType" to "application/json")
        }
        resumableUpload(existing, metadata, "application/json", bytes.size.toLong()) { ByteArrayInputStream(bytes) }
    }

    override suspend fun upload(request: UploadRequest, openStream: () -> InputStream): RemoteFile {
        val parent = folder(if (request.mediaType == "VIDEO") CloudFolders.VIDEOS else CloudFolders.PHOTOS)
        val properties = mapOf(
            "sha256" to request.sha256Hash, "dateTaken" to request.dateTakenEpochMillis.toString(),
            "isFavorite" to request.isFavorite.toString(), "bucketName" to request.bucketName,
        )
        val metadata: Map<String, Any?> = if (request.existingRemoteId == null) {
            mapOf("name" to request.displayName, "parents" to listOf(parent), "appProperties" to properties)
        } else {
            mapOf("name" to request.displayName, "appProperties" to properties)
        }
        return resumableUpload(request.existingRemoteId, metadata, request.mimeType ?: "application/octet-stream", request.sizeBytes, openStream)
    }

    override suspend fun download(remoteId: String, sink: OutputStream): Unit = withContext(io) {
        val connection = open("https://www.googleapis.com/drive/v3/files/$remoteId?alt=media", "GET")
        try {
            verify(connection.responseCode, connection)
            connection.inputStream.use { it.copyTo(sink, 64 * 1024) }
            Unit
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun rename(remoteId: String, newName: String) {
        json("PATCH", "https://www.googleapis.com/drive/v3/files/$remoteId?fields=id", mapOf("name" to newName))
    }

    override suspend fun delete(remoteId: String): Unit = withContext(io) {
        val connection = open("https://www.googleapis.com/drive/v3/files/$remoteId", "DELETE")
        try { verify(connection.responseCode, connection) } finally { connection.disconnect() }
    }

    // ------------------------------------------------------------ helpers

    private fun folder(name: String): String = folderIds[name] ?: throw CloudException("Drive folders are not ready.")

    private suspend fun findOrCreateFolder(name: String, parent: String?): String {
        val query = "name='" + name.replace("'", "\\'") + "' and mimeType='application/vnd.google-apps.folder' and trashed=false" +
            (if (parent != null) " and '$parent' in parents" else " and 'root' in parents")
        val found = list(query)
        if (found.isNotEmpty()) return found.first()
        val body = mutableMapOf<String, Any?>("name" to name, "mimeType" to "application/vnd.google-apps.folder")
        if (parent != null) body["parents"] = listOf(parent)
        val created = json("POST", "https://www.googleapis.com/drive/v3/files?fields=id", body) as Map<*, *>
        return created["id"] as? String ?: throw CloudException("Drive did not return a folder id.")
    }

    private suspend fun findFileId(name: String, parent: String): String? =
        list("name='" + name.replace("'", "\\'") + "' and '$parent' in parents and trashed=false").firstOrNull()

    private suspend fun list(query: String): List<String> {
        val url = "https://www.googleapis.com/drive/v3/files?fields=files(id)&pageSize=10&q=" + URLEncoder.encode(query, "UTF-8")
        val result = json("GET", url, null) as? Map<*, *> ?: return emptyList()
        return (result["files"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.get("id") as? String }
    }

    private suspend fun json(method: String, url: String, body: Map<String, Any?>?): Any? = withContext(io) {
        val connection = open(url, method)
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.outputStream.use { it.write(MiniJson.write(body).toByteArray(Charsets.UTF_8)) }
            }
            verify(connection.responseCode, connection)
            val text = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            if (text.isBlank()) null else MiniJson.parse(text)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun resumableUpload(
        existingId: String?,
        metadata: Map<String, Any?>,
        mimeType: String,
        size: Long,
        openStream: () -> InputStream,
    ): RemoteFile = withContext(io) {
        val endpoint = if (existingId == null) "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id,modifiedTime,size"
        else "https://www.googleapis.com/upload/drive/v3/files/$existingId?uploadType=resumable&fields=id,modifiedTime,size"
        val start = open(endpoint, if (existingId == null) "POST" else "PATCH")
        val sessionUrl = try {
            start.doOutput = true
            start.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            start.setRequestProperty("X-Upload-Content-Type", mimeType)
            start.setRequestProperty("X-Upload-Content-Length", size.toString())
            start.outputStream.use { it.write(MiniJson.write(metadata).toByteArray(Charsets.UTF_8)) }
            verify(start.responseCode, start)
            start.getHeaderField("Location") ?: throw CloudException("Drive did not return an upload session.")
        } finally {
            start.disconnect()
        }
        val put = open(sessionUrl, "PUT")
        try {
            put.doOutput = true
            put.setFixedLengthStreamingMode(size)
            put.setRequestProperty("Content-Type", mimeType)
            openStream().use { input -> put.outputStream.use { input.copyTo(it, 64 * 1024) } }
            verify(put.responseCode, put)
            val reply = MiniJson.parse(put.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }) as? Map<*, *>
                ?: throw CloudException("Drive returned an unreadable upload reply.")
            RemoteFile(
                remoteId = reply["id"] as? String ?: throw CloudException("Drive upload reply had no id."),
                modifiedTimeEpochMillis = (reply["modifiedTime"] as? String)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L,
                sizeBytes = (reply["size"] as? String)?.toLongOrNull() ?: size,
            )
        } finally {
            put.disconnect()
        }
    }

    private suspend fun open(url: String, method: String): HttpURLConnection {
        val token = tokens.accessToken()
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = if (method == "PATCH") "POST" else method
            if (method == "PATCH") setRequestProperty("X-HTTP-Method-Override", "PATCH")
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 20_000
            readTimeout = 60_000
        }
    }

    private fun verify(code: Int, connection: HttpURLConnection) {
        when {
            code in 200..299 -> Unit
            code == 401 -> throw CloudAuthException("Google Drive rejected the sign-in. Reconnect and try again.")
            code == 429 -> throw CloudRateLimitException()
            code == 403 && connection.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty().contains("ateLimit") -> throw CloudRateLimitException()
            code == 403 -> throw CloudAuthException("Google Drive denied access (check storage quota and permissions).")
            else -> throw IOException("Google Drive returned HTTP $code")
        }
    }
}
