package com.optimizer.tools.feature.inspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInspectorParserTest {
    private val dump =
        """
        Activity Resolver Table:
          Schemes:
              http:
                7548306 com.example.player/com.example.player.MainActivity filter 2236ff4
                  Action: "android.intent.action.VIEW"

        Receiver Resolver Table:
          Non-Data Actions:
              android.accounts.action.ACCOUNT_REMOVED:
                feb331d com.example.player/com.example.player.RestartReceiver filter 625c992

        Service Resolver Table:
          Non-Data Actions:
              com.example.player.Sync:
                2eaebf com.example.player/com.example.player.SyncService filter 3d8c98c

        Package [com.example.player] (abc):
            signatures=PackageSignatures{fff1799 version:3, signatures:[71c782a3], past signatures:[]}
            requested permissions:
              android.permission.INTERNET
              android.permission.CAMERA
            install permissions:
              android.permission.INTERNET: granted=true
            User 0: ceDataInode=1 installed=true
              runtime permissions:
                android.permission.CAMERA: granted=false, flags=[ USER_SENSITIVE_WHILE_INVISIBLE ]
        """.trimIndent()

    @Test
    fun `separa permisos declarados, concedidos y denegados`() {
        val permissions = AppInspectorParser.permissions(dump)

        assertEquals(
            listOf(
                AppPermission("android.permission.INTERNET", true),
                AppPermission("android.permission.CAMERA", false),
            ),
            permissions,
        )
    }

    @Test
    fun `un permiso solo declarado queda sin estado`() {
        val dump = "    requested permissions:\n      android.permission.BLUETOOTH\n"

        assertNull(AppInspectorParser.permissions(dump).single().granted)
    }

    @Test
    fun `clasifica los componentes por tabla y solo del paquete pedido`() {
        val components = AppInspectorParser.components(dump, "com.example.player")

        assertEquals(3, components.size)
        assertEquals(ComponentKind.ACTIVITY, components[0].kind)
        assertEquals("com.example.player.MainActivity", components[0].className)
        assertEquals(ComponentKind.RECEIVER, components[1].kind)
        assertEquals(ComponentKind.SERVICE, components[2].kind)
        assertTrue(AppInspectorParser.components(dump, "com.otro").isEmpty())
    }

    @Test
    fun `lee appops y descarta lo que no tiene modo`() {
        val ops = AppInspectorParser.appOps("Uid mode: COARSE_LOCATION: ignore\nCAMERA: foreground\nNo operations.\n")

        assertEquals(listOf(AppOp("CAMERA", "foreground")), ops)
    }

    @Test
    fun `lee el sha256 que imprime sha256sum`() {
        val output = "CF2B3EE849C59E3F043858ADE8A19C7EDC39527EA6F6EE9E0C0E45AF618F660F  /product/app/base.apk\n"

        assertEquals("cf2b3ee849c59e3f043858ade8a19c7edc39527ea6f6ee9e0c0e45af618f660f", AppInspectorParser.sha256(output))
        assertNull(AppInspectorParser.sha256("sha256sum: no such file"))
        assertNull(AppInspectorParser.sha256(null))
    }

    @Test
    fun `lee la version y el hash de firma`() {
        assertEquals("v3 · 71c782a3", AppInspectorParser.signature(dump))
        assertNull(AppInspectorParser.signature("sin firmas"))
    }
}
