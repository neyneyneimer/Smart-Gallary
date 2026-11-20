package ai.sakana.aura.gallery.data.export

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.OutputStream

/** MediaStore operations behind string handles so the atomic flow is unit-testable without Android. */
interface PendingMediaSink {
    /** Creates a record hidden from other apps (IS_PENDING=1) and returns its handle. */
    fun createPending(displayName: String, mimeType: String, relativePath: String): String?
    fun openOutput(handle: String): OutputStream?
    /** IS_PENDING=0: the file becomes visible in the system gallery. */
    fun finalize(handle: String)
    /** Removes the record and any partial bytes. */
    fun discard(handle: String)
}

object AtomicMediaWriter {
    /**
     * 1) insert with IS_PENDING=1, 2) write bytes, 3) [postProcess] (e.g. EXIF) while still pending,
     * 4) IS_PENDING=0. Any failure deletes the pending record so no half-written file is ever visible.
     */
    fun write(
        sink: PendingMediaSink,
        displayName: String,
        mimeType: String,
        relativePath: String,
        writer: (OutputStream) -> Unit,
        postProcess: (handle: String) -> Unit = {},
        onProgress: (Int) -> Unit = {},
    ): String {
        val handle = sink.createPending(displayName, mimeType, relativePath)
            ?: error("The edited copy could not be created in device storage.")
        try {
            onProgress(55)
            val output = sink.openOutput(handle) ?: error("The edited copy could not be written.")
            output.use(writer)
            onProgress(85)
            postProcess(handle)
            onProgress(95)
            sink.finalize(handle)
            onProgress(100)
            return handle
        } catch (error: Exception) {
            runCatching { sink.discard(handle) }
            throw error
        }
    }
}

class ResolverPendingSink(private val resolver: ContentResolver) : PendingMediaSink {
    override fun createPending(displayName: String, mimeType: String, relativePath: String): String? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.toString()
    }

    override fun openOutput(handle: String): OutputStream? = resolver.openOutputStream(Uri.parse(handle), "w")

    override fun finalize(handle: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            resolver.update(Uri.parse(handle), ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
    }

    override fun discard(handle: String) {
        resolver.delete(Uri.parse(handle), null, null)
    }
}
