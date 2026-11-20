package ai.sakana.aura.gallery.data.cloud

import android.content.Context

data class ProviderDescriptor(val type: CloudProviderType, val displayName: String, val isAvailable: Boolean)

/**
 * Hands out only providers that really exist. Anything without a registered implementation is reported
 * as unavailable so the UI can honestly label it "Coming soon" instead of faking a connection.
 */
class CloudProviderFactory(private val providers: Map<CloudProviderType, CloudRepository>) {
    fun create(type: CloudProviderType): CloudRepository? = if (type == CloudProviderType.NONE) null else providers[type]

    fun isAvailable(type: CloudProviderType): Boolean = create(type) != null

    fun descriptors(): List<ProviderDescriptor> = listOf(CloudProviderType.GOOGLE_DRIVE, CloudProviderType.MEGA, CloudProviderType.TERABOX)
        .map { ProviderDescriptor(it, it.displayName, isAvailable(it)) }
}

data class CloudSettings(
    val primaryProvider: CloudProviderType = CloudProviderType.NONE,
    val conflictMode: ConflictMode = CloudConflictResolver.DEFAULT_MODE,
    val wifiOnly: Boolean = true,
    val autoBackup: Boolean = false,
)

class CloudSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("aura_cloud_settings", Context.MODE_PRIVATE)

    fun load() = CloudSettings(
        primaryProvider = runCatching { CloudProviderType.valueOf(prefs.getString("provider", "NONE").orEmpty()) }.getOrDefault(CloudProviderType.NONE),
        conflictMode = runCatching { ConflictMode.valueOf(prefs.getString("conflict", "").orEmpty()) }.getOrDefault(CloudConflictResolver.DEFAULT_MODE),
        wifiOnly = prefs.getBoolean("wifi_only", true),
        autoBackup = prefs.getBoolean("auto_backup", false),
    )

    fun save(settings: CloudSettings) {
        prefs.edit().putString("provider", settings.primaryProvider.name).putString("conflict", settings.conflictMode.name)
            .putBoolean("wifi_only", settings.wifiOnly).putBoolean("auto_backup", settings.autoBackup).apply()
    }
}
