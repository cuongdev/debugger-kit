package io.github.cuongdev.debuggerkit.bubble

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** Drop target shown at the bottom while dragging the bubble. Dropping on it hides the bubble. */
class DismissView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density

    var hot = false
        set(v) { if (field != v) { field = v; invalidate() } }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = (if (hot) 30f else 24f) * density
        fill.color = if (hot) 0xF27EE2A8.toInt() else 0xCC1E2327.toInt()
        canvas.drawCircle(cx, cy, r, fill)
        ring.strokeWidth = 2f * density
        ring.color = if (hot) 0xFF7EE2A8.toInt() else 0x99E9EDF0.toInt()
        canvas.drawCircle(cx, cy, r, ring)
        ink.strokeWidth = 2.2f * density
        ink.color = if (hot) 0xFF0B1F14.toInt() else 0xFFE9EDF0.toInt()
        val s = 7f * density
        canvas.drawLine(cx - s, cy - s, cx + s, cy + s, ink)
        canvas.drawLine(cx + s, cy - s, cx - s, cy + s, ink)
    }
}
