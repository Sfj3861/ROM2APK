package com.sfj3861.rom2apk.core.porting

import com.sfj3861.rom2apk.core.rom.RomCheckStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NesNromPreflightTest {
    private fun image(vararg code: Int): ByteArray =
        ByteArray(16 + 16_384).also { bytes ->
            bytes[0] = 0x4E; bytes[1] = 0x45; bytes[2] = 0x53; bytes[3] = 0x1A
            bytes[4] = 1
            code.forEachIndexed { index, value -> bytes[16 + index] = value.toByte() }
            bytes[16 + 0x3FFC] = 0x00
            bytes[16 + 0x3FFD] = 0x80.toByte()
        }

    @Test
    fun reportsNromAndReachableCodeAnalysis() {
        val result = NesNromPreflight.analyze(image(0xEA, 0x60))
        assertEquals(RomCheckStatus.VALIDATED, result.status)
        assertEquals("NES-NROM-ANALYZED", result.diagnosticCode)
        assertTrue(result.details.contains("Instrucciones alcanzables: 2"))
        assertTrue(result.details.contains("no equivale a una traducción ejecutable"))
    }

    @Test
    fun doesNotClaimDynamicJumpCanBeTranslated() {
        val result = NesNromPreflight.analyze(image(0x6C, 0x00, 0x20))
        assertEquals(RomCheckStatus.NEEDS_SPECIAL_HANDLING, result.status)
        assertEquals("NES-NROM-PARTIAL", result.diagnosticCode)
    }

    @Test
    fun rejectsNonNromMapperWithoutFallback() {
        val bytes = image(0xEA)
        bytes[6] = 0x10
        val result = NesNromPreflight.analyze(bytes)
        assertEquals(RomCheckStatus.NEEDS_SPECIAL_HANDLING, result.status)
        assertEquals("NES-NROM-UNSUPPORTED", result.diagnosticCode)
    }
}
