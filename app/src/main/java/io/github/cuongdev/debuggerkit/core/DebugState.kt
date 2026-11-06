package io.github.cuongdev.debuggerkit.core

data class DebugState(
    val hasPermission: Boolean = false,
    val blocked: Boolean = false,
    val usb: Boolean = false,
    val wireless: Boolean = false,
    val devOptions: Boolean = false,
    val stayOn: Int = 0,
) {
    /** The app can change debug settings right now. */
    val ready: Boolean get() = hasPermission && !blocked
    val keepAwake: Boolean get() = stayOn != 0
}
