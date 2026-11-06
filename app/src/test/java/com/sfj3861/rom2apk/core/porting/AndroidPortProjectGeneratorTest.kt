package com.sfj3861.rom2apk.core.porting

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class AndroidPortProjectGeneratorTest {
    private val request = PortRequest(
        applicationId = "com.example.portedgame",
        applicationName = "Port de prueba"
    )

    private val translation = PortTranslation(
        success = true,
        backendId = "test-native-backend",
        gameTitle = "Juego de prueba",
        sourceFiles = listOf(
            PortSourceFile(
                "app/src/main/java/generated/port/GeneratedGameImplementation.kt",
                "package generated.port\nclass GeneratedGameImplementation : GeneratedGamePort {\n override val title = \"Test\"\n override fun createSession() = object : GeneratedGameSession { override fun onCreate() {} ; override fun onPause() {} ; override fun onResume() {} ; override fun onDestroy() {} }\n}"
            )
        )
    )

    @Test
    fun createsAndroidProjectZipWithBackendSource() {
        val bytes = AndroidPortProjectGenerator.generate(request, translation)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }

        assertTrue("app/build.gradle.kts" in entries)
        assertTrue("app/src/main/AndroidManifest.xml" in entries)
        assertTrue("app/src/main/java/generated/port/GeneratedGameImplementation.kt" in entries)
        assertFalse(entries.keys.any { it.endsWith(".nes") || it.endsWith(".gba") })
        assertTrue(entries.getValue("README-PORT.md").contains("not a bundled console emulator"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPathTraversalFromBackend() {
        AndroidPortProjectGenerator.generate(
            request,
            translation.copy(sourceFiles = listOf(PortSourceFile("../escape.kt", "bad")))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidApplicationId() {
        AndroidPortProjectGenerator.generate(
            request.copy(applicationId = "bad id"),
            translation
        )
    }

    @Test
    fun validatesApplicationIds() {
        assertTrue(AndroidPortProjectGenerator.isValidApplicationId("com.sfj3861.game"))
        assertFalse(AndroidPortProjectGenerator.isValidApplicationId("com..game"))
        assertFalse(AndroidPortProjectGenerator.isValidApplicationId("one"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFailedTranslation() {
        AndroidPortProjectGenerator.generate(request, translation.copy(success = false))
    }
}
