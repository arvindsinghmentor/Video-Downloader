package com.socialsaver

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.service.quicksettings.TileService
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import kotlin.math.hypot

class HelperService : AccessibilityService() {

    companion object {
        @Volatile var instance: HelperService? = null
        val TARGETS = setOf("com.instagram.android", "com.facebook.katana", "com.facebook.lite", "com.instagram.lite")
    }

    private lateinit var wm: WindowManager
    private lateinit var container: LinearLayout
    private lateinit var actions: LinearLayout
    private lateinit var target: FrameLayout
    private lateinit var lp: WindowManager.LayoutParams
    private lateinit var tp: WindowManager.LayoutParams
    private var attached = false
    private var expanded = false
    private var lastTarget = false

    private val screenW get() = resources.displayMetrics.widthPixels
    private val screenH get() = resources.displayMetrics.heightPixels

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        buildViews()
        Bus.listener = { refresh() }
        refresh()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Bus.listener = null
        if (attached) {
            runCatching { wm.removeView(container) }
            runCatching { wm.removeView(target) }
            attached = false
        }
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val p = e.packageName?.toString() ?: return
        if (ignored(p)) return
        lastTarget = p in TARGETS
        refresh()
    }

    override fun onInterrupt() {}

    private fun ignored(p: String) =
        p == packageName || p == "android" || p.startsWith("com.android.systemui") ||
            p.contains("inputmethod") || p.contains("keyboard") ||
            p.startsWith("com.android.intentresolver") || p.contains("permissioncontroller")

    private fun refresh() {
        val show = Prefs.isOn(this) && lastTarget && !Bus.capturing && !Bus.dialogOpen
        if (show && !attached) {
            target.visibility = View.GONE
            wm.addView(target, tp)
            wm.addView(container, lp)
            attached = true
        } else if (!show && attached) {
            expanded = false
            actions.visibility = View.GONE
            runCatching { wm.removeView(container) }
            runCatching { wm.removeView(target) }
            attached = false
        }
    }

    // ---------- UI ----------

    private fun glass(radius: Int) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x66FFFFFF, 0x33000000)
    ).apply { cornerRadius = radius.toFloat(); setStroke(dp(1), 0x99FFFFFF.toInt()) }

    private fun icon(res: Int, desc: String, onClick: (() -> Unit)? = null, pad: Int = 12) = ImageView(this).apply {
        setImageResource(res)
        contentDescription = desc
        setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        if (onClick != null) setOnClickListener { onClick() }
    }

    private fun overlayParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )

    private fun buildViews() {
        // red close target
        target = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x66FF3B30); setStroke(dp(1), 0xAAFFFFFF.toInt())
            }
            addView(icon(R.drawable.ic_close, "Close", null, 18).apply { layoutParams = FrameLayout.LayoutParams(dp(64), dp(64)) })
        }
        tp = overlayParams(dp(64), dp(64)).apply {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(60)
        }

        // glass button + 3 actions
        container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glass(dp(30))
            setPadding(dp(4), dp(4), dp(4), dp(4))
            alpha = 0.88f
        }
        val bubble = icon(R.drawable.ic_ring, "Social Saver", null, 10)
        actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            addView(icon(R.drawable.ic_download, "Save from link", { launch(LinkActivity::class.java, null) }))
            addView(icon(R.drawable.ic_image, "Capture photo", { launch(CaptureActivity::class.java, "photo") }))
            addView(icon(R.drawable.ic_video, "Record video", { launch(CaptureActivity::class.java, "video") }))
        }
        container.addView(bubble)
        container.addView(actions)

        lp = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16); y = screenH / 3
        }

        var startX = 0; var startY = 0; var downX = 0f; var downY = 0f; var moved = false
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y; downX = e.rawX; downY = e.rawY; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!moved && hypot(dx, dy) > dp(8)) { moved = true; target.visibility = View.VISIBLE }
                    if (moved) {
                        lp.x = (startX + dx).toInt(); lp.y = (startY + dy).toInt()
                        wm.updateViewLayout(container, lp)
                        target.scaleX = if (overTarget()) 1.3f else 1f
                        target.scaleY = target.scaleX
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    target.visibility = View.GONE
                    when {
                        !moved -> toggle()
                        overTarget() -> {
                            Prefs.setOn(this, false)
                            TileService.requestListeningState(this, ComponentName(this, ToggleTileService::class.java))
                        }
                        else -> clamp()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun overTarget(): Boolean {
        val cx = lp.x + container.width / 2f
        val cy = lp.y + container.height / 2f
        return hypot(cx - screenW / 2f, cy - (screenH - dp(60) - dp(32))) < dp(70)
    }

    private fun clamp() {
        lp.x = lp.x.coerceIn(0, (screenW - container.width).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (screenH - container.height).coerceAtLeast(0))
        wm.updateViewLayout(container, lp)
    }

    private fun toggle() {
        expanded = !expanded
        actions.visibility = if (expanded) View.VISIBLE else View.GONE
        container.post { if (attached) clamp() }
    }

    private fun launch(cls: Class<*>, mode: String?) {
        expanded = false
        actions.visibility = View.GONE
        startActivity(Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply {
            if (mode != null) putExtra("mode", mode)
        })
    }
}
