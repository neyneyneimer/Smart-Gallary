package ai.sakana.aura.gallery.data.repository

import androidx.room.withTransaction
import ai.sakana.aura.gallery.data.local.AiSetEntity
import ai.sakana.aura.gallery.data.local.AiSetMembershipEntity
import ai.sakana.aura.gallery.data.local.AuraGalleryDatabase
import ai.sakana.aura.gallery.data.local.MediaRecognitionDao
import ai.sakana.aura.gallery.domain.recognition.AiSet
import ai.sakana.aura.gallery.domain.recognition.AiSetRules
import ai.sakana.aura.gallery.domain.repository.AiSetRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomAiSetRepository @Inject constructor(
    private val database: AuraGalleryDatabase,
    private val dao: MediaRecognitionDao,
) : AiSetRepository {
    override fun observeAiSets(): Flow<List<AiSet>> = dao.observeAiSetMembers().map { rows ->
        rows.groupBy { it.setId }.values.map { members ->
            val first = members.first()
            AiSet(
                id = first.setId,
                name = first.name,
                mediaUris = members.mapNotNull { it.contentUri }.distinct(),
                createdAtEpochMillis = first.createdAtEpochMillis,
                updatedAtEpochMillis = first.updatedAtEpochMillis,
            )
        }
    }

    override suspend fun saveAiSet(name: String, mediaUris: List<String>): String {
        val safeName = AiSetRules.normalizeName(name)
        val membership = AiSetRules.requireSavableMembership(mediaUris)
        val now = System.currentTimeMillis()
        val setId = UUID.randomUUID().toString()
        database.withTransaction {
            dao.insertAiSet(
                AiSetEntity(
                    id = setId,
                    name = safeName,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
            dao.insertAiSetMembers(membership.map { uri ->
                AiSetMembershipEntity(setId = setId, contentUri = uri, addedAtEpochMillis = now)
            })
        }
        return setId
    }

    override suspend fun renameAiSet(setId: String, name: String) {
        val safeName = AiSetRules.normalizeName(name)
        check(dao.renameAiSet(setId, safeName, System.currentTimeMillis()) == 1) {
            "This AI Set no longer exists."
        }
    }

    override suspend fun mergeAiSets(targetSetId: String, sourceSetId: String) {
        require(targetSetId != sourceSetId) { "Choose two different AI Sets to merge." }
        database.withTransaction {
            check(dao.countAiSet(targetSetId) == 1 && dao.countAiSet(sourceSetId) == 1) {
                "One of these AI Sets no longer exists."
            }
            val now = System.currentTimeMillis()
            dao.copyAiSetMembers(sourceSetId, targetSetId, now)
            check(dao.touchAiSet(targetSetId, now) == 1) { "The destination AI Set no longer exists." }
            check(dao.deleteAiSet(sourceSetId) == 1) { "The source AI Set no longer exists." }
        }
    }

    override suspend fun removeMediaFromAiSet(setId: String, contentUri: String) {
        database.withTransaction {
            check(dao.removeAiSetMember(setId, contentUri) == 1) {
                "This item is no longer in the selected AI Set."
            }
            check(dao.touchAiSet(setId, System.currentTimeMillis()) == 1) {
                "This AI Set no longer exists."
            }
        }
    }

    override suspend fun deleteAiSet(setId: String) {
        check(dao.deleteAiSet(setId) == 1) { "This AI Set no longer exists." }
    }
}
