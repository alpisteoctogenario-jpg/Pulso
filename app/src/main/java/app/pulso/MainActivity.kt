package app.pulso

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

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
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { App() } } }
    }
}

@Composable fun Opt(t: String, sel: Boolean, m: Modifier = Modifier, f: () -> Unit) {
    if (sel) Button(f, m) { Text(t) } else OutlinedButton(f, m) { Text(t) }
}

@Composable fun App() {
    val s by Engine.ui.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Pulso", style = MaterialTheme.typography.headlineMedium)
        Text(s.status)
        if (!s.connected && !s.running) Button({ Engine.connect() }) { Text("Conectar dispositivo") }
        if (s.msg.isNotEmpty()) Text(s.msg, color = Color(0xFFE91E63))
        when {
            s.review != null -> Review(s.review!!)
            s.running -> Running(s)
            else -> Setup(s)
        }
    }
}

@Composable fun Setup(s: Ui) {
    val modes = listOf("auto" to "✨ Auto (aprende)") + PATTERNS.map { it.id to it.name }
    Text("Patrón")
    modes.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) { row.forEach { (id, n) -> Opt(n, s.mode == id, Modifier.weight(1f)) { Engine.setMode(id) } } }
    }
    Text("Intensidad máxima: ${s.maxLvl} / 20")
    Slider(s.maxLvl.toFloat(), { Engine.setMax(it.roundToInt()) }, valueRange = 1f..20f)
    Text("Apagado automático")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0 to "∞", 10 to "10m", 20 to "20m", 30 to "30m", 60 to "60m").forEach { (m, n) -> Opt(n, s.timerMin == m) { Engine.setTimer(m) } }
    }
    Button({ Engine.start() }, Modifier.fillMaxWidth().height(60.dp), enabled = s.connected) { Text("▶ EMPEZAR", fontSize = 20.sp) }
    val top = Store.pat.keys.sortedByDescending { Store.score(it) }.take(3)
    Text("Aprendido (${Store.sessions} sesiones): " + if (top.isEmpty()) "aún nada, valora durante la sesión." else
        top.joinToString { id -> (PATTERNS.find { it.id == id }?.name ?: id) + " %.1f".format(Store.score(id)) } +
        (Store.favLevel()?.let { " · nivel favorito ≈ $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
    TextButton({ Store.reset(); Engine.closeReview() }) { Text("Borrar lo aprendido") }
}

@Composable fun Running(s: Ui) {
    Text("${s.level}", fontSize = 96.sp)
    Text((PATTERNS.find { it.id == s.curPat }?.name ?: "") + "  ·  %d:%02d".format(s.elapsed / 60, s.elapsed % 60))
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton({ Engine.adjust(-1) }) { Text("−") }
        Text("Máx ${s.maxLvl}")
        OutlinedButton({ Engine.adjust(1) }) { Text("+") }
    }
    Text("¿Cuánto te gusta ahora?")
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp)) {
        listOf("😐", "🙂", "😊", "😍", "🔥").forEachIndexed { i, e -> OutlinedButton({ Engine.rate(i + 1) }, Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text(e, fontSize = 22.sp) } }
    }
    Button({ Engine.stop() }, Modifier.fillMaxWidth().height(70.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))) { Text("■ PARAR", fontSize = 22.sp) }
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
