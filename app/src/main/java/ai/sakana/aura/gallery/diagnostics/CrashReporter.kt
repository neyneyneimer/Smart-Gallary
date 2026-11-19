package ai.sakana.aura.gallery.diagnostics

/** Local integration seam for a future crash reporting provider. */
internal interface CrashReporter {
    fun recordUncaughtException(thread: Thread, throwable: Throwable)
}

/** No network requests or crash-report uploads are performed by this implementation. */
internal object NoOpCrashReporter : CrashReporter {
    override fun recordUncaughtException(thread: Thread, throwable: Throwable) = Unit
}
