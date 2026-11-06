package io.github.cuongdev.debuggerkit.bubble

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.View
import androidx.core.content.res.ResourcesCompat
import io.github.cuongdev.debuggerkit.R
import kotlin.math.max

/**
 * Bubble style C ("Timer ring"): dark disc, ">_" glyph, outer arc = time left before auto off.
 * When [docked] it collapses into a slim tab on the screen edge.
 */
class BubbleView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    /** Room around the shape for its shadow. */
    val shadowPad = dp(8f).toInt()

    var diameter: Int = dp(52f).toInt()
        set(v) { if (field != v) { field = v; requestLayout(); invalidate() } }

    var on = false
        set(v) { if (field != v) { field = v; invalidate() } }

    var wireless = false
        set(v) { if (field != v) { field = v; invalidate() } }

    var needsPermission = false
        set(v) { if (field != v) { field = v; invalidate() } }

    /** Remaining fraction 0..1. Negative hides the ring. */
    var progress = -1f
        set(v) { if (field != v) { field = v; invalidate() } }

    var docked = false
        set(v) { if (field != v) { field = v; requestLayout(); invalidate() } }

    var dockRight = true
        set(v) { if (field != v) { field = v; invalidate() } }

    private val tabWidth = dp(22f).toInt()
    private val tabHeight = dp(60f).toInt()

    fun undockedSize() = diameter + shadowPad * 2
    fun dockedWidth() = tabWidth + shadowPad
    fun dockedHeight() = tabHeight + shadowPad * 2

    private val mono: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.jetbrains_mono_bold) }
        .getOrNull() ?: Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DISC
        setShadowLayer(dp(7f), 0f, dp(3f), 0x66000000)
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x24FFFFFF }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x1FFFFFFF }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = MINT
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = mono
        textAlign = Paint.Align.CENTER
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = DISC }
    private val badgeInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = BADGE_INK
    }
    private val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = AMBER_INK
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = GRIP
    }
    private val rect = RectF()
    private val path = Path()

    init {
        // Shadow layers on shapes need software rendering before Android 9.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) setLayerType(LAYER_TYPE_SOFTWARE, null)
        contentDescription = context.getString(R.string.cd_bubble)
    }

    // Taps arrive through the service's touch listener, which calls this for accessibility.
    override fun performClick(): Boolean = super.performClick()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (docked) setMeasuredDimension(dockedWidth(), dockedHeight())
        else setMeasuredDimension(undockedSize(), undockedSize())
    }

    override fun onDraw(canvas: Canvas) {
        if (docked) drawTab(canvas) else drawBubble(canvas)
    }

    private fun drawBubble(c: Canvas) {
        val d = diameter.toFloat()
        val unit = d / 56f
        val cx = width / 2f
        val cy = height / 2f
        val r = d / 2f

        c.drawCircle(cx, cy, r, discPaint)
        rimPaint.strokeWidth = max(1f, unit)
        c.drawCircle(cx, cy, r - rimPaint.strokeWidth / 2f, rimPaint)

        if (progress >= 0f) {
            val stroke = 3f * unit
            val ringR = 24.5f * unit
            trackPaint.strokeWidth = stroke
            c.drawCircle(cx, cy, ringR, trackPaint)
            arcPaint.strokeWidth = stroke
            rect.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR)
            c.drawArc(rect, -90f, 360f * progress.coerceIn(0f, 1f), false, arcPaint)
        }

        glyphPaint.textSize = 16f * unit
        glyphPaint.color = if (on) MINT else MUTED
        val fm = glyphPaint.fontMetrics
        c.drawText(">_", cx, cy - (fm.ascent + fm.descent) / 2f, glyphPaint)

        val br = 11f * unit
        val bx = cx + r - br + 3f * unit
        val by = cy - r + br - 3f * unit
        when {
            needsPermission -> {
                drawBadgeBase(c, bx, by, br, unit, AMBER)
                badgeText.textSize = 13f * unit
                val bfm = badgeText.fontMetrics
                c.drawText("!", bx, by - (bfm.ascent + bfm.descent) / 2f, badgeText)
            }
            wireless -> {
                drawBadgeBase(c, bx, by, br, unit, BLUE)
                badgeInk.strokeWidth = 1.8f * unit
                val ay = by + br * 0.42f
                rect.set(bx - br * 0.62f, ay - br * 0.62f, bx + br * 0.62f, ay + br * 0.62f)
                c.drawArc(rect, 225f, 90f, false, badgeInk)
                rect.set(bx - br * 0.32f, ay - br * 0.32f, bx + br * 0.32f, ay + br * 0.32f)
                c.drawArc(rect, 225f, 90f, false, badgeInk)
            }
        }
    }

    private fun drawBadgeBase(c: Canvas, x: Float, y: Float, r: Float, unit: Float, color: Int) {
        badgePaint.color = color
        c.drawCircle(x, y, r, badgePaint)
        badgeRim.strokeWidth = 2f * unit
        c.drawCircle(x, y, r, badgeRim)
    }

    private fun drawTab(c: Canvas) {
        val left = if (dockRight) shadowPad.toFloat() else 0f
        val right = left + tabWidth
        val top = shadowPad.toFloat()
        val bottom = top + tabHeight
        val rad = dp(12f)
        val radii = if (dockRight) {
            floatArrayOf(rad, rad, 0f, 0f, 0f, 0f, rad, rad)
        } else {
            floatArrayOf(0f, 0f, rad, rad, rad, rad, 0f, 0f)
        }
        path.reset()
        rect.set(left, top, right, bottom)
        path.addRoundRect(rect, radii, Path.Direction.CW)
        c.drawPath(path, discPaint)

        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f - dp(6f)
        if (on) {
            dotPaint.style = Paint.Style.FILL
            dotPaint.color = MINT
        } else {
            dotPaint.style = Paint.Style.STROKE
            dotPaint.strokeWidth = dp(1.5f)
            dotPaint.color = MUTED
        }
        c.drawCircle(cx, cy, dp(4f), dotPaint)
        if (needsPermission) {
            dotPaint.style = Paint.Style.FILL
            dotPaint.color = AMBER
            c.drawCircle(cx, cy, dp(4f), dotPaint)
        }
        gripPaint.strokeWidth = dp(2f)
        c.drawLine(cx - dp(4f), cy + dp(11f), cx + dp(4f), cy + dp(11f), gripPaint)
        c.drawLine(cx - dp(4f), cy + dp(16f), cx + dp(4f), cy + dp(16f), gripPaint)
    }

    companion object {
        private const val DISC = 0xFF15191C.toInt()
        private const val MINT = 0xFF7EE2A8.toInt()
        private const val MUTED = 0xFF9BA6AF.toInt()
        private const val BLUE = 0xFF8DB6FF.toInt()
        private const val AMBER = 0xFFF2B55A.toInt()
        private const val BADGE_INK = 0xFF0E1520.toInt()
        private const val AMBER_INK = 0xFF1E1608.toInt()
        private const val GRIP = 0xFF4A545C.toInt()
    }
}
