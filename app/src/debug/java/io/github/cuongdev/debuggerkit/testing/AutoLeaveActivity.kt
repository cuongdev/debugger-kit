package io.github.cuongdev.debuggerkit.testing

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.TextView

/**
 * Debug builds only, never shipped. Add "Debugger Kit Dev" to the release app's "Debug off per app" list,
 * then start this screen from ADB. It stays in front for a few seconds and then goes to the home screen
 * by itself, so the whole off, ADB drops, back on, ADB reconnects cycle runs without anyone touching the phone.
 *
 *   adb shell am start -n io.github.cuongdev.debuggerkit.debug/io.github.cuongdev.debuggerkit.testing.AutoLeaveActivity --ei seconds 8
 */
class AutoLeaveActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val seconds = intent.getIntExtra(EXTRA_SECONDS, 8).coerceIn(1, 120)
        val label = TextView(this).apply {
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF0E1113.toInt())
        }
        setContentView(label)
        var left = seconds
        val tick = object : Runnable {
            override fun run() {
                if (left <= 0) {
                    startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                    return
                }
                label.text = "Debug off per app test\nLeaving in $left s"
                left--
                handler.postDelayed(this, 1_000L)
            }
        }
        tick.run()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SECONDS = "seconds"
    }
}
