package ai.sakana.aura.gallery.data.cloud

/**
 * Google Drive provider. Uses the `drive.file` scope, so the app only ever sees files it created.
 * The [api] is DriveRestApi (below) with a Google Sign-In/Identity token provider supplied by the app.
 */
class GoogleDriveBackupManager(
    api: CloudApi,
    gateway: LocalMediaGateway,
    stateStore: CloudSyncStateStore,
    clock: () -> Long = { System.currentTimeMillis() },
    retry: RetryPolicy = RetryPolicy(),
) : EngineBackedCloudRepository(CloudProviderType.GOOGLE_DRIVE, api, gateway, stateStore, clock, retry) {
    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
