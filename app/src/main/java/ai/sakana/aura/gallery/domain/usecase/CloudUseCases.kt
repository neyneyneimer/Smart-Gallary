package ai.sakana.aura.gallery.domain.usecase

import ai.sakana.aura.gallery.data.cloud.BackupInfo
import ai.sakana.aura.gallery.data.cloud.BackupPolicy
import ai.sakana.aura.gallery.data.cloud.CloudAuthException
import ai.sakana.aura.gallery.data.cloud.CloudProgress
import ai.sakana.aura.gallery.data.cloud.CloudProviderFactory
import ai.sakana.aura.gallery.data.cloud.CloudProviderType
import ai.sakana.aura.gallery.data.cloud.CloudRepository
import ai.sakana.aura.gallery.data.cloud.ConflictMode
import ai.sakana.aura.gallery.data.cloud.ImportOptions
import ai.sakana.aura.gallery.data.cloud.ImportReport
import ai.sakana.aura.gallery.data.cloud.LocalMediaSnapshot
import ai.sakana.aura.gallery.data.cloud.PendingConflict
import ai.sakana.aura.gallery.data.cloud.SyncOptions
import ai.sakana.aura.gallery.data.cloud.SyncReport
import javax.inject.Inject

/** Account 1 provides CloudProviderFactory in a Hilt module once provider adapters are registered. */
private fun CloudProviderFactory.require(type: CloudProviderType): CloudRepository =
    create(type) ?: throw CloudAuthException(type.displayName + " is not available yet.")

class SyncToCloudUseCase @Inject constructor(private val factory: CloudProviderFactory) {
    suspend operator fun invoke(
        type: CloudProviderType,
        locals: List<LocalMediaSnapshot>,
        options: SyncOptions = SyncOptions(),
        freeSpaceBytes: Long = Long.MAX_VALUE,
        onProgress: (CloudProgress) -> Unit = {},
    ): SyncReport = factory.require(type).sync(
        locals, options.copy(skipVideoDownloads = options.skipVideoDownloads || BackupPolicy.shouldSkipVideoDownloads(freeSpaceBytes)), onProgress,
    )
}

class ImportFromCloudUseCase @Inject constructor(private val factory: CloudProviderFactory) {
    suspend operator fun invoke(
        type: CloudProviderType,
        options: ImportOptions,
        locals: List<LocalMediaSnapshot>,
        freeSpaceBytes: Long = Long.MAX_VALUE,
        onProgress: (CloudProgress) -> Unit = {},
    ): ImportReport = factory.require(type).importFromCloud(
        options.copy(skipVideos = options.skipVideos || BackupPolicy.shouldSkipVideoDownloads(freeSpaceBytes)), locals, onProgress,
    )

    suspend fun backups(type: CloudProviderType): List<BackupInfo> = factory.require(type).getBackups()
}

class ResolveCloudConflictUseCase @Inject constructor(private val factory: CloudProviderFactory) {
    suspend operator fun invoke(type: CloudProviderType, conflict: PendingConflict, mode: ConflictMode): SyncReport =
        factory.require(type).resolveConflict(conflict, mode)
}
