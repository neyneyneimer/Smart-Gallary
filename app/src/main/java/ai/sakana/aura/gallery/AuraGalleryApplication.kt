package ai.sakana.aura.gallery

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import ai.sakana.aura.gallery.diagnostics.DiagnosticFileTree
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
    }

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context.applicationContext)
            .crossfade(true)
            .build()
}
