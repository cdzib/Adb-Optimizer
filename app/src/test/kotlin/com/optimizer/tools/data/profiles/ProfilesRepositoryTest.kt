package com.optimizer.tools.data.profiles

import com.optimizer.tools.domain.profile.AppProfile
import com.optimizer.tools.domain.profile.ProfileApp
import com.optimizer.tools.domain.profile.ProfileAppState
import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProfilesRepositoryTest {
    private fun profile(vararg apps: ProfileApp) =
        AppProfile(
            id = "p1",
            name = "Limpio",
            createdAt = 1L,
            apps = apps.toList(),
        )

    private fun repository(
        shell: FakeShell,
        file: File,
    ) = ProfilesRepository(ProfileStore(file), { shell })

    @Test
    fun `el estado combina instaladas deshabilitadas y conocidas`() =
        runTest {
            val shell =
                FakeShell().apply {
                    installed += setOf("com.a", "com.b")
                    disabled += "com.b"
                    known += setOf("com.a", "com.b", "com.c")
                }
            val file = tempFile()

            val states = repository(shell, file).states()

            assertEquals(ProfileAppState.ENABLED, states["com.a"])
            assertEquals(ProfileAppState.DISABLED, states["com.b"])
            assertEquals(ProfileAppState.UNINSTALLED, states["com.c"])
            file.delete()
        }

    @Test
    fun `aplicar un perfil ejecuta y confirma cada paso`() =
        runTest {
            val shell =
                FakeShell().apply {
                    installed += setOf("com.a")
                    disabled += setOf()
                    known += setOf("com.a", "com.c")
                }
            val file = tempFile()
            val repo = repository(shell, file)
            val target =
                profile(
                    ProfileApp("com.a", "A", ProfileAppState.DISABLED),
                    ProfileApp("com.c", "C", ProfileAppState.ENABLED),
                )

            val run = repo.apply(target)

            assertTrue(run.failures.isEmpty())
            assertEquals(2, run.changes.size)
            assertEquals(ProfileAppState.ENABLED, run.changes.first { it.packageName == "com.a" }.from)
            assertTrue(shell.disabled.contains("com.a"))
            assertTrue(shell.installed.contains("com.c"))
            assertFalse(shell.disabled.contains("com.c"))
            assertEquals(2, repo.lastRun()?.changes?.size)
            file.delete()
        }

    @Test
    fun `una app que el sistema rechaza se anota y no detiene el resto`() =
        runTest {
            val shell =
                FakeShell().apply {
                    installed += setOf("com.a", "com.b")
                    known += setOf("com.a", "com.b")
                    rejected = "com.a"
                }
            val file = tempFile()
            val repo = repository(shell, file)
            val target =
                profile(
                    ProfileApp("com.a", "A", ProfileAppState.DISABLED),
                    ProfileApp("com.b", "B", ProfileAppState.DISABLED),
                )

            val run = repo.apply(target)

            assertEquals(listOf("com.a"), run.failures)
            assertEquals(listOf("com.b"), run.changes.map { it.packageName })
            assertTrue(shell.disabled.contains("com.b"))
            file.delete()
        }

    @Test
    fun `deshacer devuelve las apps al estado anterior`() =
        runTest {
            val shell =
                FakeShell().apply {
                    installed += setOf("com.a")
                    known += setOf("com.a", "com.c")
                }
            val file = tempFile()
            val repo = repository(shell, file)
            val target =
                profile(
                    ProfileApp("com.a", "A", ProfileAppState.DISABLED),
                    ProfileApp("com.c", "C", ProfileAppState.ENABLED),
                )
            val run = repo.apply(target)

            val restored = repo.undo(run.changes)

            assertEquals(2, restored)
            assertFalse(shell.disabled.contains("com.a"))
            assertFalse(shell.installed.contains("com.c"))
            file.delete()
        }

    @Test
    fun `sin shell el repositorio explica que falta la conexion`() =
        runTest {
            val file = tempFile()
            val repo = ProfilesRepository(ProfileStore(file), { null })

            val error = runCatching { repo.states() }.exceptionOrNull()

            assertTrue(error?.message.orEmpty().contains("Conecta un dispositivo"))
            file.delete()
        }

    private fun tempFile(): File = File.createTempFile("profiles", ".json").apply { delete() }

    private class FakeShell : AdbShell {
        val installed = linkedSetOf<String>()
        val disabled = linkedSetOf<String>()
        val known = linkedSetOf<String>()
        val commands = mutableListOf<String>()
        var rejected: String? = null

        override suspend fun exec(
            command: String,
            timeoutMs: Long,
        ): ShellResult {
            commands += command
            val packageName = Regex("'([^']+)'").find(command)?.groupValues?.get(1)
            val response =
                when {
                    command == "pm list packages" -> {
                        list(installed)
                    }

                    command == "pm list packages -d" -> {
                        list(disabled)
                    }

                    command == "pm list packages -u" -> {
                        list(known + installed)
                    }

                    command == "pm list packages -e" -> {
                        list(installed - disabled)
                    }

                    command.startsWith("pm list packages -e ") -> {
                        list(if (packageName in installed && packageName !in disabled) setOfNotNull(packageName) else emptySet())
                    }

                    command.startsWith("pm list packages -d ") -> {
                        list(if (packageName in disabled) setOfNotNull(packageName) else emptySet())
                    }

                    command.startsWith("pm list packages ") -> {
                        list(if (packageName in installed) setOfNotNull(packageName) else emptySet())
                    }

                    command.startsWith("pm disable-user") -> {
                        changeDisabled(packageName, disabled = true)
                    }

                    command.startsWith("pm enable") -> {
                        changeDisabled(packageName, disabled = false)
                    }

                    command.startsWith("pm uninstall") -> {
                        uninstall(packageName)
                    }

                    command.startsWith("pm install-existing") -> {
                        reinstall(packageName)
                    }

                    else -> {
                        failure("comando no soportado")
                    }
                }
            return response
        }

        private fun list(packages: Set<String>): ShellResult = ShellResult(0, packages.joinToString("\n") { "package:$it" }, "", 1)

        private fun changeDisabled(
            packageName: String?,
            disabled: Boolean,
        ): ShellResult {
            if (packageName == null) return failure("paquete ausente")
            if (packageName == rejected) return failure("Failure [rechazado]")
            if (disabled) this.disabled += packageName else this.disabled -= packageName
            installed += packageName
            return ShellResult(0, "Package $packageName new state: ${if (disabled) "disabled-user" else "enabled"}", "", 1)
        }

        private fun uninstall(packageName: String?): ShellResult {
            if (packageName == null) return failure("paquete ausente")
            if (packageName == rejected) return failure("Failure [rechazado]")
            installed -= packageName
            disabled -= packageName
            return ShellResult(0, "Success", "", 1)
        }

        private fun reinstall(packageName: String?): ShellResult {
            if (packageName == null) return failure("paquete ausente")
            if (packageName == rejected) return failure("Failure [rechazado]")
            installed += packageName
            disabled -= packageName
            return ShellResult(0, "Package $packageName installed for user: 0", "", 1)
        }

        private fun failure(message: String): ShellResult = ShellResult(1, message, "", 1)
    }
}
