package ai.sakana.aura.gallery.domain.recognition

/** A user-confirmed group of recurring visual setups. Person identities remain separate. */
data class AiSet(
    val id: String,
    val name: String,
    val mediaUris: List<String>,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

object AiSetRules {
    fun normalizeName(name: String): String {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "An AI Set needs a name." }
        require(normalized.length <= MAX_NAME_LENGTH) { "AI Set names must be $MAX_NAME_LENGTH characters or fewer." }
        return normalized
    }

    fun normalizeMembership(mediaUris: List<String>): List<String> = mediaUris
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

    fun requireSavableMembership(mediaUris: List<String>): List<String> {
        val normalized = normalizeMembership(mediaUris)
        require(normalized.size >= MIN_SET_SIZE) { "An AI Set needs at least two different media items." }
        return normalized
    }

    fun mergeMemberships(target: List<String>, source: List<String>): List<String> =
        normalizeMembership(target + source)

    fun removeMember(mediaUris: List<String>, contentUri: String): List<String> =
        normalizeMembership(mediaUris).filterNot { it == contentUri }

    const val MAX_NAME_LENGTH = 60
    const val MIN_SET_SIZE = 2
}
