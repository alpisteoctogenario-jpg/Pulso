package app.pulso

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.random.Random

data class Sample(val t: Int, val pat: String, val lvl: Int)
data class Ui(
    val status: String = "Desconectado", val connected: Boolean = false,
    val running: Boolean = false, val mode: String = "auto", val curPat: String = "",
    val level: Int = 0, val maxLvl: Int = 10, val timerMin: Int = 0, val elapsed: Int = 0,
    val review: List<Sample>? = null, val msg: String = ""
)

object Engine {
    private const val SWITCH = 45.0 // segundos entre cambios de patrón en modo Auto
    val ui = MutableStateFlow(Ui())
    private lateinit var app: Context
    private var lov: Lovense? = null
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = mutableListOf<Sample>()

    fun init(c: Context) {
        if (::app.isInitialized) return
        app = c.applicationContext
        Store.init(app)
        lov = Lovense(app) { s, ok ->
            ui.update { it.copy(status = s, connected = ok) }
            if (!ok && ui.value.running && (s == "Desconectado" || s == "No encontrado"))
                scope.launch { delay(3000); lov?.connect() }
        }
    }

    fun connect() { lov?.connect() }
    fun setMode(m: String) = ui.update { it.copy(mode = m) }
    fun setMax(v: Int) = ui.update { it.copy(maxLvl = v.coerceIn(1, 20)) }
    fun adjust(d: Int) = setMax(ui.value.maxLvl + d)
    fun setTimer(m: Int) = ui.update { it.copy(timerMin = m) }
    fun closeReview() = ui.update { it.copy(review = null, msg = "") }

    private fun pick(): Pat {
        if (Random.nextFloat() < 0.25f) return PATTERNS.random() // exploración
        val w = PATTERNS.map { exp(Store.score(it.id) * 1.2) }
        var r = Random.nextDouble() * w.sum()
        for (i in w.indices) { r -= w[i]; if (r <= 0) return PATTERNS[i] }
        return PATTERNS.last()
    }

    fun start() {
        if (ui.value.running) return
        ContextCompat.startForegroundService(app, Intent(app, EngineService::class.java))
        synchronized(log) { log.clear() }
        ui.update { it.copy(running = true, elapsed = 0, review = null, msg = "") }
        job = scope.launch {
            var t = 0.0; var patT = 0.0; var next = SWITCH; var cur = pick(); var tick = 0
            while (isActive) {
                delay(100); t += 0.1; patT += 0.1; tick++
                val s = ui.value
                if (s.mode == "auto") { if (t >= next) { cur = pick(); next = t + SWITCH; patT = 0.0 } }
                else if (cur.id != s.mode) { cur = PATTERNS.first { it.id == s.mode }; patT = 0.0 }
                // En Auto, el pico se acerca al nivel que mejor has valorado (sin pasar del máximo)
                val peak = if (s.mode == "auto") (Store.favLevel()?.plus(3)?.coerceAtMost(s.maxLvl) ?: s.maxLvl) else s.maxLvl
                val out = (cur.f(patT).coerceIn(0.0, 1.0) * peak).roundToInt().coerceIn(0, s.maxLvl)
                lov?.send(out)
                if (tick % 10 == 0) {
                    synchronized(log) { log.add(Sample(tick / 10, cur.id, out)) }
                    ui.update { it.copy(level = out, curPat = cur.id, elapsed = tick / 10) }
                    if (s.timerMin > 0 && t >= s.timerMin * 60) { stop(); break }
                }
            }
        }
    }

    fun stop() {
        if (!ui.value.running) return
        job?.cancel()
        scope.launch { repeat(4) { lov?.send(0, true); delay(150) } }
        val l = synchronized(log) { log.toList() }
        Store.sessionDone()
        ui.update { it.copy(running = false, level = 0, review = if (l.size >= 5) l else null) }
        app.stopService(Intent(app, EngineService::class.java))
    }

    /** Valoración 1..5 durante la sesión: se atribuye al pico de los últimos 5 s (la reacción llega con retraso). */
    fun rate(r: Int) {
        val s = ui.value
        if (!s.running || s.curPat.isEmpty()) return
        val lvl = synchronized(log) { log.takeLast(5).maxOfOrNull { it.lvl } } ?: s.level
        Store.add(s.curPat, lvl, r.toDouble())
        ui.update { it.copy(msg = "Anotado ✔") }
    }

    /** Momento favorito elegido al terminar: cuenta como 3 valoraciones de 5. */
    fun favorite(i: Int) {
        val l = ui.value.review ?: return
        val w = l.subList(maxOf(0, i - 4), i + 1)
        Store.add(l[i].pat, w.maxOf { it.lvl }, 5.0, 3)
        ui.update { it.copy(msg = "Momento favorito guardado ⭐", review = null) }
    }
}
