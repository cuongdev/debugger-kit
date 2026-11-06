package io.github.cuongdev.debuggerkit.rules

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import io.github.cuongdev.debuggerkit.bubble.BubbleService
import io.github.cuongdev.debuggerkit.graph

/** Invisible trampoline behind "Open without debugging" shortcuts. Only acts on apps in the user's list. */
class SafeLaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        if (pkg != null) {
            val msg = graph.appRules.launchSafely(this, pkg)
            BubbleService.sync(this)
            msg?.let { Toast.makeText(applicationContext, it, Toast.LENGTH_LONG).show() }
        }
        finish()
    }

    companion object {
        const val ACTION = "io.github.cuongdev.debuggerkit.action.SAFE_LAUNCH"
        const val EXTRA_PACKAGE = "package"
    }
}
