package dev.adboptimizer.core.adb

import org.junit.Assert.assertEquals
import org.junit.Test

class AdbLogSanitizerTest {
    @Test
    fun `sanitizes command for secure logging without leaking full arguments or scripts`() {
        val cmd = "pm install -r /data/local/tmp/app.apk"
        val sanitized = sanitizeCommand(cmd)
        assertEquals("pm install (len=${cmd.length})", sanitized)
    }

    private fun sanitizeCommand(command: String): String {
        val trimmed = command.trim()
        val firstWord = trimmed.substringBefore(' ')
        val secondWord = trimmed.substringAfter(' ').substringBefore(' ').takeIf { it.isNotBlank() }
        val detail = if (secondWord != null && !secondWord.startsWith('-')) "$firstWord $secondWord" else firstWord
        return "$detail (len=${command.length})"
    }
}
