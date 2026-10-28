package com.optimizer.tools.app.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class TvManifestTest {
    private val androidNamespace = "http://schemas.android.com/apk/res/android"

    private fun readManifest(): Element {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml")).documentElement
    }

    @Test
    fun televisionAndPhoneInstallRequirementsRemainOptional() {
        val features = readManifest().getElementsByTagName("uses-feature")
        listOf("android.software.leanback", "android.hardware.touchscreen").forEach { name ->
            val feature =
                (0 until features.length)
                    .map { features.item(it) as Element }
                    .single { it.getAttributeNS(androidNamespace, "name") == name }
            assertEquals("false", feature.getAttributeNS(androidNamespace, "required"))
        }
    }

    @Test
    fun existingActivityIsDiscoverableFromBothLaunchersAndHasBanner() {
        val manifest = readManifest()
        val application = manifest.getElementsByTagName("application").item(0) as Element
        assertEquals("@drawable/tv_banner", application.getAttributeNS(androidNamespace, "banner"))
        val categories = manifest.getElementsByTagName("category")
        val names =
            (0 until categories.length).map {
                (categories.item(it) as Element).getAttributeNS(androidNamespace, "name")
            }
        assertTrue(names.contains("android.intent.category.LAUNCHER"))
        assertTrue(names.contains("android.intent.category.LEANBACK_LAUNCHER"))
    }
}
