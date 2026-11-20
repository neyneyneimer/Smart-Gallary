package ai.sakana.aura.gallery.data.cloud

import java.time.Duration
import java.time.ZonedDateTime

/** Pure scheduling rules for BackupWorker (kept free of WorkManager so they can be tested now). */
object BackupPolicy {
    const val MIN_FREE_BYTES_FOR_VIDEOS: Long = 500L * 1024 * 1024
    val RETRY_BACKOFF_MINUTES: List<Long> = listOf(15L, 30L, 60L)
    const val DAILY_RUN_HOUR: Int = 2

    enum class Network { UNMETERED_ONLY, ANY_CONNECTED }

    fun network(wifiOnly: Boolean) = if (wifiOnly) Network.UNMETERED_ONLY else Network.ANY_CONNECTED

    /**
     * Delay before the next retry after [consecutiveFailures] failed runs, or null to stop and show an
     * error. Three retries (15, 30, 60 minutes); the fourth consecutive failure stops auto-retry.
     */
    fun retryDelayMinutes(consecutiveFailures: Int): Long? =
        if (consecutiveFailures in 1..RETRY_BACKOFF_MINUTES.size) RETRY_BACKOFF_MINUTES[consecutiveFailures - 1] else null

    /** Below 500 MB free, skip downloading videos during sync/import. */
    fun shouldSkipVideoDownloads(freeBytes: Long): Boolean = freeBytes < MIN_FREE_BYTES_FOR_VIDEOS

    /** Milliseconds from [now] to the next 2:00 AM in [now]'s zone (a run exactly at 2:00 waits a full day). */
    fun delayUntilNextDailyRunMillis(now: ZonedDateTime, hour: Int = DAILY_RUN_HOUR): Long {
        var next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }
}
