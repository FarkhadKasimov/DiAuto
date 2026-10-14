package com.andrerinas.openheadunit.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.andrerinas.openheadunit.BuildConfig
import com.andrerinas.openheadunit.decoder.VideoDecoder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogExporter {

    enum class LogLevel(val filter: String, val logLevel: Int) {
        VERBOSE("*:V", Log.VERBOSE),
        DEBUG("*:D", Log.DEBUG),
        INFO("*:I", Log.INFO),
        WARNING("*:W", Log.WARN),
        ERROR("*:E", Log.ERROR),
        /** Do not perform any background capture. */
        SILENT("", Int.MAX_VALUE)
    }

    private var captureProcess: Process? = null
    private var captureThread: Thread? = null
    private var captureFile: File? = null
    private var captureVerbosity: LogLevel = LogLevel.DEBUG
    private var captureRestarts = 0
    private const val MAX_RESTARTS = 5

    /**
     * Ceiling for one capture file. Sits under [LogFilesHelper]'s 50 MB budget for the whole
     * directory, so a single runaway capture cannot consume it.
     */
    private const val MAX_CAPTURE_BYTES = 16L * 1024 * 1024

    val isCapturing: Boolean get() = captureProcess != null

    /** Current capture verbosity while capturing, or null when no capture is active. */
    val currentLevel: LogLevel?
        get() = if (isCapturing) captureVerbosity else null

    /**
     * Starts a continuous logcat process writing to a timestamped file.
     * Unlike [saveLogToPublicFile], this captures everything from the moment it is called,
     * bypassing the small shared ring buffer.
     */
    fun startCapture(context: Context, verbosity: LogLevel) {
        if (AppLog.logSource == Settings.LogSource.APPLOG_FILE) {
            AppLog.w("LogExporter: log source is APPLOG_FILE; logcat capture is disabled")
            stopCapture()
            captureFile = null
            captureVerbosity = verbosity
            return
        }

        // If SILENT requested, ensure capture is stopped and don't start a new one.
        if (verbosity == LogLevel.SILENT) {
            stopCapture()
            captureFile = null
            captureVerbosity = verbosity
            return
        }

        stopCapture()
        val settings = Settings(context)
        val logDir = LogFilesHelper.resolveLogDirectory(context, settings, allowInternalFallback = false) ?: return
        LogFilesHelper.rotateLogs(logDir)

        val file = LogFilesHelper.createTimestampedLogFile(logDir)
        captureFile = file
        captureVerbosity = verbosity
        captureRestarts = 0

        launchLogcatPipe(file, verbosity)
    }

    /**
     * Spawns a logcat process piping stdout into [file] (append mode).
     * When the process exits unexpectedly, restarts automatically up to [MAX_RESTARTS] times
     * so a system-killed logcat doesn't silently stop the capture.
     */
    private fun launchLogcatPipe(file: File, verbosity: LogLevel) {
        try {
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-v", "threadtime", verbosity.filter)
            )
            captureProcess = process
            captureThread = Thread {
                var capped = false
                try {
                    FileOutputStream(file, true).use { out ->
                        // Not copyTo(): at VERBOSE the filter is the whole system, and rotateLogs
                        // only runs when a capture starts, so an unattended capture used to grow
                        // until the disk did. Count what we write and stop at a bound instead.
                        var written = file.length()
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val read = process.inputStream.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            written += read
                            if (written >= MAX_CAPTURE_BYTES) {
                                out.write(
                                    "\n--- capture stopped: reached ${MAX_CAPTURE_BYTES / (1024 * 1024)} MB ---\n"
                                        .toByteArray()
                                )
                                capped = true
                                break
                            }
                        }
                    }
                } catch (_: IOException) { }

                if (capped) {
                    // Clear the process reference before destroying it so the restart below sees the
                    // capture as intentionally ended. Deliberately not stopCapture(): that joins
                    // captureThread, which is this thread.
                    captureProcess = null
                    process.destroy()
                    AppLog.w(
                        "LogExporter: capture stopped at ${MAX_CAPTURE_BYTES / (1024 * 1024)} MB. " +
                            "Export this log and start a new capture if you still need one."
                    )
                    return@Thread
                }

                // the read loop ended — logcat process died or was intentionally stopped
                if (captureProcess === process && captureRestarts < MAX_RESTARTS) {
                    captureRestarts++
                    AppLog.w("Log capture process exited, restarting (attempt $captureRestarts/$MAX_RESTARTS)")
                    try { Thread.sleep(2000) } catch (_: InterruptedException) { return@Thread }
                    launchLogcatPipe(file, verbosity)
                }
            }.also { it.isDaemon = true; it.start() }
        } catch (e: IOException) {
            AppLog.e("Failed to start log capture", e)
            captureFile = null
        }
    }

    /** Stops the continuous capture process. */
    fun stopCapture() {
        captureProcess?.destroy()
        captureProcess = null
        captureThread?.join(2000)
        captureThread = null
    }

    data class ExportResult(
        val localFile: File,
        val usbFiles: List<File> = emptyList()
    )

    /**
     * Generates a comprehensive hardware, system, and DiAuto configuration header
     * to prepend to exported diagnostic logs.
     */
    fun generateDiagnosticHeader(context: Context, settings: Settings): String {
        val dm = context.resources.displayMetrics
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val currentDate = sdf.format(Date())
        val isByd = Settings.isBydPlatform()

        return buildString {
            appendLine("================================================================================")
            appendLine("DiAuto Diagnostic Report")
            appendLine("Generated: $currentDate")
            appendLine("App Version: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
            appendLine("Package: ${context.packageName}")
            appendLine("--------------------------------------------------------------------------------")
            appendLine("DEVICE INFORMATION:")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Device / Board: ${Build.DEVICE} / ${Build.BOARD}")
            appendLine("Hardware: ${Build.HARDWARE}")
            appendLine("Fingerprint: ${Build.FINGERPRINT}")
            appendLine("Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Display Metrics: ${dm.widthPixels}x${dm.heightPixels}, Density: ${dm.densityDpi} dpi")
            appendLine("BYD Platform Detected: $isByd")
            appendLine("--------------------------------------------------------------------------------")
            appendLine("CURRENT DIAUTO CONFIGURATION:")
            appendLine("Resolution Setting: ${settings.resolutionId}")
            appendLine("FPS Limit: ${settings.fpsLimit}")
            appendLine("DPI: ${settings.dpiPixelDensity}")
            appendLine("Video Codec: ${settings.videoCodec} (Hardware HEVC Reliable: ${VideoDecoder.isHevcReliable()})")
            appendLine("Renderer ViewMode: ${settings.viewMode}")
            appendLine("BYD HUD Navigation: ${settings.bydNavigationEnabled}")
            appendLine("Music via Bluetooth: ${settings.musicViaBluetooth}")
            appendLine("Sync MediaSession Metadata: ${settings.syncMediaSessionWithAaMetadata}")
            appendLine("DSP Equalizer Attached: ${settings.attachHwDspEqualizer}")
            appendLine("Separate Audio Streams: ${settings.separateAudioStreams}")
            appendLine("Night Mode Setting: ${settings.nightMode}")
            appendLine("Log Source: ${settings.logSource}, Exporter Level: ${settings.exporterLogLevel}")
            appendLine("================================================================================")
            appendLine()
        }
    }

    /**
     * Exports full diagnostic logs prepended with device info, saving to internal/Downloads
     * and automatically copying to any mounted USB flash drives.
     */
    fun exportLogsWithSummary(context: Context, verbosity: LogLevel): ExportResult? {
        if (verbosity == LogLevel.SILENT) {
            AppLog.w("LogExporter: export requested while SILENT; skipping export")
            return null
        }

        val settings = Settings(context)
        val logDir = LogFilesHelper.resolveLogDirectory(context, settings, allowInternalFallback = true) ?: return null
        LogFilesHelper.ensureDirectory(logDir)
        LogFilesHelper.rotateLogs(logDir)

        val logFile = LogFilesHelper.createTimestampedLogFile(logDir)
        val header = generateDiagnosticHeader(context, settings)

        try {
            FileOutputStream(logFile).use { out ->
                out.write(header.toByteArray(Charsets.UTF_8))

                val source = captureFile ?: if (settings.logSource == Settings.LogSource.APPLOG_FILE) {
                    (AppLog.currentLogFile ?: AppLog.lastLogFile)?.takeIf { it.exists() && it.length() > 0 }
                } else null

                if (source != null && source.exists() && source.length() > 0) {
                    source.inputStream().use { input ->
                        input.copyTo(out)
                    }
                } else {
                    val process = Runtime.getRuntime().exec(
                        arrayOf("logcat", "-d", "-v", "threadtime", verbosity.filter)
                    )
                    process.inputStream.copyTo(out)
                    process.waitFor()
                }
            }
        } catch (e: Exception) {
            AppLog.e("Failed to save log file", e)
            return null
        }

        // Automatic copy to any detected USB flash drives / OTG volumes
        val usbFiles = mutableListOf<File>()
        val usbDirs = LogFilesHelper.findUsbStorageLogDirs(context)
        for (usbDir in usbDirs) {
            try {
                LogFilesHelper.ensureDirectory(usbDir)
                val targetFile = File(usbDir, logFile.name)
                logFile.copyTo(targetFile, overwrite = true)
                usbFiles.add(targetFile)
                AppLog.i("Successfully copied log to USB: ${targetFile.absolutePath}")
            } catch (e: Exception) {
                AppLog.w("Failed to copy log to USB dir ${usbDir.absolutePath}: ${e.message}")
            }
        }

        return ExportResult(localFile = logFile, usbFiles = usbFiles)
    }

    /**
     * Writes logs to a timestamped file and returns it.
     */
    fun saveLogToPublicFile(context: Context, verbosity: LogLevel): File? {
        return exportLogsWithSummary(context, verbosity)?.localFile
    }

    fun shareLogFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Share Log File")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}