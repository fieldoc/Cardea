package com.hrcoach.service.audio

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Append-only, per-run debug log for the audio/voice pipeline.
 *
 * Captures every coaching event fired during a run, with HR / target / zone / slope context, plus
 * the TTS and earcon decision (spoke, dropped, watchdog cleared, etc). Writes to
 * `filesDir/tts_debug/run_YYYYMMDD_HHmmss.log` so the user can pull files off-device for analysis:
 *
 *     adb shell "run-as com.hrcoach ls files/tts_debug"
 *     adb shell "run-as com.hrcoach cat files/tts_debug/run_20260421_143502.log"
 *
 * Retention: the TWO most recent runs are kept. Older files are deleted on [startRun]. This is
 * purely diagnostic — it does not touch any existing workout data in Room or in cloud backup.
 *
 * Thread-safe: all file mutations are @Synchronized on the single logger instance.
 */
class TtsDebugLogger(context: Context) {

    private val dir: File = File(context.filesDir, DIR_NAME)
    @Volatile private var currentFile: File? = null
    @Volatile private var runStartMs: Long = 0L

    @Synchronized
    fun startRun(isSimulation: Boolean, workoutMode: String?) {
        try {
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w(TAG, "Failed to create debug dir: ${dir.absolutePath}")
                return
            }

            // Retention: keep the single most recent file, then create a new one — that leaves
            // TWO files on disk (the previous run + the one we're about to start).
            val existing = dir.listFiles { f ->
                f.isFile && f.name.startsWith(FILE_PREFIX) && f.name.endsWith(FILE_SUFFIX)
            }?.toList()?.sortedByDescending { it.name } ?: emptyList()
            existing.drop(KEEP_PREVIOUS).forEach {
                runCatching { it.delete() }.onFailure { e -> Log.w(TAG, "Failed to delete old log ${it}", e) }
            }

            val now = Instant.now()
            val stamp = FILE_STAMP_FORMATTER.format(now)
            val simTag = if (isSimulation) "_sim" else ""
            val file = File(dir, "$FILE_PREFIX$stamp$simTag$FILE_SUFFIX")
            currentFile = file
            runStartMs = System.currentTimeMillis()

            rawAppend("=== RUN START ${TS_FORMATTER.format(now)} simulation=$isSimulation mode=${workoutMode ?: "?"} ===\n")
        } catch (t: Throwable) {
            Log.w(TAG, "startRun failed", t)
            currentFile = null
            runStartMs = 0L
        }
    }

    @Synchronized
    fun endRun(detail: String) {
        if (currentFile == null) return
        logLine("=== RUN END $detail ===")
        currentFile = null
        runStartMs = 0L
    }

    /** Writes a single log line prefixed with wall-clock timestamp + elapsed seconds. No-op before [startRun]. */
    @Synchronized
    fun logLine(line: String) {
        val f = currentFile ?: return
        if (f.length() >= MAX_FILE_BYTES) return  // safety cap — one run can't grow unbounded
        val now = Instant.now()
        val elapsed = if (runStartMs > 0L) (System.currentTimeMillis() - runStartMs) / 1000f else 0f
        val prefix = "[${TS_FORMATTER.format(now)}] +${"%.1f".format(elapsed)}s "
        rawAppend(prefix + line + "\n")
    }

    private fun rawAppend(text: String) {
        try {
            currentFile?.appendText(text)
        } catch (t: Throwable) {
            Log.w(TAG, "log append failed", t)
        }
    }

    companion object {
        private const val TAG = "TtsDebugLogger"
        private const val DIR_NAME = "tts_debug"
        private const val FILE_PREFIX = "run_"
        private const val FILE_SUFFIX = ".log"
        // Keep N PREVIOUS files on startRun; new file makes total = KEEP_PREVIOUS + 1 = 2.
        private const val KEEP_PREVIOUS = 1
        // Per-file safety cap. A 60-min run generates ~50-150 events at ~200 bytes each → ~30 KB.
        // 5 MB is a generous safety net for degenerate spam (BLE flapping, etc.) without filling
        // user storage.
        private const val MAX_FILE_BYTES: Long = 5L * 1024 * 1024

        private val TS_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
        private val FILE_STAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault())
    }
}
