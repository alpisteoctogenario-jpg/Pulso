package app.pulso

import kotlin.math.PI
import kotlin.math.sin

/** f(t) devuelve 0..1 (t en segundos desde que empezó el patrón). */
class Pat(val id: String, val name: String, val f: (Double) -> Double)

val PATTERNS = listOf(
    Pat("const", "Constante") { 0.7 },
    Pat("wave", "Ola") { t -> 0.5 + 0.5 * sin(2 * PI * t / 8 - PI / 2) },
    Pat("pulse", "Pulsos") { t -> if (t % 1.0 < 0.5) 1.0 else 0.15 },
    Pat("heart", "Latido") { t -> val p = t % 1.2; if (p < 0.15 || p in 0.3..0.45) 1.0 else 0.2 },
    Pat("ramp", "Escalada") { t -> (t % 40) / 40 },
    Pat("tease", "Provocación") { t -> val c = t % 30; if (c < 22) c / 22 * 0.9 else if (c < 26) 0.0 else 0.3 },
    Pat("rand", "Aleatorio") { t -> 0.5 + 0.5 * (sin(t * 1.3) * 0.6 + sin(t * 2.9 + 1) * 0.4) },
    Pat("fast", "Rápido") { t -> 0.6 + 0.4 * sin(2 * PI * t * 3) }
)
