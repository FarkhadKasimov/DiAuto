package com.andrerinas.openheadunit.adb

import android.content.Context
import android.util.Log

/** Status and tools for the local ADB link on BYD head units (localhost:5555). */
object BydAdbAccess {
    enum class State { READY, NOT_APPROVED, ADB_OFF, PAIRING_ONLY }

    class Status(
        val state: State,
        val batteryPercent: Double? = null,
        val rangeKm: Int? = null,
        val speedKmh: Double? = null,
    )

    fun checkState(context: Context, mayAsk: Boolean): State =
        try {
            LocalAdb(AdbKeys.load(context)).use { state(it.connect(mayAsk)) }
        } catch (e: Exception) {
            Log.w("BydAdbAccess", "Failed to check ADB state", e)
            State.ADB_OFF
        }

    fun executeShell(context: Context, command: String): String? =
        try {
            LocalAdb(AdbKeys.load(context)).use { adb ->
                if (adb.connect(mayAsk = false) == LocalAdb.Access.READY) {
                    adb.shell(command)
                } else null
            }
        } catch (e: Exception) {
            Log.w("BydAdbAccess", "Shell command failed: $command", e)
            null
        }

    /** Automatically grant permissions (Overlay, Write Settings, etc.) via local ADB if available */
    fun grantPermissionsIfPossible(context: Context): Boolean {
        val pkg = context.packageName
        val commands = listOf(
            "appops set $pkg SYSTEM_ALERT_WINDOW allow",
            "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS",
            "appops set $pkg GET_USAGE_STATS allow"
        )
        return try {
            LocalAdb(AdbKeys.load(context)).use { adb ->
                if (adb.connect(mayAsk = false) == LocalAdb.Access.READY) {
                    commands.forEach { cmd -> adb.shell(cmd) }
                    true
                } else false
            }
        } catch (e: Exception) {
            false
        }
    }

    internal fun state(access: LocalAdb.Access): State = when (access) {
        LocalAdb.Access.READY -> State.READY
        LocalAdb.Access.NOT_APPROVED -> State.NOT_APPROVED
        LocalAdb.Access.UNREACHABLE -> State.ADB_OFF
        LocalAdb.Access.UNSUPPORTED -> State.PAIRING_ONLY
    }
}
