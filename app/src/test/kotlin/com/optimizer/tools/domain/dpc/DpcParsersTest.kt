package com.optimizer.tools.domain.dpc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DpcParsersTest {
    private val ownersRaw =
        "User 150: admin=com.samsung.knox.securefolder/.containeragent.detector.KnoxDeviceAdminReceiver,ManagedProfileOwner(parentUserId=0)\n"

    private val usersRaw =
        """
        Users:
        	UserInfo{0:Cesar Dzib:4c13} running
        	UserInfo{95:DUAL_APP:20001010} running
        	UserInfo{150:Secure Folder:61010} running
        """.trimIndent()

    private val receiversRaw =
        """
        11 receivers found:
          Receiver #0:
            com.google.android.gms/.mdm.receivers.MdmDeviceAdminReceiver
          Receiver #1:
            com.microsoft.appmanager/com.microsoft.workaccount.workplacejoin.core.WPJAdminReceiver
        """.trimIndent()

    @Test
    fun `extrae el owner existente`() {
        assertEquals(
            listOf("com.samsung.knox.securefolder/.containeragent.detector.KnoxDeviceAdminReceiver"),
            DpcParsers.parseOwners(ownersRaw),
        )
    }

    @Test
    fun `extrae usuarios con id y nombre`() {
        assertEquals(
            listOf(DpcUser(0, "Cesar Dzib"), DpcUser(95, "DUAL_APP"), DpcUser(150, "Secure Folder")),
            DpcParsers.parseUsers(usersRaw),
        )
    }

    @Test
    fun `extrae receivers sin duplicados`() {
        val raw = "$receiversRaw\n  Receiver #2:\n    com.google.android.gms/.mdm.receivers.MdmDeviceAdminReceiver\n"
        assertEquals(
            listOf(
                DpcReceiver("com.google.android.gms/.mdm.receivers.MdmDeviceAdminReceiver", "com.google.android.gms"),
                DpcReceiver(
                    "com.microsoft.appmanager/com.microsoft.workaccount.workplacejoin.core.WPJAdminReceiver",
                    "com.microsoft.appmanager",
                ),
            ),
            DpcParsers.parseReceivers(raw),
        )
    }

    @Test
    fun `cuenta solo cuentas reales`() {
        val raw = "Account {name=null, type=null}\nAccount {name=a@b.com, type=com.google}\nAccount {name=c@d.com, type=com.google}\n"
        assertEquals(2, DpcParsers.countAccounts(raw))
    }
}

class DpcPreflightEvaluatorTest {
    @Test
    fun `aprueba un dispositivo limpio`() {
        val (checks, ready) =
            DpcPreflightEvaluator.evaluate(
                sdk = 34,
                owners = emptyList(),
                users = listOf(DpcUser(0, "owner")),
                accounts = 0,
                mode = DpcMode.DEVICE,
            )
        assertTrue(ready)
        assertTrue(checks.none { it.status == DpcCheckStatus.FAIL })
    }

    @Test
    fun `rechaza device owner con cuentas o usuarios extra`() {
        val (checks, ready) =
            DpcPreflightEvaluator.evaluate(
                sdk = 34,
                owners = emptyList(),
                users = listOf(DpcUser(0, "owner"), DpcUser(10, "Trabajo")),
                accounts = 1,
                mode = DpcMode.DEVICE,
            )
        assertFalse(ready)
        assertEquals(
            listOf(DpcCheckStatus.FAIL),
            checks.filter { it.id == "users" || it.id == "accounts" }.map { it.status }.distinct(),
        )
    }

    @Test
    fun `modo profile exige usuario secundario y permite cuentas`() {
        val (checks, ready) =
            DpcPreflightEvaluator.evaluate(
                sdk = 34,
                owners = emptyList(),
                users = listOf(DpcUser(0, "Owner"), DpcUser(10, "Trabajo")),
                accounts = 3,
                mode = DpcMode.PROFILE,
            )
        assertTrue(ready)
        assertTrue(checks.none { it.status == DpcCheckStatus.FAIL })
    }
}
