package dev.adboptimizer.core.shell

/**
 * Citado POSIX para los argumentos que viajan dentro de un comando de `adb shell`.
 *
 * Todo va entre comillas simples, asi que `$`, backticks, comillas dobles, `;` y saltos de
 * linea llegan al dispositivo como texto y nunca como codigo de shell. Una comilla simple se
 * cierra, se escapa y se vuelve a abrir (`'it'\''s'`).
 */
object ShellQuote {
    /** Cita [value]. Un NUL no puede viajar por el protocolo del shell, asi que se rechaza. */
    fun of(value: String): String {
        require(!value.contains('\u0000')) { "El texto contiene un NUL y no puede viajar por el shell." }
        return "'" + value.replace("'", "'\\''") + "'"
    }

    /** Cita cada argumento de [args] y los une con un espacio. */
    fun join(args: List<String>): String = args.joinToString(separator = " ") { of(it) }
}
