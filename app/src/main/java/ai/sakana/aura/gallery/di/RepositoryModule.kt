package ai.sakana.aura.gallery.di

import ai.sakana.aura.gallery.data.repository.RoomMediaRepository
import ai.sakana.aura.gallery.domain.repository.MediaRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMediaRepository(implementation: RoomMediaRepository): MediaRepository
}
