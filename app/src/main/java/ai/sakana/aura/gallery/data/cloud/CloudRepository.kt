package ai.sakana.aura.gallery.data.cloud

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface CloudRepository {
    val providerType: CloudProviderType
    fun getProviderName(): String
    suspend fun connect(credentials: CloudCredentials? = null): Result<Unit>
    suspend fun disconnect()
    suspend fun isConnected(): Boolean
    suspend fun sync(locals: List<LocalMediaSnapshot>, options: SyncOptions = SyncOptions(), onProgress: (CloudProgress) -> Unit = {}): SyncReport
    /** Named importFromCloud (not `import`) to avoid the Kotlin keyword. */
    suspend fun importFromCloud(options: ImportOptions = ImportOptions(), locals: List<LocalMediaSnapshot> = emptyList(), onProgress: (CloudProgress) -> Unit = {}): ImportReport
    suspend fun resolveConflict(conflict: PendingConflict, mode: ConflictMode): SyncReport
    suspend fun getBackups(): List<BackupInfo>
    suspend fun getStatus(): CloudStatus
}

/** Remembers what each provider last synced, so deletions can be told apart from new files. */
interface CloudSyncStateStore {
    suspend fun load(provider: CloudProviderType): CloudSyncState
    suspend fun save(provider: CloudProviderType, state: CloudSyncState)
}

class InMemoryCloudSyncStateStore : CloudSyncStateStore {
    private val states = HashMap<CloudProviderType, CloudSyncState>()
    override suspend fun load(provider: CloudProviderType) = states[provider] ?: CloudSyncState()
    override suspend fun save(provider: CloudProviderType, state: CloudSyncState) { states[provider] = state }
}

class PrefsCloudSyncStateStore(context: Context) : CloudSyncStateStore {
    private val prefs = context.applicationContext.getSharedPreferences("aura_cloud_sync_state", Context.MODE_PRIVATE)
    override suspend fun load(provider: CloudProviderType) = CloudSyncState(
        lastSyncEpochMillis = prefs.getLong(provider.name + ".last", 0L),
        syncedKeys = prefs.getStringSet(provider.name + ".keys", emptySet()).orEmpty().toSet(),
        syncedCount = prefs.getInt(provider.name + ".count", 0),
        lastError = prefs.getString(provider.name + ".error", null),
    )
    override suspend fun save(provider: CloudProviderType, state: CloudSyncState) {
        prefs.edit()
            .putLong(provider.name + ".last", state.lastSyncEpochMillis)
            .putStringSet(provider.name + ".keys", state.syncedKeys)
            .putInt(provider.name + ".count", state.syncedCount)
            .putString(provider.name + ".error", state.lastError)
            .apply()
    }
}

/** Shared behaviour of every provider manager; subclasses add provider-specific rules. */
abstract class EngineBackedCloudRepository(
    final override val providerType: CloudProviderType,
    protected val api: CloudApi,
    gateway: LocalMediaGateway,
    private val stateStore: CloudSyncStateStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
    retry: RetryPolicy = RetryPolicy(),
) : CloudRepository {
    private val engine = CloudSyncEngine(api, gateway, clock, retry)
    private val lock = Mutex()
    @Volatile private var syncing = false

    override fun getProviderName(): String = providerType.displayName
    override suspend fun connect(credentials: CloudCredentials?): Result<Unit> = api.connect(credentials)
    override suspend fun disconnect() = api.disconnect()
    override suspend fun isConnected(): Boolean = api.isConnected()

    private suspend fun requireConnected() {
        if (!api.isConnected()) throw CloudAuthException("Connect ${getProviderName()} before syncing.")
    }

    override suspend fun sync(locals: List<LocalMediaSnapshot>, options: SyncOptions, onProgress: (CloudProgress) -> Unit): SyncReport {
        requireConnected()
        return lock.withLock {
            syncing = true
            val state = stateStore.load(providerType)
            try {
                val report = engine.sync(locals, options.copy(previouslySyncedKeys = state.syncedKeys), onProgress)
                stateStore.save(providerType, state.copy(lastSyncEpochMillis = clock(), syncedKeys = report.syncedKeys, syncedCount = report.syncedKeys.size, lastError = null))
                report
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                stateStore.save(providerType, state.copy(lastError = error.message ?: error.javaClass.simpleName))
                throw error
            } finally {
                syncing = false
            }
        }
    }

    override suspend fun importFromCloud(options: ImportOptions, locals: List<LocalMediaSnapshot>, onProgress: (CloudProgress) -> Unit): ImportReport {
        requireConnected()
        return lock.withLock {
            syncing = true
            try {
                val report = engine.importFromCloud(options, locals, onProgress)
                val state = stateStore.load(providerType)
                stateStore.save(providerType, state.copy(syncedKeys = state.syncedKeys + report.syncedKeys, syncedCount = (state.syncedKeys + report.syncedKeys).size))
                report
            } finally {
                syncing = false
            }
        }
    }

    override suspend fun resolveConflict(conflict: PendingConflict, mode: ConflictMode): SyncReport {
        requireConnected()
        return lock.withLock {
            val state = stateStore.load(providerType)
            val report = engine.resolveConflict(conflict, mode)
            stateStore.save(providerType, state.copy(syncedKeys = state.syncedKeys + report.syncedKeys))
            report
        }
    }

    override suspend fun getBackups(): List<BackupInfo> { requireConnected(); return engine.listBackups() }

    override suspend fun getStatus(): CloudStatus {
        val state = stateStore.load(providerType)
        return CloudStatus(providerType, api.isConnected(), state.lastSyncEpochMillis, state.syncedCount, syncing, state.lastError)
    }
}
