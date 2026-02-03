package com.zj.dashcam

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

class LoopRecordingManager(
    private val context: Context,
    private val segmentDurationMs: Long,
    private val maxSegments: Int
) {
    private val logger = Logger(LOG_PREFIX)
    private val handlerThread = HandlerThread("LoopRecordingThread").apply { start() }
    private val handler = Handler(handlerThread.looper)
    private val segments = ArrayDeque<File>()
    private val timestampFormatter = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    @Volatile
    private var isRecording = false
    private var currentSegmentStartElapsed = 0L
    private var segmentIndex = 0

    private val rotationRunnable = Runnable {
        if (!isRecording) {
            logger.d("LoopRecording rotation skipped: recording stopped.")
            return@Runnable
        }
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - currentSegmentStartElapsed
        logger.d(
            "LoopRecording rotating segment index=$segmentIndex elapsed=${elapsed}ms " +
                "duration=${segmentDurationMs}ms segments=${segments.size}/$maxSegments"
        )
        startNewSegment(reason = "rotation")
    }

    fun start() {
        if (isRecording) {
            logger.d("LoopRecording start ignored: already recording.")
            return
        }
        isRecording = true
        logger.d(
            "LoopRecording start requested: segmentDuration=${segmentDurationMs}ms " +
                "maxSegments=$maxSegments storageDir=${getSegmentDir().absolutePath}"
        )
        handler.post { startNewSegment(reason = "start") }
    }

    fun stop() {
        if (!isRecording) {
            logger.d("LoopRecording stop ignored: not recording.")
            return
        }
        isRecording = false
        handler.removeCallbacks(rotationRunnable)
        handler.post {
            logger.d(
                "LoopRecording stopped: segments retained=${segments.size} " +
                    "lastSegmentStartElapsed=$currentSegmentStartElapsed"
            )
        }
    }

    fun release() {
        logger.d("LoopRecording release: stopping thread.")
        stop()
        handlerThread.quitSafely()
    }

    private fun startNewSegment(reason: String) {
        if (!isRecording) {
            logger.d("LoopRecording startNewSegment aborted: recording stopped.")
            return
        }
        currentSegmentStartElapsed = SystemClock.elapsedRealtime()
        val segmentFile = createSegmentFile()
        segments.addLast(segmentFile)
        segmentIndex += 1
        logger.d(
            "LoopRecording new segment created reason=$reason index=$segmentIndex " +
                "file=${segmentFile.absolutePath} segments=${segments.size}/$maxSegments"
        )
        trimOldSegmentsIfNeeded()
        handler.removeCallbacks(rotationRunnable)
        handler.postDelayed(rotationRunnable, segmentDurationMs)
    }

    private fun trimOldSegmentsIfNeeded() {
        while (segments.size > maxSegments) {
            val oldest = segments.removeFirst()
            val deleted = oldest.delete()
            logger.d(
                "LoopRecording trim: deleting oldest segment file=${oldest.absolutePath} " +
                    "deleted=$deleted remaining=${segments.size}/$maxSegments"
            )
        }
    }

    private fun createSegmentFile(): File {
        val dir = getSegmentDir()
        if (!dir.exists()) {
            val created = dir.mkdirs()
            logger.d("LoopRecording storage dir create result=$created path=${dir.absolutePath}")
        }
        val timestamp = timestampFormatter.format(Date())
        val fileName = "segment_${timestamp}_${segmentIndex}.mp4"
        val file = File(dir, fileName)
        if (!file.exists()) {
            val created = file.createNewFile()
            logger.d(
                "LoopRecording segment file created=$created path=${file.absolutePath} " +
                    "available=${dir.usableSpace}B"
            )
        }
        return file
    }

    private fun getSegmentDir(): File {
        val baseDir = context.getExternalFilesDir("segments") ?: context.filesDir
        return File(baseDir, "loop_recordings")
    }

    companion object {
        private const val LOG_PREFIX = "LoopRecordingManager"
    }
}
