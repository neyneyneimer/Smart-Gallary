package ai.sakana.aura.gallery.data.cloud

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import java.io.InputStream
import java.io.OutputStream

/**
 * Device side of cloud sync. Account 1 supplies [trash] (their MoveToTrashUseCase, reversible) and
 * [onRestored] (to persist favorites / DB-only metadata for a restored item).
 * Renaming or replacing media owned by other apps can throw SecurityException on Android 11+;
 * the engine records that as a failed item instead of crashing.
 */
class MediaStoreLocalGateway(
    context: Context,
    private val trash: suspend (contentUri: String) -> Unit,
    private val onRestored: suspend (contentUri: String, entry: ManifestEntry) -> Unit = { _, _ -> },
) : LocalMediaGateway {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    override fun openRead(contentUri: String): InputStream? = try {
        resolver.openInputStream(Uri.parse(contentUri))
    } catch (_: java.io.FileNotFoundException) { null } catch (_: SecurityException) { null }

    override suspend fun saveDownloaded(entry: ManifestEntry, fileNameOverride: String?, writer: suspend (OutputStream) -> Unit): String {
        val video = entry.mediaType == "VIDEO"
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileNameOverride ?: entry.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, entry.mimeType ?: if (video) "video/mp4" else "image/jpeg")
            if (entry.dateTakenEpochMillis > 0L) put(MediaStore.MediaColumns.DATE_TAKEN, entry.dateTakenEpochMillis)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, (if (video) "Movies/" else "Pictures/") + CloudFolders.ROOT)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values) ?: throw java.io.IOException("Could not create the restored file.")
        try {
            val out = resolver.openOutputStream(uri, "w") ?: throw java.io.IOException("Could not write the restored file.")
            out.use { writer(it) }
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
        onRestored(uri.toString(), entry)
        return uri.toString()
    }

    override suspend fun deleteDownloaded(contentUri: String) { resolver.delete(Uri.parse(contentUri), null, null) }

    override suspend fun renameLocal(contentUri: String, newName: String) {
        resolver.update(Uri.parse(contentUri), ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }, null, null)
    }

    override suspend fun trashLocal(contentUri: String) = trash(contentUri)

    override fun freeSpaceBytes(): Long = StatFs(Environment.getExternalStorageDirectory().path).availableBytes
}
