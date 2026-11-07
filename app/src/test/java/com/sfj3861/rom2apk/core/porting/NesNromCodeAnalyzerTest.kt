package com.sfj3861.rom2apk.core.porting

import org.junit.Assert.*
import org.junit.Test

class NesNromCodeAnalyzerTest {
    private fun prgWithReset(vararg code: Int): ByteArray =
        ByteArray(16_384) { 0xEA.toByte() }.also { prg ->
            code.forEachIndexed { index, value -> prg[index] = value.toByte() }
            prg[0x3FFC] = 0x00
            prg[0x3FFD] = 0x80
        }

    @Test
    fun followsConditionalBranchAndFallthrough() {
        val prg = prgWithReset(0xA9, 0x01, 0xD0, 0x02, 0xEA, 0xEA, 0x60)
        val result = NesNromCodeAnalyzer.analyze(prg)
        assertEquals(0x8000, result.resetVector)
        assertEquals(setOf(0x8000, 0x8002, 0x8004, 0x8005, 0x8006), result.reachableAddresses)
        assertTrue(result.analysisComplete)
    }

    @Test
    fun followsAbsoluteJumpsAndSubroutineCalls() {
        val prg = prgWithReset(0x20, 0x08, 0x80, 0x4C, 0x0B, 0x80, 0xEA, 0xEA, 0x60, 0xEA, 0xEA, 0x60)
        val result = NesNromCodeAnalyzer.analyze(prg)
        assertTrue(result.reachableAddresses.containsAll(setOf(0x8000, 0x8003, 0x8008, 0x800B)))
    }

    @Test
    fun reportsIndirectJumpAsDynamicInsteadOfPretendingItIsResolved() {
        val result = NesNromCodeAnalyzer.analyze(prgWithReset(0x6C, 0x00, 0x20))
        assertFalse(result.analysisComplete)
        assertTrue(result.diagnostics.any { it.contains("destino dinámico") })
    }

    @Test
    fun reportsUnknownOpcodeAsUntranslatable() {
        val result = NesNromCodeAnalyzer.analyze(prgWithReset(0x02))
        assertFalse(result.analysisComplete)
        assertTrue(result.diagnostics.any { it.contains("no traducible") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonNromPrgSize() {
        NesNromCodeAnalyzer.analyze(ByteArray(8192))
    }
}
