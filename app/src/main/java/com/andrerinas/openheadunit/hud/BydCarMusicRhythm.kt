package com.andrerinas.openheadunit.hud

import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.andrerinas.openheadunit.adb.AdbKeys
import com.andrerinas.openheadunit.adb.LocalAdb
import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * The car's own ambient-light music rhythm, running while Android Auto music plays.
 * Uses BYD IAudioService to activate the car's native ambient lighting music rhythm.
 */
object BydCarMusicRhythm {
    private const val TAG = "DiAuto-CarRhythm"
    private const val RETRY_MILLIS = 2_000L
    private const val MAX_RETRIES = 3
    private val executor = Executors.newSingleThreadScheduledExecutor { Thread(it, "diauto-car-rhythm").apply { isDaemon = true } }

    internal var direct: BydCarMusicRhythmCall = BydCarMusicRhythmDirect
    internal var helper: BydCarMusicRhythmCall = BydCarMusicRhythmHelper
    @Volatile private var wanted = false
    private var startedBy: BydCarMusicRhythmCall? = null
    private var failures = 0

    fun setPlaying(context: Context, value: Boolean) {
        if (wanted == value) return
        wanted = value
        val app = context.applicationContext
        executor.execute {
            failures = 0
            apply(app)
        }
    }

    fun checkSupport(context: Context): CompletableFuture<Boolean> {
        val result = CompletableFuture<Boolean>()
        val app = context.applicationContext
        executor.execute {
            val supported = runCatching { direct.supported(app) || helper.supported(app) }.getOrDefault(false)
            Log.i(TAG, "check supported=$supported")
            result.complete(supported)
        }
        return result
    }

    private fun apply(context: Context) {
        val running = startedBy
        if (wanted == (running != null)) return
        if (running != null) {
            running.stop(context)
            startedBy = null
        } else {
            startedBy = listOf(direct, helper).firstOrNull { it.start(context) }
            if (startedBy == null) retry(context) else failures = 0
        }
        val state = when (startedBy) {
            null -> "off"
            direct -> "on (direct)"
            else -> "on (adb helper)"
        }
        Log.i(TAG, "car music rhythm $state")
    }

    private fun retry(context: Context) {
        if (!wanted || failures >= MAX_RETRIES) return
        failures++
        Log.i(TAG, "start again in ${RETRY_MILLIS * failures} ms (attempt $failures)")
        executor.schedule({ apply(context) }, RETRY_MILLIS * failures, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
}

internal interface BydCarMusicRhythmCall {
    fun supported(context: Context): Boolean
    fun start(context: Context): Boolean
    fun stop(context: Context)
    fun sendBands(context: Context, bands: IntArray): Boolean = false
}

internal object BydCarMusicRhythmDirect : BydCarMusicRhythmCall {
    private const val TAG = "DiAuto-CarRhythm"
    private val token = Binder()

    override fun supported(context: Context): Boolean = runCatching { BydCarMusicRhythmTool.audio() }
        .onFailure { Log.i(TAG, "no direct call: ${describe(it)}") }.isSuccess

    override fun start(context: Context): Boolean = call(start = true)

    override fun stop(context: Context) { call(start = false) }

    private fun call(start: Boolean): Boolean = runCatching {
        val audio = BydCarMusicRhythmTool.audio()
        if (start) audio.startRhythm(token) else audio.stopRhythm(token)
    }.onFailure { Log.w(TAG, "direct ${if (start) "start" else "stop"} failed: ${describe(it)}") }.isSuccess

    private fun describe(error: Throwable): String = (error.cause ?: error).let { "${it.javaClass.simpleName}: ${it.message}" }
}

internal object BydCarMusicRhythmHelper : BydCarMusicRhythmCall {
    private const val TAG = "DiAuto-CarRhythm"
    private var adb: LocalAdb? = null
    private var shell: LocalAdb.InteractiveShell? = null

    override fun supported(context: Context): Boolean = runCatching {
        LocalAdb(AdbKeys.load(context)).use { client ->
            client.connect(mayAsk = false) == LocalAdb.Access.READY &&
                client.shell(command(context, "--probe"))?.trim() == "supported"
        }
    }.getOrDefault(false)

    override fun start(context: Context): Boolean {
        val open = shell ?: open(context) ?: return false
        val reply = open.exchangeBounded("start")?.trim()
        if (reply == "started") return true
        Log.w(TAG, "helper replied $reply")
        close()
        return false
    }

    override fun stop(context: Context) {
        val reply = shell?.let { it.exchangeBounded("stop")?.trim() } ?: return
        if (reply != "stopped") {
            Log.w(TAG, "helper replied $reply")
            close()
        }
    }

    private fun open(context: Context): LocalAdb.InteractiveShell? {
        close()
        val client = LocalAdb(AdbKeys.load(context)).also { adb = it }
        if (client.connect(mayAsk = false) != LocalAdb.Access.READY) {
            Log.w(TAG, "no adb access")
            close()
            return null
        }
        val opened = client.openShell(command(context, "--stdin")) ?: run { close(); return null }
        if (opened.exchangeBounded("ping", 10_000)?.trim() != "ready") {
            Log.w(TAG, "helper not ready")
            opened.close()
            close()
            return null
        }
        shell = opened
        return opened
    }

    private fun close() {
        runCatching { shell?.close() }
        shell = null
        runCatching { adb?.close() }
        adb = null
    }

    private fun command(context: Context, mode: String): String {
        val apk = "'" + context.applicationInfo.sourceDir.replace("'", "'\\''") + "'"
        return "CLASSPATH=$apk app_process /system/bin ${BydCarMusicRhythmTool::class.java.name} $mode"
    }
}

object BydCarMusicRhythmTool {
    const val MEDIA_CENTRE = "com.byd.mediacenter"

    internal class Audio(val service: Any, val start: Method, val stop: Method) {
        fun startRhythm(token: IBinder) { start.invoke(service, MEDIA_CENTRE, token) }
        fun stopRhythm(token: IBinder) {
            if (stop.parameterTypes.size == 2) stop.invoke(service, MEDIA_CENTRE, token)
            else stop.invoke(service, MEDIA_CENTRE)
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            when (args.firstOrNull()) {
                "--probe" -> println(if (runCatching { audio() }.isSuccess) "supported" else "unsupported")
                "--stdin" -> serve()
            }
        } catch (error: Throwable) {
            println("error ${(error.cause ?: error).javaClass.simpleName}")
            println("done")
        } finally {
            System.out.flush()
            System.exit(0)
        }
    }

    internal fun audio(): Audio {
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "audio") as IBinder
        val service = checkNotNull(Class.forName("android.media.IAudioService\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder))
        return find(service)
    }

    internal fun find(service: Any): Audio = Audio(service,
        service.javaClass.getMethod("startAudioOutput", String::class.java, IBinder::class.java),
        runCatching { service.javaClass.getMethod("stopAudioOutput", String::class.java, IBinder::class.java) }
            .getOrElse { service.javaClass.getMethod("stopAudioOutput", String::class.java) })

    private fun serve() {
        val audio = audio()
        val token = Binder()
        var started = false
        val input = System.`in`.bufferedReader()
        while (true) {
            val request = (input.readLine() ?: break).trim()
            val reply = when {
                request == "ping" -> "ready"
                request == "start" -> {
                    if (!started) audio.startRhythm(token)
                    started = true
                    "started"
                }
                request == "stop" -> {
                    if (started) audio.stopRhythm(token)
                    started = false
                    "stopped"
                }
                else -> "error request"
            }
            println(reply)
            println("done")
            System.out.flush()
        }
        if (started) audio.stopRhythm(token)
    }
}
