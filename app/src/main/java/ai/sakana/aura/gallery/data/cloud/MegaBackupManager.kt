package ai.sakana.aura.gallery.data.cloud

/**
 * MEGA provider. MEGA is end-to-end encrypted: first sign-in needs email + password; afterwards the
 * adapter resumes from a session stored in EncryptedSharedPreferences, so the password is never kept.
 * SHA-256 integrity checking after download is done by the shared engine for every provider.
 */
class MegaBackupManager(
    api: CloudApi,
    gateway: LocalMediaGateway,
    stateStore: CloudSyncStateStore,
    clock: () -> Long = { System.currentTimeMillis() },
    retry: RetryPolicy = RetryPolicy(),
) : EngineBackedCloudRepository(CloudProviderType.MEGA, api, gateway, stateStore, clock, retry) {
    override suspend fun connect(credentials: CloudCredentials?): Result<Unit> {
        if (api.isConnected()) return Result.success(Unit)
        if (credentials?.email.isNullOrBlank() || credentials?.password.isNullOrEmpty()) {
            return Result.failure(CloudAuthException("MEGA needs your email and password for the first sign-in."))
        }
        return super.connect(credentials)
    }
}
