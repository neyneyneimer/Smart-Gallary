package ai.sakana.aura.gallery

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class AuraGalleryApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context.applicationContext)
            .crossfade(true)
            .build()
}
