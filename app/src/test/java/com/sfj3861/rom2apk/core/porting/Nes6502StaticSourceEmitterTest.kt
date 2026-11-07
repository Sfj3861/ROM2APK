package com.sfj3861.rom2apk.core.porting

import org.junit.Assert.*
import org.junit.Test

class Nes6502StaticSourceEmitterTest {
    private fun analyze(vararg code: Int): NesNromCodeAnalyzer.Result {
        val prg = ByteArray(16_384) { 0xEA.toByte() }
        code.forEachIndexed { index, value -> prg[index] = value.toByte() }
        prg[0x3FFC] = 0x00.toByte()
        prg[0x3FFD] = 0x80.toByte()
        return NesNromCodeAnalyzer.analyze(prg)
    }

    @Test
    fun emitsDirectKotlinForImmediateLoadsTransfersAndReturn() {
        val output = Nes6502StaticSourceEmitter.emit(
            analyze(0xA9, 0x2A, 0xAA, 0xE8, 0x4C, 0x03, 0x80)
        )
        assertTrue(output.success)
        assertEquals(4, output.translatedInstructionCount)
        val source = output.source!!
        assertTrue(source.contains("state.a = setNZ(state, 0x002A)"))
        assertTrue(source.contains("state.x = setNZ(state, state.a)"))
        assertTrue(source.contains("state.x = setNZ(state, state.x + 1)"))
        assertTrue(source.contains("state.pc = 0x8003"))
        assertFalse(source.contains("when (opcode)"))
    }

    @Test
    fun emitsConditionalBranchesAsDirectFlagTests() {
        val output = Nes6502StaticSourceEmitter.emit(
            analyze(0xA9, 0x00, 0xD0, 0x02, 0xEA, 0xEA, 0x4C, 0x04, 0x80)
        )
        assertTrue(output.success)
        assertTrue(output.source!!.contains("if (!state.zero) 0x8006 else 0x8004"))
    }

    @Test
    fun rejectsInstructionsOutsideThePreciselySupportedSubset() {
        val output = Nes6502StaticSourceEmitter.emit(
            analyze(0x8D, 0x00, 0x20, 0x60)
        )
        assertFalse(output.success)
        assertNull(output.source)
        assertTrue(output.diagnostics.any { it.contains("STA") && it.contains("no traducible") })
    }

    @Test
    fun rejectsIncompleteControlFlowAnalysis() {
        val result = analyze(0x6C, 0x00, 0x20)
        val output = Nes6502StaticSourceEmitter.emit(result)
        assertFalse(output.success)
        assertNull(output.source)
        assertTrue(output.diagnostics.any { it.contains("no está completo") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsafePackageNames() {
        Nes6502StaticSourceEmitter.emit(analyze(0xEA, 0x4C, 0x00, 0x80), "generated.cpu; malicious")
    }
}
