package com.calma.launcher.data

/** La hora en palabras y las frases del día. */
object Words {

    private val esUnits = listOf(
        "cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
        "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho", "diecinueve",
        "veinte", "veintiuno", "veintidós", "veintitrés", "veinticuatro", "veinticinco", "veintiséis",
        "veintisiete", "veintiocho", "veintinueve",
    )
    private val esTens = mapOf(3 to "treinta", 4 to "cuarenta", 5 to "cincuenta")

    private val enUnits = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val enTens = mapOf(2 to "twenty", 3 to "thirty", 4 to "forty", 5 to "fifty")

    private fun es(n: Int): String {
        if (n < 30) return esUnits[n]
        val t = esTens[n / 10] ?: return n.toString()
        return if (n % 10 == 0) t else "$t y ${esUnits[n % 10]}"
    }

    private fun en(n: Int): String {
        if (n < 20) return enUnits[n]
        val t = enTens[n / 10] ?: return n.toString()
        return if (n % 10 == 0) t else "$t-${enUnits[n % 10]}"
    }

    /** Tres renglones: "son las" / "seis" / "y doce", más el momento del día. */
    fun clock(hour24: Int, minute: Int, lang: String): List<String> {
        val h12 = (hour24 + 11) % 12 + 1
        return if (lang == "es") {
            val first = if (h12 == 1) "es la" else "son las"
            val hourWord = if (h12 == 1) "una" else es(h12)
            val min = when (minute) {
                0 -> "en punto"
                15 -> "y cuarto"
                30 -> "y media"
                else -> "y ${es(minute)}"
            }
            val part = when (hour24) {
                in 0..5 -> "de la madrugada"
                in 6..11 -> "de la mañana"
                in 12..19 -> "de la tarde"
                else -> "de la noche"
            }
            listOf(first, hourWord, min, part)
        } else {
            val min = when {
                minute == 0 -> "o'clock"
                minute < 10 -> "oh ${en(minute)}"
                else -> en(minute)
            }
            val part = when (hour24) {
                in 0..5 -> "at night"
                in 6..11 -> "in the morning"
                in 12..17 -> "in the afternoon"
                else -> "in the evening"
            }
            listOf("it's", en(h12), min, part)
        }
    }

    private val quotesEs = listOf(
        "Hoy también cuenta.",
        "Poco a poco se llega lejos.",
        "Respira. Vas bien.",
        "Haz una cosa a la vez.",
        "Lo simple también es bonito.",
        "Que el día te trate bonito.",
        "Descansar también es avanzar.",
        "Empieza donde estás.",
        "Hoy es buen día para intentarlo.",
        "Menos ruido, más calma.",
        "Tu ritmo está bien.",
        "Lo mejor está por llegar.",
        "Cuida tu energía.",
        "Un paso más que ayer.",
        "Disfruta lo pequeño.",
        "Sé amable contigo.",
        "La calma también es fuerza.",
        "Termina lo que empezaste.",
        "Sonríe, aunque sea un poquito.",
        "Hoy, sin prisa.",
        "Lo que siembras hoy, florece después.",
        "Haz espacio para lo que importa.",
    )
    private val quotesEn = listOf(
        "Today counts too.",
        "Little by little, you get far.",
        "Breathe. You're doing fine.",
        "One thing at a time.",
        "Simple is beautiful.",
        "Rest is progress too.",
        "Start where you are.",
        "A good day to try.",
        "Less noise, more calm.",
        "Your pace is fine.",
        "The best is yet to come.",
        "Protect your energy.",
        "One step further than yesterday.",
        "Enjoy the little things.",
        "Be kind to yourself.",
        "Calm is strength too.",
        "Finish what you started.",
        "Smile, even just a little.",
        "No rush today.",
        "Make room for what matters.",
    )

    fun quote(dayOfYear: Int, lang: String): String {
        val list = if (lang == "es") quotesEs else quotesEn
        return list[Math.floorMod(dayOfYear, list.size)]
    }
}
