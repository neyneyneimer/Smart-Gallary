package ai.sakana.aura.gallery.di

import android.content.Context
import androidx.room.Room
import ai.sakana.aura.gallery.data.local.AuraGalleryDatabase
import ai.sakana.aura.gallery.data.local.MediaItemDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AuraGalleryDatabase =
        Room.databaseBuilder(context, AuraGalleryDatabase::class.java, "aura_gallery.db")
            .addMigrations(AuraGalleryDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideMediaItemDao(database: AuraGalleryDatabase): MediaItemDao = database.mediaItemDao()
}
