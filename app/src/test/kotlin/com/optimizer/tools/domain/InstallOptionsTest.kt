package com.optimizer.tools.domain

import com.optimizer.tools.domain.apk.InstallOptions
import dev.adboptimizer.core.shell.AdbCommandRules
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallOptionsTest {
    @Test
    fun `por defecto solo reemplaza la app`() {
        assertEquals(listOf("-r"), InstallOptions().flags())
    }

    @Test
    fun `agrega solo las opciones elegidas`() {
        assertEquals(listOf("-r", "-g"), InstallOptions(grantPermissions = true).flags())
        assertEquals(
            listOf("-r", "-g", "-d", "-t"),
            InstallOptions(grantPermissions = true, allowDowngrade = true, allowTest = true).flags(),
        )
    }

    @Test
    fun `las opciones siempre pasan la regla cerrada`() {
        val flags = InstallOptions(grantPermissions = true, allowDowngrade = true, allowTest = true).flags()

        assertEquals(flags, AdbCommandRules.installFlags(flags.joinToString(" ")))
    }
}
