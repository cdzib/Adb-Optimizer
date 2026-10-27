package com.optimizer.tools.feature.inspector

enum class ComponentKind { ACTIVITY, SERVICE, RECEIVER, PROVIDER }

/** Permiso declarado por la app; `granted` es `null` cuando el volcado no dice si se concedio. */
data class AppPermission(
    val name: String,
    val granted: Boolean?,
)

/** Componente declarado en el manifiesto de la app. */
data class AppComponent(
    val kind: ComponentKind,
    val className: String,
)

/** Operacion de AppOps con el modo efectivo que reporta el dispositivo. */
data class AppOp(
    val name: String,
    val mode: String,
)

data class AppInspection(
    val permissions: List<AppPermission> = emptyList(),
    val appOps: List<AppOp> = emptyList(),
    val components: List<AppComponent> = emptyList(),
    val signature: String? = null,
    /** SHA-256 del APK base, leido con sha256sum en el dispositivo. */
    val apkSha256: String? = null,
)

/**
 * Reglas puras sobre la salida de `dumpsys package` y `appops get`.
 *
 * Los formatos son de AOSP y cambian poco entre versiones; lo que no encaja se descarta en
 * vez de mostrarse mal. Verificado sobre `dumpsys package com.google.android.youtube.tvmusic`
 * en Android 16 (emulador).
 */
object AppInspectorParser {
    private const val MAX_ITEMS = 200

    private val OPS_LINE = Regex("""^([A-Z][A-Z0-9_]+):\s*(\S+)$""")
    private val COMPONENT_LINE = Regex("""^\s+\w+\s+([A-Za-z0-9_.$]+)/([A-Za-z0-9_.$]+)\s+filter\b""")
    private val SIGNATURE = Regex("""signatures=PackageSignatures\{\w+\s+version:(\d+),\s+signatures:\[([^\]]*)]""")

    fun permissions(dump: String): List<AppPermission> {
        val found = linkedMapOf<String, Boolean?>()
        var section: Section = Section.NONE
        dump.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.endsWith("requested permissions:") -> {
                    section = Section.REQUESTED
                }

                line.endsWith("install permissions:") || line.endsWith("runtime permissions:") -> {
                    section = Section.GRANTED
                }

                line.startsWith("User ") || line.startsWith("Queries:") || line.startsWith("Packages:") -> {
                    section = Section.NONE
                }

                section == Section.REQUESTED -> {
                    val name = line.trim()
                    if (name.matches(PERMISSION)) found[name] = null
                }

                section == Section.GRANTED -> {
                    val separator = line.lastIndexOf(": granted=")
                    if (separator > 0) {
                        val name = line.substring(0, separator).trim()
                        if (name.matches(PERMISSION)) {
                            val granted = line.substring(separator + ": granted=".length).startsWith("true")
                            found[name] = (found[name] ?: false) || granted
                        }
                    }
                }
            }
        }
        return found.map { AppPermission(it.key, it.value) }.take(MAX_ITEMS)
    }

    fun components(
        dump: String,
        packageName: String,
    ): List<AppComponent> {
        val found = linkedSetOf<AppComponent>()
        var kind: ComponentKind? = null
        dump.lineSequence().forEach { line ->
            kind =
                when {
                    line.startsWith("Activity Resolver Table:") -> ComponentKind.ACTIVITY
                    line.startsWith("Service Resolver Table:") -> ComponentKind.SERVICE
                    line.startsWith("Receiver Resolver Table:") -> ComponentKind.RECEIVER
                    line.startsWith("Provider Resolver Table:") -> ComponentKind.PROVIDER
                    else -> kind
                }
            if (kind == null) return@forEach
            val match = COMPONENT_LINE.find(line) ?: return@forEach
            if (match.groupValues[1] != packageName) return@forEach
            found += AppComponent(kind!!, match.groupValues[2])
        }
        return found.take(MAX_ITEMS)
    }

    fun appOps(raw: String): List<AppOp> {
        val found = mutableListOf<AppOp>()
        raw.lineSequence().forEach { line ->
            val match = OPS_LINE.find(line.trim()) ?: return@forEach
            found += AppOp(match.groupValues[1], match.groupValues[2])
        }
        return found.distinctBy { it.name }.take(MAX_ITEMS)
    }

    /** Primera linea de `sha256sum <apk>`: 64 hex en minusculas. */
    fun sha256(raw: String?): String? {
        val match = SHA256_LINE.find(raw.orEmpty()) ?: return null
        return match.groupValues[1].lowercase()
    }

    /** `version:3 · 71c782a3`: el hash corto de firma que imprime el volcado, no un SHA-256. */
    fun signature(dump: String): String? {
        val match = SIGNATURE.find(dump) ?: return null
        val hashes = match.groupValues[2].trim()
        if (hashes.isEmpty()) return null
        return "v${match.groupValues[1]} · $hashes"
    }

    private val PERMISSION = Regex("""[A-Za-z][A-Za-z0-9_.]*""")
    private val SHA256_LINE = Regex("""^\s*([0-9a-fA-F]{64})\s""", RegexOption.MULTILINE)

    private enum class Section { NONE, REQUESTED, GRANTED }
}
