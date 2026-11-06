package io.github.cuongdev.debuggerkit.bubble

import android.animation.ValueAnimator
import io.github.cuongdev.debuggerkit.R
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Point
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.core.animation.doOnEnd
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.cuongdev.debuggerkit.core.AppPrefs
import io.github.cuongdev.debuggerkit.core.DebugController
import io.github.cuongdev.debuggerkit.core.DebugState
import io.github.cuongdev.debuggerkit.core.KeepMode
import io.github.cuongdev.debuggerkit.core.Notifications
import io.github.cuongdev.debuggerkit.devtools.Preset
import io.github.cuongdev.debuggerkit.graph
import io.github.cuongdev.debuggerkit.ui.DebugKitTheme
import io.github.cuongdev.debuggerkit.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

/**
 * Foreground service that owns the floating bubble, the drag to dismiss target and the dev panel overlay.
 * Gestures: tap toggles USB debugging, double tap applies the UI test preset, long press opens the dev panel.
 * Gravity uses LEFT and RIGHT on purpose: the bubble sticks to physical screen edges.
 */
@SuppressLint("RtlHardcoded")
class BubbleService : LifecycleService(), SavedStateRegistryOwner {

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    private val g get() = applicationContext.graph
    private lateinit var wm: WindowManager
    private var bubble: BubbleView? = null
    private var dismissView: DismissView? = null
    private var panel: ComposeView? = null

    private val lp = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    )
    private var anchoredRight = true
    private var dragging = false

    private val handler = Handler(Looper.getMainLooper())
    private val idleRunnable = Runnable { goIdle() }
    private var screenOffReceiver: BroadcastReceiver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var notificationKey = ""
    private var lastAutoOffAttempt = 0L

    override fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        super.onCreate()
        instance = this
        wm = getSystemService(WindowManager::class.java)
        goForeground()
        // The first emission adds the bubble when it is enabled.
        lifecycleScope.launch {
            combine(g.controller.state, g.prefs.flow) { s, p -> s to p }.collect { (s, p) -> render(s, p) }
        }
        lifecycleScope.launch {
            while (true) {
                tick()
                delay(1_000)
            }
        }
        // "Debug off per app": watch which app is in front.
        lifecycleScope.launch(Dispatchers.Default) {
            while (true) {
                runCatching { g.appRules.poll() }
                delay(RULE_POLL_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_SHOW_PANEL -> showPanel()
            ACTION_STOP -> {
                g.prefs.setBubbleEnabled(false)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        listOfNotNull<View>(panel, dismissView, bubble).forEach { runCatching { wm.removeView(it) } }
        panel = null
        dismissView = null
        bubble = null
        screenOffReceiver?.let { runCatching { unregisterReceiver(it) } }
        networkCallback?.let { cb ->
            runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(cb) }
        }
        super.onDestroy()
    }

    // Foreground notification

    private fun holdLabel(p: AppPrefs): String? = p.holdPkg.takeIf { it.isNotEmpty() }?.let { g.appRules.label(it) }

    private fun goForeground() {
        val p = g.prefs.value
        val n = Notifications.session(this, g.controller.state.value, p, holdLabel(p))
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifications.ID_SESSION, n, type)
    }

    private fun updateNotification(s: DebugState, p: AppPrefs) {
        val key = "${s.usb}|${p.autoOffAt}|${p.holdPkg}|${p.bubbleEnabled}|${p.appRules.size}"
        if (key == notificationKey) return
        notificationKey = key
        getSystemService(NotificationManager::class.java)
            ?.notify(Notifications.ID_SESSION, Notifications.session(this, s, p, holdLabel(p)))
    }

    // Model to view

    private fun render(s: DebugState, p: AppPrefs) {
        if (!shouldRun(this, p)) {
            stopSelf()
            return
        }
        val wantBubble = p.bubbleEnabled && Settings.canDrawOverlays(this)
        if (wantBubble && bubble == null) addBubble() else if (!wantBubble && bubble != null) removeBubble()
        updateGuards(p)
        updateNotification(s, p)
        val b = bubble ?: return
        b.on = s.usb
        b.wireless = s.wireless
        b.needsPermission = !s.ready
        b.diameter = dp(SIZES[p.bubbleSize.coerceIn(0, 2)])

        val keepOn = p.bubbleKeepScreenOn || (p.keepScreenOn && p.keepMode == KeepMode.ALWAYS && s.usb)
        val flags = if (keepOn) {
            lp.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        } else {
            lp.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        }
        if (flags != lp.flags) {
            lp.flags = flags
            safeUpdate()
        }
        tick()
    }

    private fun tick() {
        val s = g.controller.state.value
        val p = g.prefs.flow.value
        // Enforce auto off on time while the service runs; the alarm is only a fallback.
        if (s.usb && p.autoOffAt > 0 && System.currentTimeMillis() >= p.autoOffAt && lastAutoOffAttempt != p.autoOffAt) {
            lastAutoOffAttempt = p.autoOffAt
            g.controller.setUsb(false, "auto off")
            return
        }
        val b = bubble ?: return
        b.progress = when {
            !s.usb -> -1f
            p.autoOffAt > 0 && p.autoOffTotal > 0 ->
                ((p.autoOffAt - System.currentTimeMillis()).toFloat() / p.autoOffTotal).coerceIn(0f, 1f)
            else -> 1f
        }
    }

    // Bubble window

    private fun addBubble() {
        val p = g.prefs.value
        val b = BubbleView(this)
        b.diameter = dp(SIZES[p.bubbleSize.coerceIn(0, 2)])
        // Click and long click listeners make the bubble usable with TalkBack and switch access.
        // While the panel is open the bubble only closes it, so a stray tap never toggles debugging.
        b.setOnClickListener { if (panel != null) hidePanel() else onTap() }
        b.setOnLongClickListener {
            if (panel != null) hidePanel() else showPanel()
            true
        }
        b.setOnTouchListener(BubbleTouch())
        fullScreen(lp)
        anchoredRight = p.bubbleRight
        lp.gravity = Gravity.TOP or if (anchoredRight) Gravity.RIGHT else Gravity.LEFT
        lp.x = 0
        lp.y = if (p.bubbleY >= 0) p.bubbleY else screen().y / 3
        wm.addView(b, lp)
        bubble = b
        scheduleIdle()
    }

    private fun removeBubble() {
        hidePanel()
        hideDismiss()
        handler.removeCallbacks(idleRunnable)
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
    }

    private fun safeUpdate() {
        val b = bubble ?: return
        runCatching { wm.updateViewLayout(b, lp) }
    }

    private fun screen(): Point = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val r = wm.currentWindowMetrics.bounds
        Point(r.width(), r.height())
    } else {
        val dm = resources.displayMetrics
        Point(dm.widthPixels, dm.heightPixels)
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    /** Lay overlay windows out against the whole display so every window shares one coordinate space. */
    private fun fullScreen(params: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) params.setFitInsetsTypes(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    /** Status bar (top) and navigation bar (bottom) heights in px. */
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun systemBarInsets(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val i = wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout(),
            )
            return i.top to i.bottom
        }
        fun res(name: String): Int {
            val id = resources.getIdentifier(name, "dimen", "android")
            return if (id > 0) resources.getDimensionPixelSize(id) else 0
        }
        return res("status_bar_height") to res("navigation_bar_height")
    }

    /** Switch from edge anchoring to absolute coordinates so dragging is simple. */
    private fun toAbsolute() {
        val b = bubble ?: return
        if ((lp.gravity and Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT) {
            lp.x = screen().x - b.width - lp.x
            lp.gravity = Gravity.TOP or Gravity.LEFT
            safeUpdate()
        }
    }

    private fun snapToEdge() {
        val b = bubble ?: return
        val size = screen()
        val right = lp.x + b.width / 2 > size.x / 2
        val targetX = if (right) size.x - b.width else 0
        val (safeTop, safeBottom) = systemBarInsets()
        val y = lp.y.coerceIn(safeTop, max(safeTop, size.y - safeBottom - b.height))
        ValueAnimator.ofInt(lp.x, targetX).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                lp.x = it.animatedValue as Int
                lp.y = y
                safeUpdate()
            }
            doOnEnd { anchor(right, y) }
            start()
        }
    }

    private fun anchor(right: Boolean, y: Int) {
        anchoredRight = right
        lp.gravity = Gravity.TOP or if (right) Gravity.RIGHT else Gravity.LEFT
        lp.x = 0
        lp.y = y
        safeUpdate()
        g.prefs.setBubblePosition(right, y)
    }

    // Idle: dock into an edge tab, or fade

    private fun scheduleIdle() {
        handler.removeCallbacks(idleRunnable)
        handler.postDelayed(idleRunnable, IDLE_MS)
    }

    private fun goIdle() {
        val b = bubble ?: return
        if (panel != null || dragging) return
        val p = g.prefs.value
        if (p.dockWhenIdle) dock() else b.alpha = p.idleOpacity / 100f
    }

    private fun dock() {
        val b = bubble ?: return
        if (b.docked) return
        b.dockRight = anchoredRight
        // The tab is narrower than the bubble, so always pin it flush to its screen edge.
        lp.gravity = Gravity.TOP or if (anchoredRight) Gravity.RIGHT else Gravity.LEFT
        lp.x = 0
        lp.y += (b.undockedSize() - b.dockedHeight()) / 2
        b.docked = true
        b.alpha = 1f
        safeUpdate()
    }

    private fun undock() {
        val b = bubble ?: return
        if (!b.docked) return
        lp.y -= (b.undockedSize() - b.dockedHeight()) / 2
        b.docked = false
        safeUpdate()
    }

    // Gestures

    private inner class BubbleTouch : View.OnTouchListener {
        private val slop = ViewConfiguration.get(this@BubbleService).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var swallow = false

        private val detector = GestureDetector(this@BubbleService, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                bubble?.performClick()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                onDoubleTapGesture()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (dragging) return
                // performLongClick also plays the long press haptic.
                bubble?.performLongClick()
            }
        })

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val b = bubble ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacks(idleRunnable)
                    b.alpha = 1f
                    if (b.docked) {
                        // First touch on the tab only brings the bubble back.
                        undock()
                        swallow = true
                        return true
                    }
                    swallow = false
                    dragging = false
                    downX = e.rawX
                    downY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    if (swallow) return true
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && hypot(dx, dy) > slop) {
                        dragging = true
                        // Switch to absolute coordinates only for a real drag. Taps and long presses
                        // keep the bubble anchored to its screen edge.
                        toAbsolute()
                        startX = lp.x
                        startY = lp.y
                        if (panel != null) hidePanel()
                        val cancel = MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL }
                        detector.onTouchEvent(cancel)
                        cancel.recycle()
                        showDismiss()
                    }
                    if (dragging) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        safeUpdate()
                        dismissView?.hot = overDismiss()
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (swallow) {
                        swallow = false
                        scheduleIdle()
                        return true
                    }
                    if (dragging) {
                        dragging = false
                        val drop = overDismiss()
                        hideDismiss()
                        if (drop) {
                            dismissBubble()
                        } else {
                            snapToEdge()
                            scheduleIdle()
                        }
                        return true
                    }
                    scheduleIdle()
                }
            }
            detector.onTouchEvent(e)
            return true
        }
    }

    private fun onTap() {
        val r = g.controller.setUsb(!g.controller.read().usb, "bubble")
        feedback(r)
    }

    private fun onDoubleTapGesture() {
        if (panel != null) {
            hidePanel()
            return
        }
        lifecycleScope.launch {
            val o = g.devTools.applyPreset(Preset.UI_TEST)
            o.message?.let { toast(it) }
        }
    }

    private fun feedback(r: DebugController.Result) {
        when (r) {
            DebugController.Result.OK -> bubble?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            DebugController.Result.NO_PERMISSION -> {
                toast(getString(R.string.toast_grant_first))
                openApp("setup")
            }
            DebugController.Result.BLOCKED -> toast(getString(R.string.msg_blocked))
            DebugController.Result.FAILED -> toast(getString(R.string.msg_failed))
        }
    }

    // Drag to dismiss

    private fun showDismiss() {
        if (dismissView != null) return
        val v = DismissView(this)
        val size = dp(72f)
        val dlp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = screen().x / 2 - size / 2
            y = screen().y - dp(DISMISS_BOTTOM) - size
        }
        fullScreen(dlp)
        runCatching { wm.addView(v, dlp) }.onSuccess { dismissView = v }
    }

    private fun hideDismiss() {
        dismissView?.let { runCatching { wm.removeView(it) } }
        dismissView = null
    }

    private fun overDismiss(): Boolean {
        val b = bubble ?: return false
        val size = screen()
        val cx = lp.x + b.width / 2f
        val cy = lp.y + b.height / 2f
        val tx = size.x / 2f
        val ty = size.y - dp(DISMISS_BOTTOM) - dp(36f).toFloat()
        return hypot(cx - tx, cy - ty) < dp(80f)
    }

    private fun dismissBubble() {
        toast(getString(R.string.toast_bubble_hidden))
        g.prefs.setBubbleEnabled(false)
    }

    // Dev panel overlay

    fun showPanel() {
        if (panel != null) return
        handler.removeCallbacks(idleRunnable)
        undock()
        bubble?.alpha = 1f
        // Open the panel below the bubble (or above it when the bubble sits low) so they never overlap.
        val (safeTop, safeBottom) = systemBarInsets()
        val anchor = PanelAnchor(
            topPx = lp.y,
            bottomPx = lp.y + (bubble?.undockedSize() ?: 0),
            screenHeightPx = screen().y,
            safeTopPx = safeTop,
            safeBottomPx = safeBottom,
        )
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@BubbleService)
            setViewTreeSavedStateRegistryOwner(this@BubbleService)
            setContent {
                DebugKitTheme {
                    DevPanelOverlay(anchor = anchor, onClose = { hidePanel() }, onOpenApp = { openApp(it) })
                }
            }
        }
        val plp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        fullScreen(plp)
        runCatching { wm.addView(view, plp) }.onSuccess {
            panel = view
            // Keep the bubble above the panel.
            bubble?.let { b -> runCatching { wm.removeView(b); wm.addView(b, lp) } }
        }
    }

    fun hidePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
        scheduleIdle()
    }

    private fun openApp(route: String) {
        hidePanel()
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_ROUTE, route),
        )
    }

    // Safety guards that run while the service is alive

    private fun updateGuards(p: AppPrefs) {
        if (p.offOnScreenLock && screenOffReceiver == null) {
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    if (g.controller.read().usb) g.controller.setUsb(false, "screen lock")
                }
            }
            ContextCompat.registerReceiver(
                this, r, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            screenOffReceiver = r
        } else if (!p.offOnScreenLock) {
            screenOffReceiver?.let { runCatching { unregisterReceiver(it) } }
            screenOffReceiver = null
        }

        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        if (p.wirelessGuard && networkCallback == null) {
            val cb = object : ConnectivityManager.NetworkCallback() {
                private var last: Network? = null
                override fun onAvailable(network: Network) {
                    val previous = last
                    last = network
                    if (previous != null && previous != network) {
                        handler.post {
                            if (g.controller.read().wireless) g.controller.setWireless(false, "network change")
                        }
                    }
                }
            }
            runCatching { cm.registerDefaultNetworkCallback(cb) }.onSuccess { networkCallback = cb }
        } else if (!p.wirelessGuard) {
            networkCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
            networkCallback = null
        }
    }

    private fun toast(msg: String) = Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()

    companion object {
        @Volatile
        var instance: BubbleService? = null
            private set

        const val ACTION_SHOW_PANEL = "io.github.cuongdev.debuggerkit.action.SHOW_PANEL"
        const val ACTION_STOP = "io.github.cuongdev.debuggerkit.action.STOP_BUBBLE"

        private val SIZES = floatArrayOf(44f, 52f, 60f)
        private const val IDLE_MS = 3_000L
        private const val DISMISS_BOTTOM = 56f
        private const val RULE_POLL_MS = 700L

        /** The service runs for the bubble, for "Debug off per app", or both. */
        fun shouldRun(context: Context, p: AppPrefs = context.graph.prefs.value): Boolean =
            (p.bubbleEnabled && Settings.canDrawOverlays(context)) || context.graph.appRules.isActive(p)

        /** Starts or stops the service so it matches the settings. Call from a visible screen. */
        fun sync(context: Context) {
            val run = shouldRun(context)
            if (run && instance == null) start(context) else if (!run && instance != null) stop(context)
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, BubbleService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleService::class.java))
        }
    }
}
