package com.optimizer.tools.domain.dpc

/**
 * Reglas puras de aprovisionamiento DPC. Portadas del servicio de escritorio
 * (desktop/src/main/apps/dpc-service.ts) y cubiertas por pruebas.
 */
object DpcParsers {
    fun parseOwners(raw: String): List<String> =
        raw
            .lineSequence()
            .map { it.trim() }
            .filter { it.contains("admin=") }
            .mapNotNull { line ->
                line
                    .substringAfter("admin=")
                    .substringBefore(',')
                    .trim()
                    .takeIf { it.isNotBlank() }
            }.distinct()
            .toList()

    private val userPattern = Regex("""UserInfo\{(\d+):([^:}]*)""")

    fun parseUsers(raw: String): List<DpcUser> =
        raw
            .lineSequence()
            .mapNotNull { userPattern.find(it.trim()) }
            .map { match ->
                val id = match.groupValues[1].toIntOrNull() ?: 0
                val name = match.groupValues[2].trim().ifBlank { "usuario $id" }
                DpcUser(id, name)
            }.toList()

    private val componentPattern = Regex("""^([A-Za-z0-9_.]+/[A-Za-z0-9_.$]+)$""")

    fun parseReceivers(raw: String): List<DpcReceiver> =
        raw
            .lineSequence()
            .map { it.trim() }
            .mapNotNull { componentPattern.matchEntire(it)?.groupValues?.get(1) }
            .distinct()
            .map { component -> DpcReceiver(component, component.substringBefore('/')) }
            .toList()

    fun countAccounts(raw: String): Int =
        raw
            .lineSequence()
            .count { it.contains("Account {") && !it.contains("Account {name=null") }
}

object DpcPreflightEvaluator {
    fun evaluate(
        sdk: Int,
        owners: List<String>,
        users: List<DpcUser>,
        accounts: Int,
        mode: DpcMode,
    ): Pair<List<DpcCheck>, Boolean> {
        val checks = mutableListOf<DpcCheck>()
        val extraUsers = users.filter { it.id != 0 }

        checks +=
            DpcCheck(
                id = "sdk",
                label = "Versión de Android",
                status = if (sdk >= 24) DpcCheckStatus.OK else DpcCheckStatus.WARN,
                detail = "SDK $sdk",
            )
        checks +=
            DpcCheck(
                id = "owner",
                label = "Sin propietario previo",
                status = if (owners.isEmpty()) DpcCheckStatus.OK else DpcCheckStatus.FAIL,
                detail =
                    if (owners.isEmpty()) {
                        "ningún admin es Device/Profile Owner"
                    } else {
                        "ya existe: ${owners.joinToString()}"
                    },
            )

        if (mode == DpcMode.DEVICE) {
            checks +=
                DpcCheck(
                    id = "users",
                    label = "Un solo usuario",
                    status = if (extraUsers.isEmpty()) DpcCheckStatus.OK else DpcCheckStatus.FAIL,
                    detail =
                        if (extraUsers.isEmpty()) {
                            "solo el usuario 0"
                        } else {
                            "${users.size} usuarios: ${extraUsers.joinToString { "${it.id}:${it.name}" }}"
                        },
                )
            checks +=
                DpcCheck(
                    id = "accounts",
                    label = "Sin cuentas configuradas",
                    status = if (accounts == 0) DpcCheckStatus.OK else DpcCheckStatus.FAIL,
                    detail =
                        if (accounts == 0) {
                            "dispositivo sin cuentas"
                        } else {
                            "$accounts cuentas; el aprovisionamiento exige un dispositivo recién restaurado"
                        },
                )
        } else {
            checks +=
                DpcCheck(
                    id = "users",
                    label = "Perfil de trabajo disponible",
                    status = if (extraUsers.isNotEmpty()) DpcCheckStatus.OK else DpcCheckStatus.FAIL,
                    detail =
                        if (extraUsers.isNotEmpty()) {
                            "usuarios: ${extraUsers.joinToString { "${it.id}:${it.name}" }}"
                        } else {
                            "crea un perfil de trabajo antes de aprovisionar (el usuario 0 no puede ser profile owner)"
                        },
                )
            checks +=
                DpcCheck(
                    id = "accounts",
                    label = "Cuentas en el perfil",
                    status = DpcCheckStatus.OK,
                    detail = "$accounts cuentas en el dispositivo (permitidas en modo perfil)",
                )
        }

        val ready = checks.none { it.status == DpcCheckStatus.FAIL }
        return checks to ready
    }
}
