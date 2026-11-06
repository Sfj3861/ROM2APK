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
                "package generated.port\nimport android.app.Activity\nimport android.view.View\nimport android.widget.FrameLayout\nclass GeneratedGameImplementation : GeneratedGamePort {\n override val title = \"Test\"\n override fun createSession(activity: Activity) = object : GeneratedGameSession { override val view: View = FrameLayout(activity); override fun onCreate() {} ; override fun onPause() {} ; override fun onResume() {} ; override fun onDestroy() {} ; override fun onInputChanged(pressed: Set<GameAction>) {} }\n}"
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
        assertTrue("app/src/main/java/generated/port/VirtualControlsView.kt" in entries)
        assertTrue(entries.getValue("app/src/main/java/generated/port/GeneratedPortActivity.kt").contains("dispatchKeyEvent"))
        assertTrue(entries.getValue("app/src/main/java/generated/port/GeneratedPortActivity.kt").contains("KEYCODE_DPAD_UP"))
        assertTrue(entries.getValue("app/src/main/java/generated/port/GeneratedPortActivity.kt").contains("KEYCODE_W"))
        assertTrue(entries.getValue("app/src/main/java/generated/port/GeneratedPortActivity.kt").contains("KEYCODE_BUTTON_A"))
        assertTrue(entries.getValue("app/src/main/java/generated/port/GeneratedPortActivity.kt").contains("onInputChanged"))
        assertTrue(entries.getValue("app/src/main/java/generated/port/VirtualControlsView.kt").contains("ACTION_POINTER_DOWN"))
        assertTrue(entries.getValue("app/src/main/java/generated/port/VirtualControlsView.kt").contains("GameAction.L"))
        assertTrue(entries.getValue("app/src/main/java/generated/port/VirtualControlsView.kt").contains("capturedPointers"))
        assertTrue(entries.getValue("app/src/main/java/generated/port/VirtualControlsView.kt").contains("ordinary touchscreen input"))
        assertTrue(entries.getValue("app/src/main/java/generated/port/VirtualControlsView.kt").contains("drawPill(canvas, GameAction.L"))
        assertTrue(entries.getValue("app/src/main/java/generated/port/VirtualControlsView.kt").contains("drawPill(canvas, GameAction.R"))
        assertTrue(entries.getValue("app/src/main/java/generated/port/VirtualControlsView.kt").contains("pointerActions.values.flatten()"))
        assertTrue(entries.getValue("app/src/main/java/generated/port/GeneratedPortActivity.kt").contains("keyboardKeys[event.keyCode]"))
        assertTrue(entries.getValue("app/src/main/java/generated/port/GeneratedPortActivity.kt").contains("keyboardKeys.values + touchActions"))
        assertTrue(entries.getValue("app/src/main/java/generated/port/GeneratedGamePort.kt").contains("onInputChanged"))
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
