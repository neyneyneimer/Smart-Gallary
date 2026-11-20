package ai.sakana.aura.gallery.domain.repository

import ai.sakana.aura.gallery.domain.recognition.AiSet
import kotlinx.coroutines.flow.Flow

interface AiSetRepository {
    fun observeAiSets(): Flow<List<AiSet>>
    suspend fun saveAiSet(name: String, mediaUris: List<String>): String
    suspend fun renameAiSet(setId: String, name: String)
    suspend fun mergeAiSets(targetSetId: String, sourceSetId: String)
    suspend fun removeMediaFromAiSet(setId: String, contentUri: String)
    suspend fun deleteAiSet(setId: String)
}
