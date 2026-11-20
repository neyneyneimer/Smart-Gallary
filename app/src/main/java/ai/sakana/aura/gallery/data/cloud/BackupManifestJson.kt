package ai.sakana.aura.gallery.data.cloud

object BackupManifestJson {
    fun toJson(manifest: BackupManifest): String = MiniJson.write(
        mapOf(
            "version" to manifest.version,
            "updatedAt" to manifest.updatedAtEpochMillis,
            "entries" to manifest.entries.map { e ->
                mapOf(
                    "contentUri" to e.contentUri, "remoteId" to e.remoteId, "displayName" to e.displayName,
                    "mimeType" to e.mimeType, "mediaType" to e.mediaType, "sizeBytes" to e.sizeBytes,
                    "localModifiedTime" to e.localModifiedTime, "remoteModifiedTime" to e.remoteModifiedTime,
                    "sha256Hash" to e.sha256Hash, "dateTaken" to e.dateTakenEpochMillis,
                    "isFavorite" to e.isFavorite, "bucketName" to e.bucketName,
                )
            },
        ),
    )

    /** Throws IllegalArgumentException on malformed JSON. Unknown fields are ignored. */
    fun fromJson(text: String): BackupManifest {
        val root = MiniJson.parse(text) as? Map<*, *> ?: throw IllegalArgumentException("Manifest is not a JSON object")
        val entries = (root["entries"] as? List<*>).orEmpty().map { raw ->
            val m = raw as? Map<*, *> ?: throw IllegalArgumentException("Manifest entry is not an object")
            fun str(k: String) = m[k] as? String
            fun long(k: String) = (m[k] as? Number)?.toLong() ?: 0L
            ManifestEntry(
                contentUri = str("contentUri") ?: throw IllegalArgumentException("Entry without contentUri"),
                remoteId = str("remoteId") ?: throw IllegalArgumentException("Entry without remoteId"),
                displayName = str("displayName") ?: "",
                mimeType = str("mimeType"),
                mediaType = str("mediaType") ?: "IMAGE",
                sizeBytes = long("sizeBytes"),
                localModifiedTime = long("localModifiedTime"),
                remoteModifiedTime = long("remoteModifiedTime"),
                sha256Hash = str("sha256Hash"),
                dateTakenEpochMillis = long("dateTaken"),
                isFavorite = m["isFavorite"] as? Boolean ?: false,
                bucketName = str("bucketName") ?: "Other",
            )
        }
        return BackupManifest(
            version = (root["version"] as? Number)?.toInt() ?: 1,
            updatedAtEpochMillis = (root["updatedAt"] as? Number)?.toLong() ?: 0L,
            entries = entries,
        )
    }
}
