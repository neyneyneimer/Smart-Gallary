package ai.sakana.aura.gallery.data.cloud

data class TeraboxTokens(val accessToken: String, val refreshToken: String, val expiresAtEpochMillis: Long)

interface TeraboxTokenStore {
    suspend fun load(): TeraboxTokens?
    suspend fun save(tokens: TeraboxTokens)
}

fun interface TeraboxTokenRefresher {
    suspend fun refresh(refreshToken: String): TeraboxTokens
}

/** Hands out a valid access token, refreshing it when less than 5 minutes of life remain. */
class TeraboxTokenManager(
    private val store: TeraboxTokenStore,
    private val refresher: TeraboxTokenRefresher,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun validAccessToken(): String {
        val tokens = store.load() ?: throw CloudAuthException("Sign in to TeraBox first.")
        if (tokens.expiresAtEpochMillis - clock() > REFRESH_MARGIN_MILLIS) return tokens.accessToken
        val fresh = try {
            refresher.refresh(tokens.refreshToken)
        } catch (error: CloudAuthException) {
            throw error
        } catch (error: Exception) {
            throw CloudAuthException("TeraBox session expired. Sign in again.")
        }
        store.save(fresh)
        return fresh.accessToken
    }

    companion object { const val REFRESH_MARGIN_MILLIS = 5 * 60 * 1000L }
}

/**
 * TeraBox provider. Its adapter reports supportsHashLookup = true, so the shared engine skips the
 * upload when TeraBox already has the same SHA-256 (rapid upload). The adapter itself needs TeraBox
 * developer credentials and the official API documentation; see the dependency list.
 */
class TeraboxBackupManager(
    api: CloudApi,
    gateway: LocalMediaGateway,
    stateStore: CloudSyncStateStore,
    clock: () -> Long = { System.currentTimeMillis() },
    retry: RetryPolicy = RetryPolicy(),
) : EngineBackedCloudRepository(CloudProviderType.TERABOX, api, gateway, stateStore, clock, retry)
