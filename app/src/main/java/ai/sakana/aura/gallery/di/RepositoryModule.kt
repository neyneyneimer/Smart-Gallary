package ai.sakana.aura.gallery.di

import ai.sakana.aura.gallery.data.repository.RoomAiSetRepository
import ai.sakana.aura.gallery.data.repository.RoomMediaRecognitionRepository
import ai.sakana.aura.gallery.data.repository.RoomMediaRepository
import ai.sakana.aura.gallery.domain.repository.AiSetRepository
import ai.sakana.aura.gallery.domain.repository.MediaRecognitionRepository
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

    @Binds
    @Singleton
    abstract fun bindMediaRecognitionRepository(implementation: RoomMediaRecognitionRepository): MediaRecognitionRepository

    @Binds
    @Singleton
    abstract fun bindAiSetRepository(implementation: RoomAiSetRepository): AiSetRepository
}
