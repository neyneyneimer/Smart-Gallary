package ai.sakana.aura.gallery.data.deduplication

import android.content.ContentResolver
import android.net.Uri
import java.io.InputStream

/** Opens the raw bytes behind a media content URI. Returns null when the item cannot be read. */
fun interface MediaByteSource {
    fun open(contentUri: String): InputStream?
}

class ContentResolverByteSource(private val resolver: ContentResolver) : MediaByteSource {
    override fun open(contentUri: String): InputStream? = try {
        resolver.openInputStream(Uri.parse(contentUri))
    } catch (_: SecurityException) {
        null
    } catch (_: java.io.FileNotFoundException) {
        null
    }
}
