package ai.sakana.aura.gallery

import android.app.Application
import android.content.Context
import android.os.Process
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import ai.sakana.aura.gallery.diagnostics.DiagnosticFileTree
import ai.sakana.aura.gallery.diagnostics.CrashRecoveryStore
import ai.sakana.aura.gallery.diagnostics.NoOpCrashReporter
import ai.sakana.aura.gallery.diagnostics.PerformanceMetrics
import java.io.File
import timber.log.Timber

@HiltAndroidApp
class AuraGalleryApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.plant(DiagnosticFileTree(File(filesDir, "diagnostics/aura-debug.log")))
            Timber.i("Aura debug diagnostics initialized")
        }
        PerformanceMetrics.markProcessStart()
        installUncaughtExceptionHandler()
    }

    private fun installUncaughtExceptionHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Timber.e(throwable, "Uncaught exception on thread %s", thread.name)
            NoOpCrashReporter.recordUncaughtException(thread, throwable)
            runCatching { CrashRecoveryStore(this).markFatalCrash() }
                .onFailure { Timber.e(it, "Unable to persist crash recovery marker") }
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context.applicationContext)
            .crossfade(true)
            .build()
}
