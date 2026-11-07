package com.sfj3861.rom2apk.core.porting

import com.sfj3861.rom2apk.core.rom.RomFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePortingPipelineTest {
    private class FakeBackend(
        override val supportedFormats: Set<RomFormat> = setOf(RomFormat.GB),
        private val canInspect: Boolean = true
    ) : NativePortBackend {
        override val id = "test-gb"
        override val displayName = "Test backend"

        override fun inspect(input: ByteArray, fileName: String) =
            PortInspection(canInspect, "Test inspection")

        override fun translate(input: ByteArray, request: PortRequest) =
            PortTranslation(
                success = true,
                backendId = id,
                gameTitle = "Test game",
                sourceFiles = listOf(PortSourceFile(
                    "app/src/main/java/example/GeneratedGameImplementation.kt",
                    "package generated.port"
                ))
            )
    }

    @Test
    fun reportsNoEmulatorFallbackForUnimplementedSystem() {
        val result = NativePortingPipeline(emptyList()).translate(
            "game.nes", byteArrayOf(1), PortRequest("com.example.game", "Game")
        )
        assertTrue(result is PortingOutcome.Unsupported)
        assertTrue((result as PortingOutcome.Unsupported).reason.contains("No se utilizará un emulador"))
    }

    @Test
    fun rejectsUnsupportedInspection() {
        val result = NativePortingPipeline(listOf(FakeBackend(canInspect = false))).translate(
            "game.gb", byteArrayOf(1), PortRequest("com.example.game", "Game")
        )
        assertTrue(result is PortingOutcome.Rejected)
    }

    @Test
    fun invokesMatchingBackendAndReturnsGeneratedSource() {
        val result = NativePortingPipeline(listOf(FakeBackend())).translate(
            "game.GB", byteArrayOf(1), PortRequest("com.example.game", "Game")
        )
        assertTrue(result is PortingOutcome.Ready)
        assertEquals("test-gb", (result as PortingOutcome.Ready).translation.backendId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTwoBackendsForSameFormat() {
        NativePortingPipeline(listOf(FakeBackend(), FakeBackend().copyWithId("second")))
    }

    private fun FakeBackend.copyWithId(newId: String): NativePortBackend =
        object : NativePortBackend by this {
            override val id: String = newId
        }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateBackendIds() {
        NativePortingPipeline(listOf(FakeBackend(), FakeBackend()))
    }
}
