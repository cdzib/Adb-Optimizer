package dev.adboptimizer.core.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbCommandRulesTest {
    // --- ShellQuote ---

    @Test
    fun `cita texto simple entre comillas simples`() {
        assertEquals("'abc'", ShellQuote.of("abc"))
        assertEquals("'a b'", ShellQuote.of("a b"))
    }

    @Test
    fun `una comilla simple dentro del texto se escapa cerrando y abriendo comillas`() {
        assertEquals("'it'\\''s'", ShellQuote.of("it's"))
    }

    @Test
    fun `variables y sustituciones no se expanden dentro de comillas simples`() {
        assertEquals("'\$HOME \$(id)'", ShellQuote.of("\$HOME \$(id)"))
    }

    @Test
    fun `un salto de linea sobrevive el citado`() {
        assertEquals("'a\nb'", ShellQuote.of("a\nb"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un NUL no se puede pasar por el shell y se rechaza`() {
        ShellQuote.of("a\u0000b")
    }

    @Test
    fun `join cita cada argumento y los separa con un espacio`() {
        assertEquals("'a' 'b c' '-r'", ShellQuote.join(listOf("a", "b c", "-r")))
        assertEquals("", ShellQuote.join(emptyList()))
    }

    // --- Nombres de paquete y componente ---

    @Test
    fun `acepta nombres de paquete de Android`() {
        assertTrue(AdbCommandRules.isPackageName("com.foo.bar"))
        assertTrue(AdbCommandRules.isPackageName("a.b"))
        assertTrue(AdbCommandRules.isPackageName("com.foo_bar.a1"))
    }

    @Test
    fun `rechaza nombres de paquete que no son de Android`() {
        assertFalse(AdbCommandRules.isPackageName("com"))
        assertFalse(AdbCommandRules.isPackageName("com.foo."))
        assertFalse(AdbCommandRules.isPackageName(".com.foo"))
        assertFalse(AdbCommandRules.isPackageName("com.foo-bar"))
        assertFalse(AdbCommandRules.isPackageName("com.foo;reboot"))
        assertFalse(AdbCommandRules.isPackageName(""))
        assertFalse(AdbCommandRules.isPackageName("a".repeat(256) + ".b"))
    }

    // --- Flags de pm install ---

    @Test
    fun `sin flags usa el reemplazo por defecto`() {
        assertEquals(listOf("-r"), AdbCommandRules.installFlags(null))
        assertEquals(listOf("-r"), AdbCommandRules.installFlags(""))
        assertEquals(listOf("-r"), AdbCommandRules.installFlags("   "))
    }

    @Test
    fun `acepta los flags booleanos de la lista`() {
        assertEquals(
            listOf("-r", "-g", "-d", "-t", "--bypass-low-target-sdk-block", "--update-ownership"),
            AdbCommandRules.installFlags("-r -g -d -t --bypass-low-target-sdk-block --update-ownership"),
        )
    }

    @Test
    fun `acepta los flags con valor solo con el formato esperado`() {
        assertEquals(listOf("--user", "all"), AdbCommandRules.installFlags("--user all"))
        assertEquals(listOf("--user", "current"), AdbCommandRules.installFlags("--user current"))
        assertEquals(listOf("--user", "10"), AdbCommandRules.installFlags("--user 10"))
        assertEquals(listOf("--install-reason", "3"), AdbCommandRules.installFlags("--install-reason 3"))
        assertEquals(listOf("-i", "com.android.vending"), AdbCommandRules.installFlags("-i com.android.vending"))
        assertEquals(
            listOf("--originating-uri", "https://example.com/app.apk"),
            AdbCommandRules.installFlags("--originating-uri https://example.com/app.apk"),
        )
    }

    @Test
    fun `rechaza flags desconocidos, valores mal formados y valores faltantes`() {
        assertNull(AdbCommandRules.installFlags("--evil"))
        assertNull(AdbCommandRules.installFlags("-r --evil"))
        assertNull(AdbCommandRules.installFlags("--user"))
        assertNull(AdbCommandRules.installFlags("--user root"))
        assertNull(AdbCommandRules.installFlags("--install-reason abc"))
        assertNull(AdbCommandRules.installFlags("-i not a package"))
        assertNull(AdbCommandRules.installFlags("--originating-uri app.apk"))
        assertNull(AdbCommandRules.installFlags("; reboot"))
    }

    // --- pm compile ---

    @Test
    fun `compila con el modo pedido o speed por defecto`() {
        assertEquals("pm compile -m speed com.foo.bar", AdbCommandRules.compileCommand("com.foo.bar", null, force = false))
        assertEquals("pm compile -m speed com.foo.bar", AdbCommandRules.compileCommand("com.foo.bar", "", force = false))
        assertEquals("pm compile -m speed-profile com.foo.bar", AdbCommandRules.compileCommand("com.foo.bar", "speed-profile", false))
        assertEquals("pm compile -m speed -f com.foo.bar", AdbCommandRules.compileCommand("com.foo.bar", "speed", force = true))
    }

    @Test
    fun `reset restaura el estado posterior a la instalacion`() {
        assertEquals("pm compile --reset com.foo.bar", AdbCommandRules.compileCommand("com.foo.bar", "reset", force = false))
    }

    @Test
    fun `rechaza modos y paquetes que no estan en la lista`() {
        assertNull(AdbCommandRules.compileCommand("com.foo.bar", "turbo", force = false))
        assertNull(AdbCommandRules.compileCommand("com.foo.bar;reboot", "speed", force = false))
    }

    @Test
    fun `construye appops set solo con operacion y modo validos`() {
        assertEquals(
            "appops set com.foo.bar RUN_IN_BACKGROUND ignore",
            AdbCommandRules.appOpsCommand("com.foo.bar", "RUN_IN_BACKGROUND", "ignore"),
        )
        assertNull(AdbCommandRules.appOpsCommand("com.foo.bar", "run_in_background", "ignore"))
        assertNull(AdbCommandRules.appOpsCommand("com.foo.bar", "RUN_IN_BACKGROUND", "always"))
        assertNull(AdbCommandRules.appOpsCommand("com.foo.bar;reboot", "RUN_IN_BACKGROUND", "ignore"))
    }

    // --- Componentes ---

    @Test
    fun `habilita y deshabilita componentes con el nombre completo`() {
        assertEquals("pm enable 'com.foo.bar/.MainActivity'", AdbCommandRules.componentCommand("com.foo.bar", ".MainActivity", true))
        assertEquals(
            "pm disable 'com.foo.bar/com.foo.bar.MainActivity'",
            AdbCommandRules.componentCommand("com.foo.bar", "com.foo.bar.MainActivity", false),
        )
    }

    @Test
    fun `el dolar de una clase interna llega a pm sin expandirse`() {
        assertEquals(
            "pm enable 'com.foo.bar/com.foo.bar.MainActivity\$Inner'",
            AdbCommandRules.componentCommand("com.foo.bar", "com.foo.bar.MainActivity\$Inner", true),
        )
    }

    @Test
    fun `rechaza componentes con caracteres de shell`() {
        assertNull(AdbCommandRules.componentCommand("com.foo.bar", "Main;reboot", true))
        assertNull(AdbCommandRules.componentCommand("com.foo.bar", "", true))
        assertNull(AdbCommandRules.componentCommand("com.foo.bar", "/.Main", true))
    }

    // --- App ops ---

    @Test
    fun `acepta solo nombres y modos de appops conocidos`() {
        assertTrue(AdbCommandRules.isAppOp("OP_RUN_IN_BACKGROUND"))
        assertFalse(AdbCommandRules.isAppOp("run_in_background"))
        assertFalse(AdbCommandRules.isAppOp("OP_RUN_IN_BACKGROUND;reboot"))

        assertTrue(AdbCommandRules.isAppOpMode("allow"))
        assertTrue(AdbCommandRules.isAppOpMode("foreground"))
        assertFalse(AdbCommandRules.isAppOpMode("always"))
        assertFalse(AdbCommandRules.isAppOpMode("allow ; reboot"))
    }

    @Test
    fun `el modo noche solo acepta yes, no o auto`() {
        assertEquals("cmd uimode night yes", AdbCommandRules.nightModeCommand("yes"))
        assertEquals("cmd uimode night auto", AdbCommandRules.nightModeCommand("auto"))
        assertNull(AdbCommandRules.nightModeCommand("siempre"))
        assertTrue(AdbCommandRules.nightModeReported("Night mode: yes", "yes"))
        assertFalse(AdbCommandRules.nightModeReported("Night mode: no", "yes"))
    }

    // --- Ajustes (settings) ---

    @Test
    fun `construye settings get y put solo con tabla y clave validas`() {
        assertEquals("settings get secure 'android_id'", AdbCommandRules.settingsGetCommand("secure", "android_id"))
        assertEquals(
            "settings put global 'demo_key' 'un valor con espacios'",
            AdbCommandRules.settingsPutCommand("global", "demo_key", "un valor con espacios"),
        )
        assertNull(AdbCommandRules.settingsGetCommand("personal", "android_id"))
        assertNull(AdbCommandRules.settingsGetCommand("secure", "-rm"))
        assertNull(AdbCommandRules.settingsGetCommand("secure", ""))
        assertNull(AdbCommandRules.settingsPutCommand("global", "demo_key", "a\u0000b"))
    }

    @Test
    fun `el valor del ajuste se cita y llega intacto`() {
        val command = AdbCommandRules.settingsPutCommand("secure", "demo", "it's")

        assertEquals("settings put secure 'demo' 'it'\\''s'", command)
    }

    @Test
    fun `una clave con salto de linea no se acepta`() {
        assertNull(AdbCommandRules.settingsGetCommand("global", "a\nb"))
        assertFalse(AdbCommandRules.isSettingsKey("a\rb"))
    }

    // --- Standby bucket ---

    @Test
    fun `consulta y cambia el standby bucket solo con nombres permitidos`() {
        assertEquals("am get-standby-bucket com.foo.bar", AdbCommandRules.standbyBucketCommand("com.foo.bar", null))
        assertEquals("am set-standby-bucket com.foo.bar rare", AdbCommandRules.standbyBucketCommand("com.foo.bar", "rare"))
        assertNull(AdbCommandRules.standbyBucketCommand("com.foo.bar", "exempted"))
        assertNull(AdbCommandRules.standbyBucketCommand("com.foo.bar", "rare; reboot"))
        assertNull(AdbCommandRules.standbyBucketCommand("com.foo.bar;reboot", "rare"))
    }

    @Test
    fun `lee el bucket que imprime el dispositivo en numero o en nombre`() {
        assertEquals("active", AdbCommandRules.parseStandbyBucket("10"))
        assertEquals("working_set", AdbCommandRules.parseStandbyBucket("20"))
        assertEquals("rare", AdbCommandRules.parseStandbyBucket("RARE"))
        assertEquals("exempted", AdbCommandRules.parseStandbyBucket("5"))
        assertEquals("never", AdbCommandRules.parseStandbyBucket("50"))
    }

    @Test
    fun `un bucket desconocido o un error no se interpretan`() {
        assertNull(AdbCommandRules.parseStandbyBucket(null))
        assertNull(AdbCommandRules.parseStandbyBucket(""))
        assertNull(AdbCommandRules.parseStandbyBucket("37"))
        assertNull(AdbCommandRules.parseStandbyBucket("error: package not found"))
        assertNull(AdbCommandRules.parseStandbyBucket("10\n20"))
    }
}
