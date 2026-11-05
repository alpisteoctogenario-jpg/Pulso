package app.pulso

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object Io { var export: () -> Unit = {}; var import: () -> Unit = {} }

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Engine.init(this)
        val p = buildList {
            if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT) }
            else add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissions(p.toTypedArray(), 1)
        val exp = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
            u?.let { runCatching { contentResolver.openOutputStream(it)?.use { o -> o.write(Store.toJson().toByteArray()) } }.onSuccess { Engine.toast("Exportado ✔") }.onFailure { Engine.toast("Error al exportar") } }
        }
        val imp = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
            u?.let {
                val ok = runCatching { Store.load(contentResolver.openInputStream(it)!!.bufferedReader().readText()) }.getOrDefault(false)
                Engine.reloadCfg(); Engine.toast(if (ok) "Importado ✔" else "Archivo no válido")
            }
        }
        Io.export = { exp.launch("pulso-backup.json") }
        Io.import = { imp.launch(arrayOf("application/json", "*/*")) }
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { App() } } }
    }
}

@Composable fun Opt(t: String, sel: Boolean, m: Modifier = Modifier, f: () -> Unit) {
    if (sel) Button(f, m, contentPadding = PaddingValues(8.dp, 0.dp)) { Text(t) } else OutlinedButton(f, m, contentPadding = PaddingValues(8.dp, 0.dp)) { Text(t) }
}

@Composable fun Sw(label: String, v: Boolean, f: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Switch(v, f); Text(label) }
}

@Composable fun App() {
    val s by Engine.ui.collectAsState()
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Pulso", style = MaterialTheme.typography.headlineMedium)
        Text(s.status)
        if (!s.connected && !s.running) Button({ Engine.connect() }) { Text("Conectar dispositivo") }
        if (s.msg.isNotEmpty()) Text(s.msg, color = Color(0xFFE91E63))
        when {
            s.review != null -> Review(s.review!!)
            s.running -> Running(s)
            else -> {
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp)) {
                    listOf("Sesión", "Historial", "Ajustes").forEachIndexed { i, n -> Opt(n, tab == i, Modifier.weight(1f)) { tab = i } }
                }
                when (tab) { 0 -> Setup(s); 1 -> History(); else -> Config(s) }
            }
        }
    }
}

@Composable fun Setup(s: Ui) {
    val modes = listOf("auto" to "✨ Auto (aprende)") + PATTERNS.map { it.id to it.name }
    Text("Patrón")
    modes.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) { row.forEach { (id, n) -> Opt(n, s.mode == id, Modifier.weight(1f)) { Engine.setMode(id) } } }
    }
    Text("Intensidad máxima (tope): ${s.maxLvl} / 20")
    Slider(s.maxLvl.toFloat(), { Engine.setMax(it.roundToInt()) }, valueRange = 1f..20f)
    Text("Caña general: ${(s.gain * 100).roundToInt()}%  (sube o baja todo sin tocar el tope)")
    Slider(s.gain.toFloat(), { Engine.setGain(it.toDouble()) }, valueRange = 0.4f..1.6f)
    Text("Apagado automático")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(0 to "∞", 10 to "10m", 20 to "20m", 30 to "30m", 60 to "60m").forEach { (m, n) -> Opt(n, s.timerMin == m) { Engine.setTimer(m) } } }
    Sw("Modo JOI (te voy diciendo qué hacer)", s.joi) { Engine.setJoi(it) }
    if (s.joi) {
        Text("Duración objetivo del guion")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(10, 15, 20, 30).forEach { Opt("${it}m", s.joiGoal == it) { Engine.setGoal(it) } } }
    }
    Button({ Engine.start() }, Modifier.fillMaxWidth().height(60.dp), enabled = s.connected) { Text("▶ EMPEZAR", fontSize = 20.sp) }
    Text(Store.summary(), style = MaterialTheme.typography.bodySmall)
}

@Composable fun Running(s: Ui) {
    Text("${s.level}", fontSize = 96.sp)
    Text((PATTERNS.find { it.id == s.curPat }?.name ?: "") + "  ·  %d:%02d".format(s.elapsed / 60, s.elapsed % 60))
    if (s.joiText.isNotEmpty()) Text("💬 ${s.joiText}", color = Color(0xFFFF80AB), fontSize = 18.sp)
    if (s.ask) {
        Text("¿Cómo vas?", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp)) {
            Opt("Más", false, Modifier.weight(1f)) { Engine.answer("more") }; Opt("Igual", false, Modifier.weight(1f)) { Engine.answer("same") }
            Opt("Menos", false, Modifier.weight(1f)) { Engine.answer("less") }; Opt("Cambiar", false, Modifier.weight(1f)) { Engine.answer("change") }
        }
    }
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
        OutlinedButton({ Engine.adjust(-1) }, Modifier.weight(1f)) { Text("− caña") }
        Text("${(s.gain * 100).roundToInt()}%")
        OutlinedButton({ Engine.adjust(1) }, Modifier.weight(1f)) { Text("+ caña") }
    }
    Text("¿Cuánto te gusta ahora?")
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp)) {
        listOf("😐", "🙂", "😊", "😍", "🔥").forEachIndexed { i, e -> OutlinedButton({ Engine.rate(i + 1) }, Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text(e, fontSize = 22.sp) } }
    }
    Sw("Modo JOI", s.joi) { Engine.setJoi(it) }
    Button({ Engine.stop() }, Modifier.fillMaxWidth().height(70.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))) { Text("■ PARAR", fontSize = 22.sp) }
}

@Composable fun History() {
    val h = Store.hist
    if (h.isEmpty()) { Text("Aún no hay sesiones (se guardan las de más de 30 s)."); return }
    val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()) }
    Text("Verde: duración muy buena · Amarillo: normal/justa · Rojo: corta, comparado con tus sesiones de intensidad parecida", style = MaterialTheme.typography.bodySmall)
    h.indices.reversed().forEach { i ->
        val x = h[i]; val (exp, ratio) = Store.verdict(i)
        val (c, label) = when {
            ratio >= 0.9 -> Color(0xFF2E7D32) to "Duración muy buena"
            ratio >= 0.55 -> Color(0xFFF9A825) to "Normal / justa"
            else -> Color(0xFFC62828) to "Corta para esa intensidad"
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(18.dp).background(c, CircleShape))
            Column {
                Text("${fmt.format(Date(x.ts))} · %d:%02d".format(x.dur / 60, x.dur % 60))
                Text("$label · media %.1f · pico ${x.peak} · esperado ≈ %.0f min".format(x.avg, exp) + (if (x.mood > 0) " · ánimo %.1f/5".format(x.mood) else ""), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable fun Config(s: Ui) {
    val ctx = LocalContext.current
    val canDraw = Settings.canDrawOverlays(ctx)
    Sw("Burbuja flotante sobre otras apps", s.bubble) { Engine.setBubble(it) }
    if (!canDraw) Button({ ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))) }) { Text("Conceder permiso de burbuja") }
    else Text("Permiso de burbuja concedido ✔", style = MaterialTheme.typography.bodySmall)
    Text("Preguntar «¿cómo vas?» cada…")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(0 to "No", 2 to "2m", 3 to "3m", 5 to "5m", 10 to "10m").forEach { (m, n) -> Opt(n, s.checkMin == m) { Engine.setCheck(m) } } }
    Sw("Voz (JOI y preguntas)", s.voice) { Engine.setVoice(it) }
    OutlinedButton({ Engine.nextVoice() }) { Text("Cambiar voz (prueba varias)") }
    Button({ Io.export() }, Modifier.fillMaxWidth()) { Text("Exportar preferencias e historial") }
    OutlinedButton({ Io.import() }, Modifier.fillMaxWidth()) { Text("Importar copia de seguridad") }
    TextButton({ Store.reset(); Engine.toast("Aprendizaje e historial borrados") }) { Text("Borrar lo aprendido y el historial") }
}

@Composable fun Review(r: List<Sample>) {
    var idx by remember { mutableStateOf(r.size / 2f) }
    val i = idx.roundToInt().coerceIn(0, r.size - 1)
    val x = r[i]
    Text("¿Cuál fue tu momento favorito?", style = MaterialTheme.typography.titleMedium)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val path = Path()
        r.forEachIndexed { k, v ->
            val px = size.width * k / (r.size - 1); val py = size.height * (1 - v.lvl / 20f)
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, Color(0xFFE91E63), style = Stroke(3f))
        val cx = size.width * i / (r.size - 1)
        drawLine(Color.White, Offset(cx, 0f), Offset(cx, size.height), 3f)
    }
    Slider(idx, { idx = it }, valueRange = 0f..(r.size - 1).toFloat())
    Text("%d:%02d · %s · nivel %d".format(x.t / 60, x.t % 60, PATTERNS.find { it.id == x.pat }?.name ?: x.pat, x.lvl))
    Button({ Engine.favorite(i) }, Modifier.fillMaxWidth()) { Text("⭐ Este fue mi favorito") }
    TextButton({ Engine.closeReview() }) { Text("Saltar") }
}
