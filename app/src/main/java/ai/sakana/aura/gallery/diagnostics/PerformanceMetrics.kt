package ai.sakana.aura.gallery.diagnostics

import android.os.SystemClock
import android.view.Choreographer
import timber.log.Timber

/** Local-only measurements for the M1 performance targets. No performance pass is assumed. */
internal object PerformanceMetrics {
    @Volatile private var processStartedAtMs = 0L
    @Volatile private var firstContentLogged = false
    private var frameCallback: Choreographer.FrameCallback? = null
    private var previousFrameNanos = 0L
    private var frameCount = 0
    private var slowFrameCount = 0
    private var peakScrollHeapBytes = 0L

    @Synchronized
    fun markProcessStart() {
        processStartedAtMs = SystemClock.elapsedRealtime()
        firstContentLogged = false
    }

    @Synchronized
    fun recordFirstGalleryContent(itemCount: Int) {
        if (itemCount <= 0 || firstContentLogged) return
        firstContentLogged = true
        val started = processStartedAtMs
        if (started <= 0L) return
        val elapsed = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L)
        Timber.i("PERF metric=cold_start_to_first_gallery_content duration_ms=%d item_count=%d", elapsed, itemCount)
        logHeapUsage("after_first_gallery_content")
    }

    fun recordMediaStoreScan(metric: String, durationMs: Long, itemCountBefore: Int, itemCountAfter: Int) {
        Timber.i(
            "PERF metric=%s duration_ms=%d item_count_before=%d item_count_after=%d item_count_delta=%d",
            metric, durationMs, itemCountBefore, itemCountAfter, itemCountAfter - itemCountBefore,
        )
        logHeapUsage("after_$metric")
    }

    fun logHeapUsage(label: String) {
        Timber.i("PERF metric=heap_usage label=%s used_bytes=%d", label, usedHeapBytes())
    }

    fun startScrollFrameMonitor() {
        if (frameCallback != null) return
        previousFrameNanos = 0L
        frameCount = 0
        slowFrameCount = 0
        peakScrollHeapBytes = usedHeapBytes()
        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (frameCallback !== this) return
                val previous = previousFrameNanos
                if (previous > 0L) {
                    frameCount += 1
                    if (frameTimeNanos - previous > 16_000_000L) slowFrameCount += 1
                }
                previousFrameNanos = frameTimeNanos
                peakScrollHeapBytes = maxOf(peakScrollHeapBytes, usedHeapBytes())
                choreographer.postFrameCallback(this)
            }
        }
        frameCallback = callback
        choreographer.postFrameCallback(callback)
    }

    fun stopScrollFrameMonitor() {
        val callback = frameCallback ?: return
        Choreographer.getInstance().removeFrameCallback(callback)
        frameCallback = null
        val withinBudget = if (frameCount == 0) 100.0 else (frameCount - slowFrameCount) * 100.0 / frameCount
        Timber.i(
            "PERF metric=scroll_jank frame_count=%d over_16ms=%d within_16ms_percent=%.1f",
            frameCount, slowFrameCount, withinBudget,
        )
        Timber.i("PERF metric=scroll_peak_heap peak_bytes=%d", peakScrollHeapBytes)
    }

    private fun usedHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
    }
}
