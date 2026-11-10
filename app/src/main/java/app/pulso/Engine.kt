package app.pulso

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

data class Sample(val t: Int, val pat: String, val lvl: Int)
data class Ui(
    val status: String = "Desconectado", val connected: Boolean = false,
    val running: Boolean = false, val mode: String = "auto", val curPat: String = "",
    val level: Int = 0, val maxLvl: Int = 10, val gain: Double = 1.0, val timerMin: Int = 0,
    val elapsed: Int = 0, val review: List<Sample>? = null, val msg: String = "",
    val ask: Boolean = false, val joi: Boolean = false, val joiText: String = "", val joiGoal: Int = 15,
    val checkMin: Int = 3, val voice: Boolean = true, val bubble: Boolean = true,
    val instr: Boolean = true, val chat: List<String> = emptyList(), val pitch: Double = 1.1, val rate: Double = 1.0, val voiceName: String = "", val lang: Int = 1
)

object Engine {
    private const val SWITCH = 45.0
    val ui = MutableStateFlow(Ui())
    private lateinit var app: Context
    private var lov: Lovense? = null
    private var job: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = mutableListOf<Sample>()
    private val ratings = mutableListOf<Int>()
    @Volatile private var forceSwitch = false
    @Volatile private var extend = 0.0

    fun init(c: Context) {
        if (::app.isInitialized) return
        app = c.applicationContext
        Store.init(app); Talk.init(app); reloadCfg()
        tts = TextToSpeech(app) { ok -> ttsReady = ok == TextToSpeech.SUCCESS; if (ttsReady) applyVoice() }
        lov = Lovense(app) { s, ok ->
            ui.update { it.copy(status = s, connected = ok) }
            if (!ok && ui.value.running && (s == "Desconectado" || s == "No encontrado"))
                scope.launch { delay(3000); lov?.connect() }
        }
        lov?.onRx = { addRx("RX  $it") }
    }

    fun reloadCfg() = ui.update {
        it.copy(maxLvl = Store.cfg("max", 10.0).toInt(), gain = Store.cfg("gain", 1.0), checkMin = Store.cfg("check", 3.0).toInt(),
            voice = Store.cfg("voice", 1.0) > 0, bubble = Store.cfg("bubble", 1.0) > 0, joiGoal = Store.cfg("goal", 15.0).toInt(),
            instr = Store.cfg("instr", 1.0) > 0, pitch = Store.cfg("pitch", 1.1), rate = Store.cfg("rate", 1.0), voiceName = Store.voiceName,
            lang = Store.cfg("lang", 1.0).toInt().also { l -> Talk.level = l })
    }
    private fun put(k: String, v: Double) { Store.conf[k] = v; Store.save() }

    // ---- voz ----
    private fun voices() = tts?.voices?.filter { it.locale.language == "es" }?.sortedBy { it.name } ?: emptyList()
    fun voiceList(): List<Pair<String, String>> = voices().map { v -> v.name to "${v.locale.displayCountry} · ${v.name}${if (v.isNetworkConnectionRequired) " (online)" else ""}" }
    private fun applyVoice() {
        val t = tts ?: return
        t.language = Locale("es", "ES"); t.setPitch(ui.value.pitch.toFloat()); t.setSpeechRate(ui.value.rate.toFloat())
        val vs = voices()
        (vs.find { it.name == Store.voiceName } ?: vs.firstOrNull { it.name.contains("female", true) || it.features.contains("female") })?.let { t.voice = it }
    }
    fun setVoiceByName(n: String) {
        Store.voiceName = n; Store.save(); applyVoice(); ui.update { it.copy(voiceName = n) }
        tts?.speak("Hola, así suena mi voz. ¿Te gusta?", TextToSpeech.QUEUE_FLUSH, null, "t")
    }
    fun setPitch(v: Double) { put("pitch", v); ui.update { it.copy(pitch = v) }; applyVoice() }
    fun setRate(v: Double) { put("rate", v); ui.update { it.copy(rate = v) }; applyVoice() }
    fun testVoice() { tts?.speak(Talk.line("up"), TextToSpeech.QUEUE_FLUSH, null, "t") }
    private fun speak(s: String) { if (ui.value.voice && ttsReady) tts?.speak(s, TextToSpeech.QUEUE_FLUSH, null, "j") }
    @Volatile private var nowT = 0.0
    @Volatile private var lastSay = -100.0
    private fun canSay(gap: Double = 8.0) = ui.value.running && ui.value.joi && nowT - lastSay >= gap
    private fun say(text: String) {
        if (text.isBlank()) return
        speak(text); lastSay = nowT
        ui.update { it.copy(joiText = text, chat = (it.chat + text).takeLast(6)) }
    }

    // ---- ajustes ----
    fun connect() { lov?.connect() }

    // ---- laboratorio: registro de lo que manda el dispositivo ----
    val rx = MutableStateFlow<List<String>>(emptyList())
    private val tfmt = java.text.SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    fun addRx(l: String) { val t = synchronized(tfmt) { tfmt.format(java.util.Date()) }; rx.update { (it + "$t $l").takeLast(200) } }
    private val allowed = setOf("Battery;", "DeviceType;", "Status:1;", "StartMove:1;", "StopMove:1;")
    fun probe(cmd: String) { if (cmd !in allowed) return; addRx("TX  $cmd"); lov?.sendRaw(cmd) }
    fun clearRx() { rx.value = emptyList() }
    fun setMode(m: String) = ui.update { it.copy(mode = m) }
    fun setMax(v: Int) { val x = v.coerceIn(1, 20); put("max", x.toDouble()); ui.update { it.copy(maxLvl = x) } }
    fun setGain(v: Double) { val g = ((v * 10).roundToInt() / 10.0).coerceIn(0.4, 1.6); put("gain", g); ui.update { it.copy(gain = g) } }
    fun adjust(d: Int) { setGain(ui.value.gain + 0.1 * d); if (canSay(5.0)) say(Talk.line(if (d > 0) "more" else "less")) }
    fun setLang(l: Int) { put("lang", l.toDouble()); Talk.level = l; ui.update { it.copy(lang = l) } }
    fun setInstr(b: Boolean) { put("instr", if (b) 1.0 else 0.0); ui.update { it.copy(instr = b) } }
    fun setTimer(m: Int) = ui.update { it.copy(timerMin = m) }
    fun setJoi(b: Boolean) = ui.update { it.copy(joi = b, joiText = if (b) it.joiText else "") }
    fun setGoal(m: Int) { put("goal", m.toDouble()); ui.update { it.copy(joiGoal = m) } }
    fun setCheck(m: Int) { put("check", m.toDouble()); ui.update { it.copy(checkMin = m) } }
    fun setVoice(b: Boolean) { put("voice", if (b) 1.0 else 0.0); ui.update { it.copy(voice = b) }; if (!b) tts?.stop() }
    fun setBubble(b: Boolean) { put("bubble", if (b) 1.0 else 0.0); ui.update { it.copy(bubble = b) } }
    fun toast(m: String) = ui.update { it.copy(msg = m) }
    fun closeReview() = ui.update { it.copy(review = null, msg = "") }

    // ---- aprendizaje ----
    private val gauss get() = Store.gauss()
    private fun phase(t: Double) = if (t < 300) 0 else if (t < 720) 1 else 2

    /** Thompson sampling: muestrea una "valoración posible" de cada patrón y elige la mayor. Explora donde hay dudas. */
    private fun pickPat(ph: Int, not: String?): Pat {
        if (Random.nextFloat() < 0.08f) return PATTERNS.random()
        return PATTERNS.filter { it.id != not }.maxByOrNull { val (mu, sd) = Store.score(it.id, ph); mu + gauss * sd }!!
    }
    private fun recentLvl(): Int = synchronized(log) { log.takeLast(5).maxOfOrNull { it.lvl } } ?: ui.value.level

    fun start() {
        if (ui.value.running) return
        ContextCompat.startForegroundService(app, Intent(app, EngineService::class.java))
        synchronized(log) { log.clear() }; ratings.clear(); forceSwitch = false; extend = 0.0
        nowT = 0.0; lastSay = -100.0
        ui.update { it.copy(running = true, elapsed = 0, review = null, msg = "", joiText = "", chat = emptyList(), ask = false) }
        job = scope.launch {
            var t = 0.0; var patT = 0.0; var next = SWITCH; var tick = 0
            var cur = pickPat(0, null); var peak = Store.peakFor(cur.id, ui.value.maxLvl)
            var nextAsk = ui.value.checkMin * 60.0; var askUntil = 0.0
            var joiNext = 5.0; var mul = 1.0; var mulUntil = 0.0; var pauseUntil = 0.0; var edges = 0; var lastEdge = -999.0
            var lastId = cur.id; var lastE = 0.0; var wasPaused = false
            if (ui.value.joi) say(Talk.line("start"))
            while (isActive) {
                delay(100); t += 0.1; patT += 0.1; tick++; nowT = t
                val s = ui.value
                if (extend > 0) { next += extend; extend = 0.0 }
                if (s.mode == "auto") {
                    if (t >= next || forceSwitch) { cur = pickPat(phase(t), cur.id); peak = Store.peakFor(cur.id, s.maxLvl); next = t + SWITCH; patT = 0.0; forceSwitch = false }
                } else if (cur.id != s.mode) { cur = PATTERNS.first { it.id == s.mode }; patT = 0.0 }
                val pk = if (s.mode == "auto") peak.toDouble() else s.maxLvl * 0.85

                if (s.joi && s.instr) {
                    if (t >= joiNext) {
                        val st = Joi.next(t, s.joiGoal * 60.0, edges, t - lastEdge)
                        if (st.edge) { edges++; lastEdge = t }
                        mul = st.mul; mulUntil = t + st.dur; pauseUntil = if (st.pause > 0) t + st.pause else 0.0
                        joiNext = t + st.dur + st.gap
                        say(st.text)
                    }
                    if (t > mulUntil) mul = 1.0
                } else { mul = 1.0; pauseUntil = 0.0; if (t >= joiNext) joiNext = t + 5 }
                val paused = t < pauseUntil
                if (wasPaused && !paused && s.joi && canSay(3.0)) say(Talk.line("resume"))
                wasPaused = paused
                val e = cur.avg * pk * s.gain
                if (cur.id != lastId) {
                    if (s.joi && t > 3 && canSay(6.0)) say(Talk.change(cur.id, if (e > lastE * 1.25) true else if (e < lastE * 0.8) false else null))
                    lastId = cur.id
                }
                lastE = e

                if (s.checkMin > 0 && !s.ask && t >= nextAsk) {
                    ui.update { it.copy(ask = true) }; askUntil = t + 25; nextAsk = t + s.checkMin * 60
                    if (s.joi) say(Talk.line("ask")) else speak("¿Cómo vas? ¿Más, igual o menos?")
                }
                if (s.ask && t >= askUntil) ui.update { it.copy(ask = false) }

                val out = if (paused) 0 else (cur.f(patT).coerceIn(0.0, 1.0) * pk * s.gain * mul).roundToInt().coerceIn(0, s.maxLvl)
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
        job?.cancel(); tts?.stop(); val bye = ui.value.joi
        scope.launch { repeat(4) { lov?.send(0, true); delay(150) } }
        val l = synchronized(log) { log.toList() }
        if (l.size >= 30) Store.sessionDone(Sess(System.currentTimeMillis(), l.size, l.map { it.lvl }.average(), l.maxOf { it.lvl }, if (ratings.isEmpty()) 0.0 else ratings.average()))
        ui.update { it.copy(running = false, level = 0, ask = false, joiText = "", review = if (l.size >= 5) l else null) }
        if (bye) speak(Talk.line("bye"))
        app.stopService(Intent(app, EngineService::class.java))
    }

    /** Valoración 1..5: se atribuye al pico de los últimos 5 s. ≤2 cambia de patrón ya; ≥4 lo alarga 30 s. */
    fun rate(r: Int) {
        val s = ui.value
        if (!s.running || s.curPat.isEmpty()) return
        Store.add(s.curPat, recentLvl(), r.toDouble(), 1, phase(s.elapsed.toDouble())); ratings.add(r)
        if (r <= 2) forceSwitch = true else if (r >= 4) extend = 30.0
        if (r >= 5 && canSay(10.0)) say(Talk.line("love"))
        ui.update { it.copy(msg = "Anotado ✔") }
    }

    /** Respuesta a "¿Cómo vas?" */
    fun answer(a: String) {
        val s = ui.value
        if (!s.running) return
        val ph = phase(s.elapsed.toDouble())
        when (a) {
            "more" -> { setGain(s.gain + 0.1); Store.add(s.curPat, recentLvl(), 3.5, 1, ph) }
            "same" -> Store.add(s.curPat, recentLvl(), 4.5, 1, ph)
            "less" -> { setGain(s.gain - 0.1); Store.add(s.curPat, recentLvl(), 2.0, 1, ph) }
            "change" -> forceSwitch = true
        }
        ui.update { it.copy(ask = false) }
    }

    fun favorite(i: Int) {
        val l = ui.value.review ?: return
        val w = l.subList(maxOf(0, i - 4), i + 1)
        Store.add(l[i].pat, w.maxOf { it.lvl }, 5.0, 3, phase(l[i].t.toDouble()))
        ui.update { it.copy(msg = "Momento favorito guardado ⭐", review = null) }
    }
}
