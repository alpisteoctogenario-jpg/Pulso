package app.pulso

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** Panel flotante arrastrable sobre cualquier app (agárralo por la línea del nivel). */
class Bubble(private val ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private var info: TextView? = null
    private var joi: TextView? = null
    private var ask: LinearLayout? = null

    private fun btn(t: String, f: () -> Unit) = Button(ctx).apply {
        text = t; textSize = 13f; minWidth = 0; minimumWidth = 0; setPadding(18, 0, 18, 0); setOnClickListener { f() }
    }

    fun show() {
        if (root != null || !Settings.canDrawOverlays(ctx)) return
        val r = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xCC1E1E1E.toInt()); setPadding(20, 12, 20, 12) }
        val i = TextView(ctx).apply { setTextColor(Color.WHITE); textSize = 16f; text = "Pulso" }
        val j = TextView(ctx).apply { setTextColor(0xFFFF80AB.toInt()); textSize = 14f; maxWidth = 640; visibility = View.GONE }
        val a = LinearLayout(ctx).apply {
            visibility = View.GONE
            addView(btn("Más") { Engine.answer("more") }); addView(btn("Igual") { Engine.answer("same") })
            addView(btn("Menos") { Engine.answer("less") }); addView(btn("Cambiar") { Engine.answer("change") })
        }
        val row = LinearLayout(ctx).apply {
            addView(btn("−") { Engine.adjust(-1) }); addView(btn("+") { Engine.adjust(1) })
            addView(btn("😍") { Engine.rate(5) }); addView(btn("😐") { Engine.rate(2) }); addView(btn("■") { Engine.stop() })
        }
        r.addView(i); r.addView(j); r.addView(a); r.addView(row)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 20; y = 200 }
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0
        i.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y }
                MotionEvent.ACTION_MOVE -> { lp.x = ox + (e.rawX - sx).toInt(); lp.y = oy + (e.rawY - sy).toInt(); wm.updateViewLayout(r, lp) }
            }
            true
        }
        runCatching { wm.addView(r, lp); root = r; info = i; joi = j; ask = a }
    }

    fun update(s: Ui) {
        if (root == null) return
        info?.text = "⠿ ${s.level} · ${PATTERNS.find { it.id == s.curPat }?.name ?: ""} · caña ${(s.gain * 100).roundToInt()}%"
        joi?.apply { text = s.joiText; visibility = if (s.joiText.isEmpty()) View.GONE else View.VISIBLE }
        ask?.visibility = if (s.ask) View.VISIBLE else View.GONE
    }

    fun hide() { root?.let { runCatching { wm.removeView(it) }; root = null } }
}
