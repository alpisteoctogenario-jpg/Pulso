package app.pulso

import kotlin.random.Random

class Step(val text: String, val mul: Double, val dur: Double, val gap: Double, val pause: Double = 0.0, val edge: Boolean = false)

/** Guion del modo JOI: calentamiento, subida con altibajos, pausas y final. Edita las frases a tu gusto. */
object Joi {
    private val warm = listOf("Empieza despacio… sin prisa. Quiero que sientas cada segundo.", "Respira hondo y relájate. Deja que el ritmo trabaje por ti.", "Muy suave al principio. Disfruta de cómo se va calentando todo.")
    private val slow = listOf("Más despacio… no tengas prisa.", "Baja el ritmo. Aguanta un poquito más.", "Suave, suave… contrólate.")
    private val fast = listOf("Ahora más rápido. No pares.", "Sube el ritmo, lo estás haciendo muy bien.", "Más fuerte… así, justo así.")
    private val hold = listOf("Mantén ese ritmo exacto. No cambies nada.", "Justo ahí. Quédate en ese punto.")
    private val edge = listOf("Para. Quieto. Respira… todavía no.", "Suelta y espera. Aún no te dejo terminar.", "Alto ahí. Respira hondo y aguanta.")
    private val fin = listOf("Ya casi… sube el ritmo. Ahora sí.", "Es el momento. Déjate llevar, no te contengas.")
    private val end = listOf("Cuando quieras… termina tú.", "Ya puedes dejarte ir. Disfruta.")

    fun next(t: Double, goal: Double, edges: Int, since: Double): Step {
        val f = t / goal
        return when {
            f < 0.2 -> Step(warm.random(), 0.6, 12.0, 10.0)
            f < 0.85 ->
                if (edges < 3 && since > 90 && f > 0.35 && Random.nextFloat() < 0.4f)
                    Step(edge.random(), 0.5, 8.0, 14.0, 5.0 + Random.nextInt(6), true)
                else when (Random.nextInt(3)) {
                    0 -> Step(slow.random(), 0.7, 15.0, 10.0)
                    1 -> Step(fast.random(), 1.3, 15.0, 10.0)
                    else -> Step(hold.random(), 1.0, 15.0, 12.0)
                }
            f < 1.1 -> Step(fin.random(), 1.4, 25.0, 10.0)
            else -> Step(end.random(), 1.0, 30.0, 45.0)
        }
    }
}
