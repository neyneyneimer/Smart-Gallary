package ai.sakana.aura.gallery.diagnostics

import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

/** Stores only app-owned Timber messages in private internal storage. */
internal class DiagnosticFileTree(private val logFile: File) : Timber.Tree() {
    @Suppress("LogNotTimber") // Timber must not be called from inside its own Tree failure handler.
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val priorityLabel = when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> priority.toString()
        }
        val entry = buildString {
            append(Instant.now())
            append(' ')
            append(priorityLabel)
            append('/')
            append(tag ?: "Aura")
            append(": ")
            append(message.take(MAX_MESSAGE_CHARS))
            if (t != null) {
                appendLine()
                append(t.stackTraceToString().take(MAX_STACK_CHARS))
            }
            appendLine()
        }

        synchronized(lock) {
            try {
                logFile.parentFile?.mkdirs()
                if (logFile.exists() && logFile.length() + entry.toByteArray(Charsets.UTF_8).size > MAX_FILE_BYTES) {
                    val rotated = File(logFile.parentFile, "aura-debug.1.log")
                    rotated.delete()
                    if (!logFile.renameTo(rotated)) logFile.delete()
                }
                FileOutputStream(logFile, true).bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(entry)
                }
            } catch (error: Exception) {
                // Never allow a diagnostics write failure to crash the application or recurse into Timber.
                Log.w("AuraDiagnostics", "Unable to append diagnostic log", error)
            }
        }
    }

    private companion object {
        const val MAX_FILE_BYTES = 512L * 1024L
        const val MAX_MESSAGE_CHARS = 8_000
        const val MAX_STACK_CHARS = 16_000
        val lock = Any()
    }
}
