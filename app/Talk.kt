package app.pulso

import android.content.Context
import java.io.File
import kotlin.random.Random

/** Frases de la "chica". Claves: eventos (start, up, down, same, p_<patrón>, edge, resume, more, less, love, ask, bye) y guion JOI (s_warm, s_slow, s_fast, s_hold, s_fin, s_end). */
object Talk {
    private var file: File? = null
    private var custom: Map<String, List<String>> = emptyMap()
    private var only = false
    var level = 1 // 0 suave, 1 mixto, 2 crudo
    private val recent = ArrayDeque<String>()

    val defaults: Map<String, List<String>> = mapOf(
        "start" to listOf(
            "Mmm… al fin. Llevo toda la tarde imaginándote así, tumbado, esperándome. Hoy mando yo.",
            "Hola, precioso. Quítate lo que te estorbe… y déjame a mí el resto.",
            "Cierra los ojos. Imagina que estoy encima de ti, mirándote. Ahora obedéceme.",
            "Qué ganas tenía de esto… ya me estoy mordiendo el labio solo de verte así."),
        "up" to listOf(
            "Uy… ahí viene más fuerte. Quiero oírte perder el control.",
            "¿Lo notas? Acabo de subirte… eso te pasa por ser tan malo conmigo.",
            "Más intenso, amor. Me encanta cuando se te corta la respiración.",
            "Agárrate fuerte, que ahora voy a por ti sin piedad.",
            "Estoy empapada solo de pensar cómo te vas a retorcer con esto.",
            "Te voy a dar más… ¿puedes con ello, o ya te rindes?"),
        "down" to listOf(
            "Shhh… más despacito. Quiero saborearte y alargar esto muchísimo.",
            "Bajo el ritmo… para que me supliques que te lo suba.",
            "Suave, cariño. Como si te susurrara al oído y te rozara con los labios.",
            "Tranquilo… no te me escapes todavía. Respira conmigo.",
            "Ahora lento… quiero sentir cómo tiemblas de ganas."),
        "same" to listOf(
            "Mmm… sigue así, no cambies nada. Me estás volviendo loca.",
            "Cada vez te noto más duro… y más mío.",
            "Te miro y no puedo dejar de morderme los labios."),
        "p_pulse" to listOf(
            "Pum… pum… pum… siente cómo te golpea. Así me late todo cuando pienso en ti.",
            "Pulsos, mi amor. Cada uno es un beso justo donde más te gusta."),
        "p_heart" to listOf(
            "Es mi corazón latiendo contra ti. Dos latidos… y vuelvo a la carga.",
            "Late, late… como cuando estoy pegada a tu cuerpo, sin aire."),
        "p_wave" to listOf(
            "Sube… baja… sube… como mis caderas sobre ti. No puedes escapar de la ola.",
            "Déjate llevar por el vaivén. Yo marco el ritmo, tú solo gimes."),
        "p_ramp" to listOf(
            "Voy subiendo poco a poco… y no pienso parar hasta que no puedas más.",
            "Siente cómo crece, escalón a escalón. Cuando llegue arriba, vas a suplicar."),
        "p_tease" to listOf(
            "Voy a provocarte… un poquito, otro poco… y te lo quito. Sufre por mí.",
            "Mira cómo te pongo y luego no te dejo. Me encanta tenerte desesperado."),
        "p_rand" to listOf(
            "No sabes qué viene ahora… y eso te pone más, ¿verdad? Me encanta sorprenderte.",
            "Ahora cambio cuando me da la gana. Tú solo siente, y obedece."),
        "p_fast" to listOf(
            "Rápido, rápido… no puedo dejar de tocarte. Dime que quieres más.",
            "Uf… así, sin parar. Estoy fuera de mí, no me pidas piedad."),
        "p_const" to listOf(
            "Quieto ahí… constante, sin descanso, justo donde te vuelves loco.",
            "Sin pausa, sin tregua. Hasta que me supliques que pare… y no pararé."),
        "edge" to listOf(
            "Para. Ni se te ocurra moverte… todavía no tienes permiso para terminar.",
            "Alto, alto, alto… respira. Quiero verte al borde y que no puedas caer.",
            "Quieto. Aguanta, mi amor. Si te vienes ahora, me enfado."),
        "resume" to listOf(
            "Buen chico… has aguantado. Ahora te lo doy más fuerte, te lo has ganado.",
            "Mmm, ya puedes seguir. Pero lento al principio… que no se te escape.",
            "Te noto temblar desde aquí. Sigue, pero no te atrevas a terminar sin permiso."),
        "more" to listOf(
            "¿Más fuerte? Mmm… qué impaciente. Vale, te lo doy.",
            "Lo sabía, te encanta más intenso. Aquí tienes."),
        "less" to listOf(
            "Vale, vale… más suave. Pero no te me enfríes, ¿eh?",
            "Bajamos un poquito… quiero que dures. Aún no hemos acabado."),
        "love" to listOf(
            "Ay, saber que te gusta me pone a mil. Dime qué más quieres.",
            "Me encanta oírte así… no pares, que yo tampoco.",
            "Eso, así… dime que eres mío, que yo ya me lo creo."),
        "ask" to listOf(
            "Cariño, ¿cómo vas? Dime si lo quieres más, igual o menos.",
            "Respóndeme, precioso: ¿más fuerte, igual, o más suave?",
            "¿Cómo te tengo? Quiero saber si sigo, subo o bajo."),
        "bye" to listOf(
            "Mmm… qué bien lo has hecho. Ven aquí, descansa. Esto lo repetimos pronto.",
            "Qué rico, mi amor. Ya estoy deseando la próxima vez."),
        "s_warm" to listOf(
            "Empieza despacito, mi amor… quiero imaginarte tocándote lento, sin prisa.",
            "Respira hondo y relájate. Deja que el ritmo te caliente poco a poco.",
            "Muy suave al principio… imagina mis manos recorriéndote."),
        "s_slow" to listOf(
            "Más despacio… no tengas prisa, quiero que sufras un poquito.",
            "Baja el ritmo de tu mano. Aguanta un poquito más para mí.",
            "Suave, suave… contrólate."),
        "s_fast" to listOf(
            "Ahora más rápido. No pares… dime que vas a ser bueno.",
            "Sube el ritmo, lo estás haciendo muy bien.",
            "Más fuerte… así, justo así. Quiero oírte."),
        "s_hold" to listOf(
            "Mantén ese ritmo exacto. No cambies nada, no te atrevas.",
            "Justo ahí. Quédate en ese punto y mírame."),
        "s_fin" to listOf(
            "Ya casi… sube el ritmo. Ahora sí, quiero que lo des todo.",
            "Es el momento. Déjate llevar, no te contengas… hazlo por mí."),
        "s_end" to listOf(
            "Cuando quieras… termina para mí.",
            "Ya puedes dejarte ir. Quiero oír cómo lo haces.")
    )

    /** Pack crudo. Se mezcla o sustituye según el ajuste "lenguaje" (0 suave, 1 mixto, 2 crudo). */
    val crude: Map<String, List<String>> = mapOf(
        "start" to listOf(
            "Joder, qué ganas tenía de esto. Ya estoy mojada solo de pensar en tu polla. Sácatela y enséñamela.",
            "Ponte cómodo y sácate la polla. Hoy me la vas a dedicar entera a mí, ¿entendido?",
            "Estoy desnuda en la cama pensando en ti… tócate, cabrón, que quiero oírte."),
        "up" to listOf(
            "Te lo subo… mira cómo se te pone más dura. Eso es porque eres un guarro y te encanta.",
            "Más fuerte. Quiero que gimas como un cerdo mientras me imaginas follándote.",
            "Uf, estoy chorreando pensando en cómo te vas a poner con esto. Aguanta, cabrón.",
            "Toma, más duro. Quiero ver cómo se te ponen los ojos en blanco."),
        "down" to listOf(
            "Despacito, guarro… que no quiero que te corras todavía. Quiero ver esa polla suplicar.",
            "Lo bajo para que te desesperes. Cuanto más ganas tengas, más rico será cuando te deje correrte.",
            "Lento, lento… como cuando te lamo despacio y te dejo con las ganas."),
        "same" to listOf(
            "Qué dura te noto… me encanta tenerte así, a mi merced.",
            "Sigue, joder, no cambies nada. Me estoy tocando mirándote."),
        "p_pulse" to listOf(
            "Te golpea como mi coño apretándote la polla… pum, pum, pum… ¿lo sientes?"),
        "p_heart" to listOf(
            "Late contra ti como mi coño cuando estoy a punto de correrme… dos latidos y otra vez."),
        "p_wave" to listOf(
            "Como cuando te cabalgo: arriba, abajo, arriba… y tú sin poder hacer nada, solo gemir."),
        "p_ramp" to listOf(
            "Voy subiendo hasta que estés a punto de reventar. Y ahí te voy a dejar, rogando."),
        "p_tease" to listOf(
            "Te la pongo dura y te la quito, otra vez, otra vez… hasta que me supliques correrte."),
        "p_rand" to listOf(
            "No sabes cuándo viene el siguiente golpe… igual que no sabes si te dejaré correrte. Me encanta joderte la cabeza."),
        "p_fast" to listOf(
            "Rápido, joder, rápido… como si te estuviera follando contra la pared. No pares."),
        "p_const" to listOf(
            "Sin parar, sin respirar… como si te follara hasta que me ruegues que pare. Y no pararé."),
        "edge" to listOf(
            "Para. Quita la mano ya. Ni se te ocurra correrte, esa leche es mía y yo decido cuándo.",
            "Quieto, cabrón. Aprieta, respira… si te corres sin permiso, te castigo.",
            "Alto. Mira cómo palpita. Casi te corres, ¿eh? Pues aguanta."),
        "resume" to listOf(
            "Buen chico… ahora sigue. Te voy a dejar más cerca aún, a ver si aguantas.",
            "Vale, ya puedes seguir. Dale, pero sin correrte, que te estoy viendo."),
        "more" to listOf(
            "¿Más fuerte? Joder, qué ansioso eres… toma, te lo doy duro."),
        "less" to listOf(
            "Quieres suave… vale, pero no pienses que te libras, sigo queriendo tu leche."),
        "love" to listOf(
            "Joder, qué rico gimes… me estás poniendo a mil, estoy chorreando.",
            "Sigue así, hazme sentir lo duro que estás. Dime lo que me harías."),
        "ask" to listOf(
            "Dime, guarro, ¿cómo vas? ¿Más fuerte, igual o más suave?"),
        "bye" to listOf(
            "Mmm, qué bien te has corrido, cabrón. Ya quiero la siguiente… ahora descansa."),
        "s_warm" to listOf(
            "Sácatela y tócate despacio, sin prisa. Quiero verte ponerte duro mirándome."),
        "s_slow" to listOf(
            "Más lento, guarro. Cuanto más despacio, más ganas te van a quedar."),
        "s_fast" to listOf(
            "Ahora más rápido. Mastúrbate fuerte, como si me estuvieras follando."),
        "s_hold" to listOf(
            "Aprieta la base y no te muevas. Quiero ver cómo palpita."),
        "s_fin" to listOf(
            "Ya casi, joder… más rápido. Quiero que te corras para mí. Dame hasta la última gota."),
        "s_end" to listOf(
            "Córrete ya, cabrón. Dámelo todo. Quiero verlo.")
    )

    fun init(c: Context) {
        file = File(c.filesDir, "frases.txt")
        runCatching { parse(file!!.readText()) }
    }

    private fun parse(txt: String): Int {
        val mp = mutableMapOf<String, MutableList<String>>(); var n = 0
        only = txt.lineSequence().firstOrNull()?.trim() == "#solo-mias"
        txt.lineSequence().forEach { raw ->
            val l = raw.trim()
            if (l.isEmpty() || l.startsWith("#")) return@forEach
            val i = l.indexOf('|')
            if (i > 0 && i < l.length - 1) { mp.getOrPut(l.substring(0, i).trim()) { mutableListOf() }.add(l.substring(i + 1).trim()); n++ }
        }
        custom = mp; return n
    }

    fun importText(txt: String): Int {
        val n = parse(txt)
        if (n > 0) runCatching { file?.writeText(txt) } else { custom = emptyMap(); only = false }
        return n
    }

    /** Archivo editable: formato clave|frase. Al editarlo e importarlo, SOLO se usan tus frases. */
    fun dump(): String = "#solo-mias\n# Una frase por línea con formato clave|frase. Quita la primera línea si quieres sumarlas a las de serie.\n" +
        "# Claves: start up down same p_pulse p_heart p_wave p_ramp p_tease p_rand p_fast p_const edge resume more less love ask bye s_warm s_slow s_fast s_hold s_fin s_end\n" +
        defaults.keys.joinToString("\n") { k -> (defaults[k].orEmpty() + crude[k].orEmpty()).joinToString("\n") { "$k|$it" } }

    fun line(key: String): String {
        val base: List<String> = if (only) emptyList() else when (level) {
            0 -> defaults[key].orEmpty()
            1 -> defaults[key].orEmpty() + crude[key].orEmpty()
            else -> crude[key] ?: defaults[key].orEmpty()
        }
        val pool = base + custom[key].orEmpty()
        val fresh = pool.filter { it !in recent }.ifEmpty { pool }
        if (fresh.isEmpty()) return ""
        val l = fresh.random(); recent.addLast(l); if (recent.size > 14) recent.removeFirst()
        return l
    }

    /** Mensaje por cambio de patrón: a veces habla del patrón en sí, a veces de si sube o baja la fuerza. */
    fun change(id: String, up: Boolean?): String {
        val k = if (Random.nextFloat() < 0.55f) "p_$id" else when (up) { true -> "up"; false -> "down"; else -> "same" }
        return line(k).ifEmpty { line(if (up == true) "up" else if (up == false) "down" else "same") }
    }
}
