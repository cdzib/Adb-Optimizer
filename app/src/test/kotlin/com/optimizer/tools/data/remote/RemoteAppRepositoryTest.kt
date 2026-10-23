package com.optimizer.tools.data.remote

import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteAppRepositoryTest {
    private class FakeShell(
        private val answer: (String) -> String,
    ) : AdbShell {
        override suspend fun exec(
            command: String,
            timeoutMs: Long,
        ): ShellResult = ShellResult(0, answer(command), "", 1)
    }

    @Test
    fun `lista apps, versionCode, tamano y estado deshabilitado`() =
        runTest {
            val userApk = "/data/app/~~abc==/com.example.app-1/base.apk"
            val systemApk = "/system/priv-app/Settings/Settings.apk"
            val shell =
                FakeShell { command ->
                    when {
                        command.startsWith("pm list packages -3") -> {
                            "package:$userApk=com.example.app versionCode:42"
                        }

                        command.startsWith("pm list packages -s") -> {
                            "package:$systemApk=com.android.settings versionCode:36"
                        }

                        command.startsWith("pm list packages -d") -> {
                            "package:com.example.app"
                        }

                        command.startsWith("stat ") -> {
                            "$userApk|1048576|1700000000\n$systemApk|2097152|1700000001"
                        }

                        else -> {
                            ""
                        }
                    }
                }

            val apps = RemoteAppRepository(shell).list(includeSystem = true)

            assertEquals(2, apps.size)
            val user = apps.first { it.packageName == "com.example.app" }
            assertFalse(user.isSystem)
            assertFalse(user.enabled)
            assertEquals(42L, user.versionCode)
            assertEquals(1_048_576L, user.sizeBytes)
            assertEquals(1_700_000_000_000L, user.updatedAt)

            val system = apps.first { it.packageName == "com.android.settings" }
            assertTrue(system.isSystem)
            assertTrue(system.enabled)
        }

    @Test
    fun `detalle parsea version sdk fechas permisos y estado`() =
        runTest {
            val apk = "/data/app/~~abc==/com.example.app-1/base.apk"
            val dump =
                """
                Package [com.example.app] (abc):
                  codePath=$apk
                  versionCode=42 minSdk=26 targetSdk=35
                  versionName=4.2.0
                  flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]
                  installerPackageName=com.android.vending
                  splits=[base]
                  firstInstallTime=2024-01-02 03:04:05
                  lastUpdateTime=2025-06-07 08:09:10
                  requested permissions:
                    android.permission.INTERNET
                    android.permission.CAMERA
                  install permissions:
                    android.permission.INTERNET: granted=true
                  User 0: ceDataInode=1 deDataInode=2 installed=true hidden=false suspended=false stopped=false notLaunched=false enabled=2 instant=false virtual=false quarantined=false
                """.trimIndent()

            val shell =
                FakeShell { command ->
                    when {
                        command.startsWith("dumpsys package") -> dump
                        command.startsWith("pm path") -> "package:$apk"
                        command.startsWith("stat ") -> "$apk|2048|1700000000"
                        else -> ""
                    }
                }

            val app = RemoteAppRepository(shell).details("com.example.app")!!

            assertEquals("4.2.0", app.versionName)
            assertEquals(42L, app.versionCode)
            assertEquals(26, app.minSdk)
            assertEquals(35, app.targetSdk)
            assertEquals("com.android.vending", app.installerPackage)
            assertFalse(app.enabled)
            assertFalse(app.isSystem)
            assertEquals(2, app.requestedPermissions.size)
            assertTrue(app.requestedPermissions.contains("android.permission.CAMERA"))
        }
}
