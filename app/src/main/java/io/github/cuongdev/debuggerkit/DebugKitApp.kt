package io.github.cuongdev.debuggerkit

import android.app.Application
import android.content.Context
import io.github.cuongdev.debuggerkit.core.AppGraph

class DebugKitApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.autoOff.resync(graph.controller.read().usb)
    }
}

/** Shared singletons, created once per process. */
val Context.graph: AppGraph
    get() = (applicationContext as DebugKitApp).graph
