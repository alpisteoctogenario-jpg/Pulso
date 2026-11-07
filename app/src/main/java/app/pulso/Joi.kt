package app.pulso

import kotlin.random.Random

class Step(val text: String, val mul: Double, val dur: Double, val gap: Double, val pause: Double = 0.0, val edge: Boolean = false)

/** Guion de órdenes: calentamiento, subidas y bajadas, pausas (edging) y final. Las frases están en Talk.kt. */
object Joi {
    fun next(t: Double, goal: Double, edges: Int, since: Double): Step {
        val f = t / goal
        return when {
            f < 0.2 -> Step(Talk.line("s_warm"), 0.6, 12.0, 10.0)
            f < 0.85 ->
                if (edges < 3 && since > 90 && f > 0.35 && Random.nextFloat() < 0.4f)
                    Step(Talk.line("edge"), 0.5, 8.0, 14.0, 5.0 + Random.nextInt(6), true)
                else when (Random.nextInt(3)) {
                    0 -> Step(Talk.line("s_slow"), 0.7, 15.0, 10.0)
                    1 -> Step(Talk.line("s_fast"), 1.3, 15.0, 10.0)
                    else -> Step(Talk.line("s_hold"), 1.0, 15.0, 12.0)
                }
            f < 1.1 -> Step(Talk.line("s_fin"), 1.4, 25.0, 10.0)
            else -> Step(Talk.line("s_end"), 1.0, 30.0, 45.0)
        }
    }
}
