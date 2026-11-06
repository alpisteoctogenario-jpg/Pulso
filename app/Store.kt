package app.pulso

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

class Stat(var n: Double = 0.0, var sum: Double = 0.0)
class Sess(val ts: Long, val dur: Int, val avg: Double, val peak: Int, val mood: Double)

/**
 * Memoria local. Claves de m:
 *  p|pat        valoración global del patrón
 *  pp|pat|fase  valoración del patrón según fase de la sesión (0 inicio, 1 medio, 2 larga)
 *  l|pat|nivel  valoración de ese nivel de pico para ese patrón
 *  L|nivel      valoración global de ese nivel
 */
object Store {
    private var file: File? = null
    private val rnd = java.util.Random()
    val m = mutableMapOf<String, Stat>()
    val hist = mutableListOf<Sess>()
    val conf = mutableMapOf<String, Double>()
    var voiceName = ""

    fun cfg(k: String, d: Double) = conf[k] ?: d

    fun init(c: Context) {
        file = File(c.filesDir, "learn.json")
        runCatching { load(file!!.readText(), false) }
    }

    @Synchronized fun load(s: String, persist: Boolean = true): Boolean = runCatching {
        val j = JSONObject(s)
        val nm = mutableMapOf<String, Stat>(); val nh = mutableListOf<Sess>(); val nc = mutableMapOf<String, Double>()
        if (j.has("pat")) { // formato antiguo (v1)
            val o = j.getJSONObject("pat")
            o.keys().forEach { k -> val x = o.getJSONObject(k); nm["p|$k"] = Stat(x.getDouble("n"), x.getDouble("sum")) }
        } else {
            val o = j.getJSONObject("m")
            o.keys().forEach { k -> val a = o.getJSONArray(k); nm[k] = Stat(a.getDouble(0), a.getDouble(1)) }
            val h = j.getJSONArray("hist")
            for (i in 0 until h.length()) { val a = h.getJSONArray(i); nh.add(Sess(a.getLong(0), a.getInt(1), a.getDouble(2), a.getInt(3), a.getDouble(4))) }
            j.optJSONObject("conf")?.let { o2 -> o2.keys().forEach { k -> nc[k] = o2.getDouble(k) } }
        }
        m.clear(); m.putAll(nm); hist.clear(); hist.addAll(nh); conf.clear(); conf.putAll(nc)
        voiceName = j.optString("voice", "")
        if (persist) save()
        true
    }.getOrDefault(false)

    @Synchronized fun toJson(): String {
        val j = JSONObject()
        j.put("v", 2); j.put("voice", voiceName)
        j.put("m", JSONObject().also { o -> m.forEach { (k, v) -> o.put(k, JSONArray().put(v.n).put(v.sum)) } })
        j.put("hist", JSONArray().also { a -> hist.forEach { a.put(JSONArray().put(it.ts).put(it.dur).put(it.avg).put(it.peak).put(it.mood)) } })
        j.put("conf", JSONObject().also { o -> conf.forEach { (k, v) -> o.put(k, v) } })
        return j.toString()
    }

    @Synchronized fun save() { runCatching { file?.writeText(toJson()) } }

    @Synchronized fun add(p: String, l: Int, r: Double, w: Int, ph: Int) {
        fun up(k: String) { m.getOrPut(k) { Stat() }.also { it.n += w; it.sum += r * w } }
        up("p|$p"); up("pp|$p|$ph")
        if (l >= 2) { val b = l / 2 * 2; up("l|$p|$b"); up("L|$b") }
        save()
    }

    fun sessionDone(s: Sess) {
        hist.add(s)
        m.values.forEach { it.n *= 0.97; it.sum *= 0.97 } // olvido suave: los gustos cambian
        save()
    }

    private fun mean(k: String, prior: Double, w: Double): Double { val s = m[k]; return (prior * w + (s?.sum ?: 0.0)) / (w + (s?.n ?: 0.0)) }
    private fun n(k: String) = m[k]?.n ?: 0.0

    /** (media estimada, incertidumbre) del patrón en esa fase. Se usa para Thompson sampling. */
    fun score(p: String, ph: Int): Pair<Double, Double> {
        val g = mean("p|$p", 3.0, 2.0)
        val k = "pp|$p|$ph"
        return mean(k, g, 3.0) to 1.2 / sqrt(1 + n(k) + 0.3 * n("p|$p"))
    }

    /** Elige el pico (nivel) para el patrón: aprende el nivel ideal de cada patrón por separado. */
    fun peakFor(p: String, max: Int): Int {
        val bs = if (max < 2) listOf(max) else (2..max step 2).toList()
        return bs.maxByOrNull { b ->
            val k = "l|$p|$b"; val kg = "L|$b"
            val prior = 3.0 + 0.4 * (1 - abs(b - 0.65 * max) / max)
            mean(k, mean(kg, prior, 2.0), 3.0) + rnd.nextGaussian() / sqrt(1.0 + n(k) + 0.3 * n(kg))
        } ?: max
    }

    fun gauss() = rnd.nextGaussian()

    fun verdict(i: Int): Pair<Double, Double> {
        val x = hist[i]
        val past = hist.subList(0, i).takeLast(30).filter { abs(it.avg - x.avg) <= 3 }.map { it.dur / 60.0 }.sorted()
        val exp = if (past.size >= 3) past[past.size / 2] else maxOf(5.0, 25 - 0.9 * x.avg)
        return exp to (x.dur / 60.0) / exp
    }

    fun summary(): String {
        val pats = PATTERNS.filter { n("p|${it.id}") >= 2 }.sortedByDescending { mean("p|${it.id}", 3.0, 2.0) }.take(3)
        val lv = (2..20 step 2).filter { n("L|$it") >= 2 }.maxByOrNull { mean("L|$it", 3.0, 2.0) }
        if (pats.isEmpty()) return "Aún estoy aprendiendo: valora durante las sesiones."
        return "Tus favoritos: " + pats.joinToString { "${it.name} (%.1f)".format(mean("p|${it.id}", 3.0, 2.0)) } +
            (lv?.let { " · nivel ideal ≈ $it" } ?: "") + " · ${hist.size} sesiones"
    }

    fun reset() { m.clear(); hist.clear(); save() }
}
