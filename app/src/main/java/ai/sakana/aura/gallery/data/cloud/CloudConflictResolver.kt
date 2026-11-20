package ai.sakana.aura.gallery.data.cloud

/**
 * Pure decision table: (conflict type, mode) -> action.
 * Never deletes anything permanently: local removal means the app's reversible Trash.
 */
object CloudConflictResolver {
    val DEFAULT_MODE: ConflictMode = ConflictMode.KEEP_DEVICE

    fun plan(type: ConflictType, mode: ConflictMode): ConflictAction {
        if (mode == ConflictMode.ASK) return ConflictAction.DEFER
        return when (type) {
            ConflictType.SAME_NAME_DIFFERENT_HASH -> when (mode) {
                ConflictMode.KEEP_DEVICE -> ConflictAction.UPLOAD_LOCAL_OVERWRITE
                ConflictMode.KEEP_CLOUD -> ConflictAction.DOWNLOAD_AND_TRASH_LOCAL
                ConflictMode.KEEP_BOTH -> ConflictAction.RENAME_CLOUD_AND_UPLOAD
                ConflictMode.ASK -> ConflictAction.DEFER
            }
            ConflictType.SAME_HASH_DIFFERENT_NAME -> when (mode) {
                ConflictMode.KEEP_DEVICE -> ConflictAction.RENAME_REMOTE_TO_LOCAL_NAME
                ConflictMode.KEEP_CLOUD -> ConflictAction.RENAME_LOCAL_TO_REMOTE_NAME
                ConflictMode.KEEP_BOTH -> ConflictAction.LEAVE_AS_IS
                ConflictMode.ASK -> ConflictAction.DEFER
            }
            ConflictType.DELETED_LOCALLY_EXISTS_IN_CLOUD -> when (mode) {
                ConflictMode.KEEP_DEVICE -> ConflictAction.LEAVE_AS_IS
                ConflictMode.KEEP_CLOUD, ConflictMode.KEEP_BOTH -> ConflictAction.DOWNLOAD_RESTORE
                ConflictMode.ASK -> ConflictAction.DEFER
            }
            ConflictType.DELETED_IN_CLOUD_EXISTS_LOCALLY -> when (mode) {
                ConflictMode.KEEP_DEVICE, ConflictMode.KEEP_BOTH -> ConflictAction.REUPLOAD
                ConflictMode.KEEP_CLOUD -> ConflictAction.TRASH_LOCAL
                ConflictMode.ASK -> ConflictAction.DEFER
            }
        }
    }

    /** "photo.jpg" -> "photo_cloud.jpg"; "photo" -> "photo_cloud". */
    fun cloudCopyName(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) name + "_cloud" else name.substring(0, dot) + "_cloud" + name.substring(dot)
    }
}
