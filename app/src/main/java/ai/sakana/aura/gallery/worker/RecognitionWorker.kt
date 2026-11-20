package ai.sakana.aura.gallery.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ai.sakana.aura.gallery.domain.repository.MediaRecognitionRepository
import ai.sakana.aura.gallery.domain.repository.MediaRepository
import ai.sakana.aura.gallery.domain.repository.PersonClusterRepository
import ai.sakana.aura.gallery.domain.repository.RecognitionProgressState
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class RecognitionWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val mediaRepository: MediaRepository,
    private val recognitionRepository: MediaRecognitionRepository,
    private val personClusterRepository: PersonClusterRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val savedProgress = personClusterRepository.getRecognitionProgress()
        if (savedProgress?.isPaused == true) return Result.success()

        val allItems = mediaRepository.observeMedia().first()
        val startIndex = (savedProgress?.currentItemIndex ?: 0).coerceIn(0, allItems.size)
        if (startIndex >= allItems.size) return Result.success()

        val remaining = allItems.subList(startIndex, allItems.size)
        setForeground(createForegroundInfo(startIndex, allItems.size))

        var processedSoFar = savedProgress?.processedCount ?: 0
        recognitionRepository.analyze(remaining) { completed, _ ->
            if (isStopped) return@analyze
            val currentIdx = startIndex + completed
            processedSoFar++
            runBlocking {
                personClusterRepository.saveRecognitionProgress(
                    RecognitionProgressState(
                        currentItemIndex = currentIdx,
                        totalItems = allItems.size,
                        processedCount = processedSoFar,
                        lastRunEpochMillis = System.currentTimeMillis(),
                        isPaused = false,
                    )
                )
            }
            setForegroundAsync(createForegroundInfo(currentIdx, allItems.size))
        }
        return Result.success()
    }

    private fun createForegroundInfo(current: Int, total: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "On-device AI Recognition",
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
        val pauseIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Analyzing photos & videos on-device")
            .setContentText("\$current of \$total items processed")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setOngoing(true)
            .setProgress(total.coerceAtLeast(1), current, false)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val WORK_NAME = "aura_on_device_recognition"
        private const val CHANNEL_ID = "aura_recognition_progress"
        private const val NOTIFICATION_ID = 2002
        const val INCREMENTAL_TRIGGER_MIN_NEW_ITEMS = 10
        const val EXPEDITED_MAX_BATCH_SIZE = 50

        fun enqueueIfNeeded(context: Context, newItemsCount: Int) {
            if (newItemsCount <= INCREMENTAL_TRIGGER_MIN_NEW_ITEMS) return
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()

            val builder = OneTimeWorkRequestBuilder<RecognitionWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)

            if (newItemsCount < EXPEDITED_MAX_BATCH_SIZE) {
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                builder.build(),
            )
        }
    }
}
