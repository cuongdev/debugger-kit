package io.github.cuongdev.debuggerkit.shell

import android.content.Context
import kotlin.system.exitProcess

/**
 * Runs inside the process Shizuku starts for us, with shell (adb) privileges.
 * Shizuku creates it by reflection, so keep both constructors.
 */
class ShellUserService() : IShellService.Stub() {

    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : this()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String = try {
        val p = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        "$code\n$out"
    } catch (t: Throwable) {
        "-1\n${t.message}"
    }
}
