package io.github.cuongdev.debuggerkit.core

import android.content.Context
import io.github.cuongdev.debuggerkit.devtools.DevTools
import io.github.cuongdev.debuggerkit.rules.AppRules
import io.github.cuongdev.debuggerkit.shell.Shell

class AppGraph(context: Context) {
    val app: Context = context.applicationContext
    val sys = SystemAccess(app)
    val prefs = Prefs(app)
    val log = ActivityLog(app)
    val snapshot = SnapshotStore(app)
    val shell = Shell(app, prefs)
    val autoOff = AutoOff(app, prefs)
    val controller = DebugController(app, sys, prefs, log, autoOff)
    val devTools = DevTools(app, sys, shell, snapshot, log, prefs)
    val appRules = AppRules(app, prefs, controller)

    init {
        Notifications.createChannels(app)
    }
}
