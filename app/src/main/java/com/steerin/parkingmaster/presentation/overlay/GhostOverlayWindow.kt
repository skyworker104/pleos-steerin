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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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
            val size = (min(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels) * 0.34f).toInt()
            val lp = WindowManager.LayoutParams(
                size, (size * 1.18f).toInt(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = (size * 0.12f).toInt()
                y = (size * 0.25f).toInt()
            }
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
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9121418.toInt() }
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
            val w = width.toFloat(); val h = height.toFloat()
            rect.set(0f, 0f, w, h)
            c.drawRoundRect(rect, w * 0.08f, w * 0.08f, bg)
            val r = w * 0.36f
            val cx = w / 2f; val cy = r + w * 0.08f
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
            label.textSize = w * 0.075f
            val lines = wrap(text, 14)
            lines.forEachIndexed { i, s -> c.drawText(s, cx, cy + r + w * 0.13f + i * label.textSize * 1.25f, label) }
        }

        private fun drawWheel(c: Canvas, cx: Float, cy: Float, radius: Float, deg: Float, p: Paint) {
            c.save(); c.rotate(deg, cx, cy)
            c.drawCircle(cx, cy, radius, p)
            c.drawCircle(cx, cy, radius * 0.16f, p)
            for (a in listOf(90f, 210f, 330f)) {
                val rad = Math.toRadians(a.toDouble())
                c.drawLine(cx + cos(rad).toFloat() * radius * 0.18f, cy + sin(rad).toFloat() * radius * 0.18f, cx + cos(rad).toFloat() * radius * 0.96f, cy + sin(rad).toFloat() * radius * 0.96f, p)
            }
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
            return out.take(3)
        }
    }

    companion object { private const val TAG = "GhostOverlay" }
}
