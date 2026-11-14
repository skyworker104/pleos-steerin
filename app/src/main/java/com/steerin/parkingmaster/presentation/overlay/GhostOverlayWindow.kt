package com.steerin.parkingmaster.presentation.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.steerin.parkingmaster.presentation.ui.WheelEmblem
import kotlin.math.abs
import kotlin.math.min

/**
 * EXPERIMENTAL. A small Ghost Ring drawn in a TYPE_APPLICATION_OVERLAY window so it can sit on top
 * of the system rear-view app while the car is in R.
 *
 * Status (measured on Pleos_Connect_v2.1): the window type is drawn above DrivingViewActivity,
 * but SYSTEM_ALERT_WINDOW cannot be granted by a driver — the image has no "display over other
 * apps" settings screen and `appops set … allow` is not honoured for third-party packages. It is
 * therefore kept behind a debug-only manifest permission and [isAllowed]; production builds use
 * [CoachNotifier] (heads-up) + voice instead. Touch is never consumed by this window.
 */
class GhostOverlayWindow(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var view: GhostRingView? = null
    private val hideRunnable = Runnable { hide() }

    val isAllowed: Boolean get() = Settings.canDrawOverlays(context)

    /** Show (or refresh) the ring for [visibleMs]; subsequent calls extend the visibility. */
    fun show(liveDeg: Float, targetDeg: Float, toleranceDeg: Float, snapped: Boolean, instruction: String, visibleMs: Long = 4000) {
        if (!isAllowed) return
        val v = view ?: GhostRingView(context).also { created ->
            // Pleos Connect rear-camera layout (2560x1440, AVD-measured): under the main video, the two
            // small side views leave an empty white strip in the middle (x 1491-2020, y 963-1300). The
            // card sits centred in that strip so it never covers the main rear video or the side views.
            // Overlay windows are placed in full-screen coordinates; resources.displayMetrics is only the
            // app pane on Pleos (1682x1210), so use the real display size.
            val dm = android.util.DisplayMetrics().also { @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(it) }
            val sw = dm.widthPixels.toFloat(); val sh = dm.heightPixels.toFloat()
            val w = (sw * SLOT_W).toInt(); val h = (sh * SLOT_H).toInt()
            val lp = WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (sw * SLOT_CX - w / 2f).toInt()
                y = (sh * SLOT_CY - h / 2f).toInt()
            }
            Log.i(TAG, "display=${dm.widthPixels}x${dm.heightPixels} card=${lp.width}x${lp.height}@${lp.x},${lp.y}")
            runCatching { wm.addView(created, lp) }.onFailure { Log.w(TAG, "overlay rejected: ${it.message}"); return }
            view = created
        }
        v.update(liveDeg, targetDeg, toleranceDeg, snapped, instruction)
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, visibleMs)
    }

    fun hide() {
        handler.removeCallbacks(hideRunnable)
        view?.let { runCatching { wm.removeViewImmediate(it) } }
        view = null
    }

    /** Plain-View twin of the Compose GhostRing so it can live in a WindowManager window. */
    private class GhostRingView(context: Context) : View(context) {
        private var live = 0f; private var target = 0f; private var tol = 5f; private var snapped = false; private var text = ""
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2121418.toInt() }
        private val ghost = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x8C00AAD2.toInt(); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val wheel = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC107.toInt(); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
        private val rect = RectF()

        fun update(liveDeg: Float, targetDeg: Float, toleranceDeg: Float, isSnapped: Boolean, instruction: String) {
            live = liveDeg; target = targetDeg; tol = toleranceDeg; snapped = isSnapped; text = instruction
            invalidate()
        }

        override fun onDraw(c: Canvas) {
            // Landscape card: wheel on the left, one-line instruction (wrapped) on the right.
            val w = width.toFloat(); val h = height.toFloat()
            rect.set(0f, 0f, w, h)
            c.drawRoundRect(rect, h * 0.12f, h * 0.12f, bg)
            val r = min(h * 0.36f, w * 0.19f)
            val cx = h * 0.08f + r * 1.05f; val cy = h / 2f
            ghost.strokeWidth = r * 0.07f
            drawWheel(c, cx, cy, r * 0.92f, target, ghost)
            wheel.color = when { snapped -> 0xFF00E676.toInt(); abs(live - target) <= tol * 2.5f -> 0xFFF2F4F7.toInt(); else -> 0xFFFF5252.toInt() }
            wheel.strokeWidth = r * 0.09f
            drawWheel(c, cx, cy, r * 0.8f, live, wheel)
            if (!snapped) {
                hint.strokeWidth = r * 0.05f
                rect.set(cx - r, cy - r, cx + r, cy + r)
                c.drawArc(rect, -90f + live, (target - live).coerceIn(-60f, 60f), false, hint)
            }
            val left = cx + r * 1.15f
            val textW = w - left - h * 0.08f
            label.textAlign = Paint.Align.LEFT
            label.color = if (snapped) 0xFF00E676.toInt() else Color.WHITE
            label.textSize = h * 0.095f
            val perLine = (textW / label.textSize).toInt().coerceAtLeast(6)
            val lines = wrap(text, perLine)
            val lineH = label.textSize * 1.3f
            val top = cy - (lines.size - 1) * lineH / 2f + label.textSize * 0.35f
            lines.forEachIndexed { i, s -> c.drawText(s, left, top + i * lineH, label) }
        }

        /** Same emblem wheel as the Compose GhostRing (see WheelEmblem). */
        private fun drawWheel(c: Canvas, cx: Float, cy: Float, radius: Float, deg: Float, p: Paint) {
            c.save(); c.rotate(deg, cx, cy)
            c.drawCircle(cx, cy, radius, p)
            val rx = radius * WheelEmblem.OVAL_RX
            val ry = radius * WheelEmblem.OVAL_RY
            c.drawLine(cx - radius * WheelEmblem.SPOKE_OUTER, cy, cx - rx, cy, p)
            c.drawLine(cx + rx, cy, cx + radius * WheelEmblem.SPOKE_OUTER, cy, p)
            c.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), p)
            val l = WheelEmblem.LINES
            for (i in l.indices step 4) c.drawLine(cx + l[i] * radius, cy + l[i + 1] * radius, cx + l[i + 2] * radius, cy + l[i + 3] * radius, p)
            val dot = p.style; p.style = Paint.Style.FILL
            c.drawCircle(cx, cy - radius, p.strokeWidth * 1.3f, p)
            p.style = dot
            c.restore()
        }

        private fun wrap(s: String, n: Int): List<String> {
            if (s.length <= n) return listOf(s)
            val out = ArrayList<String>(); var line = StringBuilder()
            for (word in s.split(' ')) {
                if (line.isNotEmpty() && line.length + 1 + word.length > n) { out += line.toString(); line = StringBuilder() }
                if (line.isNotEmpty()) line.append(' ')
                line.append(word)
            }
            if (line.isNotEmpty()) out += line.toString()
            return out.take(5)
        }
    }

    companion object {
        private const val TAG = "GhostOverlay"
        // Empty strip between the two bottom side views of the system rear camera, as display fractions.
        private const val SLOT_CX = 0.6857f  // (1491 + 2020) / 2 / 2560
        private const val SLOT_CY = 0.7858f  // (963 + 1300) / 2 / 1440
        private const val SLOT_W = 0.195f    // ~500 px of the 529 px strip
        private const val SLOT_H = 0.215f    // ~310 px of the 337 px strip
    }
}
