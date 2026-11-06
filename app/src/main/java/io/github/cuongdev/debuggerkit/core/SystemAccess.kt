package io.github.cuongdev.debuggerkit.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.UserManager
import android.provider.Settings
import java.net.Inet4Address

/** Thin, exception safe wrapper over Settings and the permission checks. */
class SystemAccess(private val context: Context) {
    private val cr get() = context.contentResolver

    fun hasSecureSettings(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    /** True when an admin (MDM) disabled debugging. The app must never try to override this. */
    fun debuggingBlockedByPolicy(): Boolean {
        val um = context.getSystemService(UserManager::class.java) ?: return false
        return um.hasUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES)
    }

    fun globalString(key: String): String? =
        try { Settings.Global.getString(cr, key) } catch (e: SecurityException) { null }

    fun globalInt(key: String, def: Int = 0): Int = globalString(key)?.trim()?.toIntOrNull() ?: def

    fun globalFloat(key: String, def: Float = 1f): Float = globalString(key)?.trim()?.toFloatOrNull() ?: def

    fun putGlobalString(key: String, value: String?): Boolean =
        try {
            Settings.Global.putString(cr, key, value)
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }

    fun putGlobalInt(key: String, value: Int): Boolean = putGlobalString(key, value.toString())

    fun systemString(key: String): String? =
        try { Settings.System.getString(cr, key) } catch (e: SecurityException) { null }

    fun putSystemString(key: String, value: String?): Boolean =
        try {
            Settings.System.putString(cr, key, value)
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }

    /** IPv4 address on the current WiFi network, or null when not on WiFi. */
    fun wifiIpv4(): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val net = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(net) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val lp = cm.getLinkProperties(net) ?: return null
        return lp.linkAddresses
            .map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }
}
