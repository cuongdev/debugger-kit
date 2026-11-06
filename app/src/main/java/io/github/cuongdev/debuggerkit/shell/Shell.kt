package io.github.cuongdev.debuggerkit.shell

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.github.cuongdev.debuggerkit.BuildConfig
import io.github.cuongdev.debuggerkit.core.Prefs
import io.github.cuongdev.debuggerkit.core.ShellBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Optional shell access for settings Android does not let normal apps write
 * (layout bounds, GPU overlays, show taps, dark mode, demo mode, force stop).
 * Backed by Shizuku or root, whichever the user picked in Setup.
 */
class Shell(private val context: Context, private val prefs: Prefs) {

    data class Out(val code: Int, val text: String) {
        val ok: Boolean get() = code == 0
    }

    val shizukuRunning: Boolean
        get() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    val shizukuGranted: Boolean
        get() = shizukuRunning && runCatching {
            !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    fun available(): Boolean = when (prefs.value.shellBackend) {
        ShellBackend.SHIZUKU -> shizukuGranted
        ShellBackend.ROOT -> true
        ShellBackend.NONE -> false
    }

    suspend fun exec(command: String): Out? = withContext(Dispatchers.IO) {
        when (prefs.value.shellBackend) {
            ShellBackend.SHIZUKU -> execShizuku(command)
            ShellBackend.ROOT -> execRoot(command)
            ShellBackend.NONE -> null
        }
    }

    /** Grants WRITE_SECURE_SETTINGS to this app through the selected shell. */
    suspend fun grantSelf(): Out? =
        exec("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")

    /** Asks su for root once. Shows the root manager prompt on rooted phones. */
    suspend fun checkRoot(): Boolean = withContext(Dispatchers.IO) {
        execRoot("id")?.text?.contains("uid=0") == true
    }

    fun requestShizukuPermission(onResult: (Boolean) -> Unit) {
        if (!shizukuRunning) {
            onResult(false)
            return
        }
        if (shizukuGranted) {
            onResult(true)
            return
        }
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != REQUEST_CODE) return
                Shizuku.removeRequestPermissionResultListener(this)
                onResult(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.requestPermission(REQUEST_CODE)
    }

    // Shizuku user service

    @Volatile
    private var service: IShellService? = null
    private val bindLock = Mutex()

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellUserService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private suspend fun shizukuService(): IShellService? {
        service?.takeIf { it.asBinder().pingBinder() }?.let { return it }
        if (!shizukuGranted) return null
        return bindLock.withLock {
            service?.takeIf { it.asBinder().pingBinder() } ?: withTimeoutOrNull(BIND_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val conn = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                            val s = binder?.takeIf { it.pingBinder() }?.let { IShellService.Stub.asInterface(it) }
                            service = s
                            if (cont.isActive) cont.resume(s)
                        }

                        override fun onServiceDisconnected(name: ComponentName?) {
                            service = null
                        }
                    }
                    Handler(Looper.getMainLooper()).post {
                        runCatching { Shizuku.bindUserService(userServiceArgs, conn) }
                            .onFailure { if (cont.isActive) cont.resume(null) }
                    }
                }
            }
        }
    }

    private suspend fun execShizuku(command: String): Out? {
        val s = shizukuService() ?: return null
        return runCatching { parse(s.exec(command)) }.getOrNull()
    }

    private fun execRoot(command: String): Out? = runCatching {
        val p = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().readText()
        val done = p.waitFor(ROOT_TIMEOUT_S, TimeUnit.SECONDS)
        Out(if (done) p.exitValue() else -1, text)
    }.getOrNull()

    private fun parse(raw: String): Out {
        val nl = raw.indexOf('\n')
        return if (nl < 0) {
            Out(raw.trim().toIntOrNull() ?: -1, "")
        } else {
            Out(raw.substring(0, nl).trim().toIntOrNull() ?: -1, raw.substring(nl + 1))
        }
    }

    companion object {
        private const val REQUEST_CODE = 4210
        private const val BIND_TIMEOUT_MS = 5_000L
        private const val ROOT_TIMEOUT_S = 15L
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}
