package ai.sakana.aura.gallery.diagnostics

import android.database.sqlite.SQLiteException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/** Bridges local Room failures from repositories to a safe, user-visible gallery error state. */
@Singleton
class GalleryErrorReporter @Inject constructor() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun report(operation: String, error: SQLiteException) {
        Timber.e(error, "Room database operation failed: %s", operation)
        _message.value = USER_MESSAGE
    }

    fun clear() {
        _message.value = null
    }

    private companion object {
        const val USER_MESSAGE = "Aura couldn't access its local media library. Your original photos and videos are unchanged. Try again."
    }
}
