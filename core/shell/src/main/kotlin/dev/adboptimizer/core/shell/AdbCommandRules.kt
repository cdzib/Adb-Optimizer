package dev.adboptimizer.core.shell

/**
 * Reglas puras para los nombres y opciones que terminan en un comando de `adb shell`.
 *
 * La regla es cerrada: lo que no esta en la lista se rechaza con `null` en lugar de repararse
 * o escaparse a medias. Los comandos se construyen con [ShellQuote] cuando el valor puede
 * contener caracteres especiales.
 */
object AdbCommandRules {
    private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
    private val COMPONENT = Regex("[A-Za-z0-9._$]+")
    private val APP_OP = Regex("[A-Z][A-Z0-9_]+")
    private val APP_OP_MODE = Regex("allow|ignore|deny|foreground|default")
    private val DIGITS = Regex("[0-9]{1,9}")
    private val URI = Regex("[A-Za-z][A-Za-z0-9+.\\-]*:[\\x21-\\x7e]{0,2000}")
    private val BUCKET_CODE = Regex("[0-9]{1,3}")

    private const val MAX_PACKAGE_LENGTH = 255
    private const val MAX_FLAGS = 40

    private val BOOLEAN_INSTALL_FLAGS =
        setOf(
            "-r",
            "-g",
            "-d",
            "-t",
            "--bypass-low-target-sdk-block",
            "--update-ownership",
        )

    private val VALUE_INSTALL_FLAGS =
        setOf("-i", "--user", "--install-reason", "--package-source", "--originating-uri")

    private val COMPILE_MODES =
        setOf(
            "assume-verified",
            "extract",
            "verify",
            "quicken",
            "space-profile",
            "space",
            "speed-profile",
            "speed",
            "everything-profile",
            "everything",
        )

    /** Los buckets que acepta `am set-standby-bucket`, del menos al mas restringido. */
    val STANDBY_BUCKETS = listOf("active", "working_set", "frequent", "rare", "restricted")

    /** Lo que Android imprime para cada bucket; `get-standby-bucket` puede devolver el numero o el nombre. */
    private val STANDBY_BUCKET_NAMES =
        mapOf(
            5 to "exempted",
            10 to "active",
            20 to "working_set",
            30 to "frequent",
            40 to "rare",
            45 to "restricted",
            50 to "never",
        )

    private val STANDBY_BUCKET_VALUES = STANDBY_BUCKET_NAMES.values.toSet()

    /** Un nombre de paquete de Android: segmentos alfanumericos separados por punto. */
    fun isPackageName(value: String): Boolean = value.length <= MAX_PACKAGE_LENGTH && PACKAGE_NAME.matches(value)

    /**
     * Las opciones de `pm install` como argumentos, o `null` si alguna no esta permitida.
     * Un texto vacio significa el reemplazo por defecto.
     */
    fun installFlags(raw: String?): List<String>? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return listOf("-r")

        val words = text.split(Regex("\\s+"))
        if (words.size > MAX_FLAGS) return null

        val flags = mutableListOf<String>()
        var index = 0
        while (index < words.size) {
            val flag = words[index]
            if (flag in BOOLEAN_INSTALL_FLAGS) {
                flags += flag
                index++
                continue
            }
            if (flag !in VALUE_INSTALL_FLAGS) return null

            val value = words.getOrNull(index + 1) ?: return null
            if (!validInstallValue(flag, value)) return null
            flags += flag
            flags += value
            index += 2
        }
        return flags
    }

    private fun validInstallValue(
        flag: String,
        value: String,
    ): Boolean =
        when (flag) {
            "--user" -> value == "all" || value == "current" || DIGITS.matches(value)
            "--install-reason", "--package-source" -> DIGITS.matches(value)
            "-i" -> isPackageName(value)
            "--originating-uri" -> URI.matches(value)
            else -> false
        }

    /** `pm compile` de un paquete, o `null` si el paquete o el modo no son validos. */
    fun compileCommand(
        packageName: String,
        mode: String?,
        force: Boolean,
    ): String? {
        if (!isPackageName(packageName)) return null
        if (mode == "reset") return "pm compile --reset $packageName"

        val compileMode = mode?.ifEmpty { null } ?: "speed"
        if (compileMode !in COMPILE_MODES) return null
        return "pm compile -m $compileMode${if (force) " -f" else ""} $packageName"
    }

    /**
     * Habilita o deshabilita un componente con la forma `paquete/clase`. Una clase relativa
     * conserva su punto inicial (`paquete/.MainActivity`), como la acepta `pm`. Se cita para que
     * el `$` de una clase interna llegue a `pm` sin expandirse.
     */
    fun componentCommand(
        packageName: String,
        component: String,
        enable: Boolean,
    ): String? {
        if (!isPackageName(packageName) || component.isEmpty() || !COMPONENT.matches(component)) return null

        val action = if (enable) "enable" else "disable"
        return "pm $action ${ShellQuote.of("$packageName/$component")}"
    }

    /** Un nombre de `appops set` valido: mayusculas, digitos y guion bajo. */
    fun isAppOp(value: String): Boolean = APP_OP.matches(value)

    /** Un modo de `appops set` valido. */
    fun isAppOpMode(value: String): Boolean = APP_OP_MODE.matches(value)

    /**
     * `appops set <paquete> <op> <modo>` con el nombre y el modo validados, o `null` si algo
     * no esta permitido. Se usa para restringir comportamiento sin tocar el APK de la app.
     */
    fun appOpsCommand(
        packageName: String,
        op: String,
        mode: String,
    ): String? {
        if (!isPackageName(packageName) || !isAppOp(op) || !isAppOpMode(mode)) return null
        return "appops set $packageName $op $mode"
    }

    /**
     * `am get-standby-bucket` cuando [bucket] es `null`, o `am set-standby-bucket` con uno de los
     * buckets permitidos. `null` cuando el paquete o el bucket no son validos; `exempted`,
     * `never` y los valores numericos no se pueden fijar por esta via.
     */
    fun standbyBucketCommand(
        packageName: String,
        bucket: String?,
    ): String? {
        if (!isPackageName(packageName)) return null
        if (bucket == null) return "am get-standby-bucket $packageName"
        if (bucket !in STANDBY_BUCKETS) return null
        return "am set-standby-bucket $packageName $bucket"
    }

    /** Las tres tablas de `settings` que Android expone por shell. */
    val SETTINGS_NAMESPACES = listOf("global", "secure", "system")

    /** Una de las tres tablas que acepta `settings`. */
    fun isSettingsNamespace(namespace: String): Boolean = namespace in SETTINGS_NAMESPACES

    private const val MAX_SETTING_KEY = 256
    private const val MAX_SETTING_VALUE = 20_000

    /**
     * Una clave de `settings` utilizable: sin NUL, sin saltos de linea, sin guion inicial
     * (que `settings` leeria como opcion) y con un tamano razonable.
     */
    fun isSettingsKey(key: String): Boolean =
        key.isNotEmpty() &&
            key.length <= MAX_SETTING_KEY &&
            !key.startsWith("-") &&
            key.none { it == '\u0000' || it == '\n' || it == '\r' }

    /** Un valor que cabe en una peticion de adb sin romper el marco de 64 KiB. */
    fun isSettingsValue(value: String): Boolean = value.length <= MAX_SETTING_VALUE && !value.contains('\u0000')

    /** `settings get <tabla> <clave>`, con la clave citada, o `null` si algo no es valido. */
    fun settingsGetCommand(
        namespace: String,
        key: String,
    ): String? {
        if (!isSettingsNamespace(namespace) || !isSettingsKey(key)) return null
        return "settings get $namespace ${ShellQuote.of(key)}"
    }

    /** `settings put <tabla> <clave> <valor>`, o `null` si algo no es valido. */
    fun settingsPutCommand(
        namespace: String,
        key: String,
        value: String,
    ): String? {
        if (!isSettingsNamespace(namespace) || !isSettingsKey(key) || !isSettingsValue(value)) return null
        return "settings put $namespace ${ShellQuote.of(key)} ${ShellQuote.of(value)}"
    }

    /** El bucket que imprimio el dispositivo, en numero o en nombre, o `null` si no es reconocible. */
    fun parseStandbyBucket(output: String?): String? {
        val text = output?.trim()?.lowercase().orEmpty()
        if (text.isEmpty() || text.length > 40) return null
        if (text in STANDBY_BUCKET_VALUES) return text
        if (!BUCKET_CODE.matches(text)) return null
        return STANDBY_BUCKET_NAMES[text.toInt()]
    }
}
