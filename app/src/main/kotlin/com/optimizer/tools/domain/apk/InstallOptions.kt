package com.optimizer.tools.domain.apk

import dev.adboptimizer.core.shell.AdbCommandRules

/**
 * Opciones de `pm install` que el usuario puede elegir antes de instalar.
 *
 * Siempre se reemplaza la app existente (`-r`); el resto son opt-in porque cambian el
 * resultado: conceder permisos, aceptar un downgrade o instalar un APK marcado como test.
 */
data class InstallOptions(
    val grantPermissions: Boolean = false,
    val allowDowngrade: Boolean = false,
    val allowTest: Boolean = false,
) {
    /** Argumentos ya validados por la regla cerrada, listos para un comando de shell. */
    fun flags(): List<String> {
        val raw =
            buildList {
                add("-r")
                if (grantPermissions) add("-g")
                if (allowDowngrade) add("-d")
                if (allowTest) add("-t")
            }.joinToString(" ")
        return AdbCommandRules.installFlags(raw) ?: listOf("-r")
    }
}
