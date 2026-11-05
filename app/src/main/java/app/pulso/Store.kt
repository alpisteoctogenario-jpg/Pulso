package app.pulso

import android.content.Context
import org.json.JSONObject
import java.io.File

class Stat(var n: Int = 0, var sum: Double = 0.0)

/** Memoria de preferencias (100 % local, un JSON en el almacenamiento privado de la app). */
object Store {
    private var file: File? = null
    val pat = mutableMapOf<String, Stat>()
    val lvl = mutableMapOf<Int, Stat>()
    var sessions = 0

    fun init(c: Context) {
        file = File(c.filesDir, "learn.json")
        runCatching {
            val j = JSONObject(file!!.readText())
            sessions = j.optInt("sessions")
            j.getJSONObject("pat").let { o -> o.keys().forEach { k -> val x = o.getJSONObject(k); pat[k] = Stat(x.getInt("n"), x.getDouble("sum")) } }
            j.getJSONObject("lvl").let { o -> o.keys().forEach { k -> val x = o.getJSONObject(k); lvl[k.toInt()] = Stat(x.getInt("n"), x.getDouble("sum")) } }
        }
    }

    @Synchronized fun add(p: String, l: Int, r: Double, w: Int = 1) {
        pat.getOrPut(p) { Stat() }.also { it.n += w; it.sum += r * w }
        lvl.getOrPut(l) { Stat() }.also { it.n += w; it.sum += r * w }
        save()
    }
    fun sessionDone() { sessions++; save() }
    // Media suavizada con prior de 3/5 (peso 2) para no fiarse de 1 sola valoración
    fun score(id: String): Double { val s = pat[id]; return (6.0 + (s?.sum ?: 0.0)) / (2 + (s?.n ?: 0)) }
    fun favLevel(): Int? = lvl.filter { it.value.n >= 2 }.maxByOrNull { (6.0 + it.value.sum) / (2 + it.value.n) }?.key
    fun reset() { pat.clear(); lvl.clear(); sessions = 0; save() }

    @Synchronized private fun save() {
        runCatching {
            val j = JSONObject()
            j.put("sessions", sessions)
            j.put("pat", JSONObject().also { o -> pat.forEach { (k, v) -> o.put(k, JSONObject().put("n", v.n).put("sum", v.sum)) } })
            j.put("lvl", JSONObject().also { o -> lvl.forEach { (k, v) -> o.put(k.toString(), JSONObject().put("n", v.n).put("sum", v.sum)) } })
            file?.writeText(j.toString())
        }
    }
}
