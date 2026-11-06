package app.pulso

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Burbuja flotante sobre otras apps. Círculo con el nivel actual; arrástralo por la pantalla.
 * Toca para abrir/cerrar el panel (− + 😍 😐 ■). Los mensajes y las preguntas la abren solos un rato.
 */
class Bubble(private val base: Context) {
    private val ctx: Context = ContextThemeWrapper(base, android.R.style.Theme_DeviceDefault)
    private val wm = base.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val dens = base.resources.displayMetrics.density
    private val h = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var head: TextView? = null
    private var panel: LinearLayout? = null
    private var joi: TextView? = null
    private var ask: LinearLayout? = null
    private var expanded = false
    private var pinned = false
    private var last = Ui()
    private var lastText = ""
    private var lastAsk = false
    var onError: (String) -> Unit = {}
    private val collapse = Runnable { if (!pinned) setExpanded(false) }

    private fun dp(v: Int) = (v * dens).toInt()
    private fun shape(c: Int, r: Int) = GradientDrawable().apply { setColor(c); cornerRadius = dp(r).toFloat() }
    private fun btn(t: String, f: () -> Unit) = Button(ctx).apply {
        text = t; textSize = 13f; minWidth = 0; minimumWidth = 0; setPadding(dp(10), 0, dp(10), 0); setOnClickListener { f() }
    }

    fun show() {
        if (root != null) return
        if (!Settings.canDrawOverlays(base)) return
        try {
            val hd = TextView(ctx).apply {
                setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD)
                background = shape(0xEEE91E63.toInt(), 28); minWidth = dp(56); minHeight = dp(56); setPadding(dp(14), dp(8), dp(14), dp(8)); text = "·"
            }
            val j = TextView(ctx).apply { setTextColor(0xFFFF80AB.toInt()); textSize = 14f; maxWidth = dp(260); visibility = View.GONE }
            val a = LinearLayout(ctx).apply {
                visibility = View.GONE
                addView(btn("Más") { Engine.answer("more") }); addView(btn("Igual") { Engine.answer("same") })
                addView(btn("Menos") { Engine.answer("less") }); addView(btn("Cambiar") { Engine.answer("change") })
            }
            val row = LinearLayout(ctx).apply {
                addView(btn("−") { Engine.adjust(-1) }); addView(btn("+") { Engine.adjust(1) })
                addView(btn("😍") { Engine.rate(5) }); addView(btn("😐") { Engine.rate(2) }); addView(btn("■") { Engine.stop() })
            }
            val p = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; background = shape(0xE61E1E1E.toInt(), 14)
                setPadding(dp(10), dp(8), dp(10), dp(8)); visibility = View.GONE
                addView(j); addView(a); addView(row)
            }
            val r = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; addView(hd); addView(p) }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(160) }
            var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
            hd.setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - sx; val dy = e.rawY - sy
                        if (abs(dx) > 12 || abs(dy) > 12) moved = true
                        if (moved) { lp.x = ox + dx.toInt(); lp.y = oy + dy.toInt(); wm.updateViewLayout(r, lp) }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) { pinned = !expanded; h.removeCallbacks(collapse); setExpanded(!expanded) }
                }
                true
            }
            wm.addView(r, lp)
            root = r; head = hd; panel = p; joi = j; ask = a
            update(last)
        } catch (e: Exception) { onError(e.message ?: "error desconocido") }
    }

    private fun setExpanded(v: Boolean) { expanded = v; panel?.visibility = if (v) View.VISIBLE else View.GONE; refreshHead() }
    private fun refreshHead() {
        head?.text = if (expanded) "⠿ ${last.level} · ${PATTERNS.find { it.id == last.curPat }?.name ?: ""} · ${(last.gain * 100).roundToInt()}%" else "${last.level}"
    }

    fun update(s: Ui) {
        last = s
        if (root == null) return
        joi?.apply { text = s.joiText; visibility = if (s.joiText.isEmpty()) View.GONE else View.VISIBLE }
        ask?.visibility = if (s.ask) View.VISIBLE else View.GONE
        if (s.ask) { h.removeCallbacks(collapse); setExpanded(true) }
        else if (lastAsk && !pinned) h.postDelayed(collapse, 3000)
        lastAsk = s.ask
        if (s.joiText != lastText) {
            lastText = s.joiText
            if (s.joiText.isNotEmpty() && !s.ask) { setExpanded(true); h.removeCallbacks(collapse); if (!pinned) h.postDelayed(collapse, 9000) }
        }
        refreshHead()
    }

    fun hide() {
        h.removeCallbacksAndMessages(null)
        root?.let { runCatching { wm.removeView(it) }; root = null }
        expanded = false; pinned = false
    }
}
