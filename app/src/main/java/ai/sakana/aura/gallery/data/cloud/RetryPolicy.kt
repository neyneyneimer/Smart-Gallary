package ai.sakana.aura.gallery.data.cloud

import kotlinx.coroutines.delay

/** Retries rate-limited calls 3 times with delays 1s, 2s, 4s; any other error propagates immediately. */
class RetryPolicy(
    private val delaysMillis: List<Long> = listOf(1_000L, 2_000L, 4_000L),
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun <T> run(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (error: CloudRateLimitException) {
                if (attempt >= delaysMillis.size) throw error
                sleeper(delaysMillis[attempt])
                attempt++
            }
        }
    }
}
