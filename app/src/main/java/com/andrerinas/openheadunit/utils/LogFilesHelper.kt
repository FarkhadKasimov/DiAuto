package com.andrerinas.openheadunit.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogFilesHelper {

    const val LOG_FILE_PREFIX = "DiAuto_Log_"
    private const val LOG_FILE_SUFFIX = ".txt"
    const val DEFAULT_MAX_LOG_FILES = 15
    const val DEFAULT_MAX_TOTAL_SIZE = 50L * 1024 * 1024 // 50 MB

    fun resolveLogDirectory(
        context: Context,
        settings: Settings? = null,
        allowInternalFallback: Boolean = true
    ): File? {
        val location = settings?.logLocation ?: try {
            Settings(context).logLocation
        } catch (_: Exception) {
            Settings.LogLocation.DEFAULT
        }

        // On automotive headunits, prefer public Downloads/DiAuto_Logs so users can access files via car file manager
        val downloadsDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION") Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        } else {
            @Suppress("DEPRECATION")
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }

        if (downloadsDir != null) {
            val diAutoDir = File(downloadsDir, "DiAuto_Logs")
            if (diAutoDir.exists() || diAutoDir.mkdirs()) {
                return diAutoDir
            }
            if (downloadsDir.exists() || downloadsDir.mkdirs()) {
                return downloadsDir
            }
        }

        return context.getExternalFilesDir(null) ?: if (allowInternalFallback) context.filesDir else null
    }

    /**
     * Scans for mounted USB storage volumes or OTG flash drives.
     */
    fun findUsbStorageLogDirs(context: Context): List<File> {
        val results = mutableListOf<File>()
        try {
            val dirs = context.getExternalFilesDirs(null)
            if (dirs != null && dirs.size > 1) {
                for (i in 1 until dirs.size) {
                    val d = dirs[i] ?: continue
                    var current: File? = d
                    while (current != null && current.name != "Android" && current.parentFile != null) {
                        current = current.parentFile
                    }
                    val usbRoot = current?.parentFile ?: d
                    val logDir = File(usbRoot, "DiAuto_Logs")
                    if (logDir.exists() || logDir.mkdirs() || logDir.canWrite()) {
                        results.add(logDir)
                    } else if (d.canWrite()) {
                        val fallback = File(d, "DiAuto_Logs")
                        fallback.mkdirs()
                        results.add(fallback)
                    }
                }
            }

            // Common mount paths on Android car units
            val commonMounts = listOf(
                "/storage/usbotg",
                "/mnt/usb_storage",
                "/storage/usbdisk",
                "/storage/udisk0",
                "/storage/udisk1",
                "/mnt/media_rw"
            )
            for (m in commonMounts) {
                val f = File(m)
                if (f.exists() && f.isDirectory) {
                    val sub = f.listFiles()
                    if (sub != null && sub.isNotEmpty()) {
                        for (drive in sub) {
                            if (drive.isDirectory && drive.canWrite()) {
                                val logDir = File(drive, "DiAuto_Logs")
                                if (logDir.exists() || logDir.mkdirs()) {
                                    results.add(logDir)
                                }
                            }
                        }
                    } else if (f.canWrite()) {
                        val logDir = File(f, "DiAuto_Logs")
                        if (logDir.exists() || logDir.mkdirs()) {
                            results.add(logDir)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.w("Error scanning USB storage: ${e.message}")
        }
        return results.filter { it.canWrite() || it.exists() }.distinct()
    }

    fun rotateLogs(
        logDir: File,
        prefix: String = LOG_FILE_PREFIX,
        maxFiles: Int = DEFAULT_MAX_LOG_FILES,
        maxTotalSizeBytes: Long = DEFAULT_MAX_TOTAL_SIZE
    ) {
        val files = logDir.listFiles { _, name -> name.startsWith(prefix) || name.startsWith("HUR_Log_") }
            ?.sortedBy { it.lastModified() }
            ?.toMutableList() ?: return

        while (files.size >= maxFiles) {
            files.removeAt(0).delete()
        }

        var totalSize = files.sumOf { it.length() }
        while (totalSize > maxTotalSizeBytes && files.isNotEmpty()) {
            val oldest = files.removeAt(0)
            totalSize -= oldest.length()
            oldest.delete()
        }
    }

    fun createTimestampedLogFile(logDir: File, prefix: String = LOG_FILE_PREFIX): File {
        ensureDirectory(logDir)

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        var candidate = File(logDir, "$prefix$timeStamp$LOG_FILE_SUFFIX")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(logDir, "${prefix}${timeStamp}_$suffix$LOG_FILE_SUFFIX")
            suffix++
        }
        return candidate
    }

    fun ensureDirectory(logDir: File) {
        if (!logDir.exists()) logDir.mkdirs()
    }
}

