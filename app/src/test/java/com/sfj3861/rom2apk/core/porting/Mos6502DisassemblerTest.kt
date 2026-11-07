package com.sfj3861.rom2apk.core.porting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mos6502DisassemblerTest {
    @Test
    fun decodesImmediateAndAbsoluteInstructions() {
        val result = Mos6502Disassembler.disassemble(
            byteArrayOf(0xA9.toByte(), 0x42, 0x8D.toByte(), 0x00, 0x20),
            startAddress = 0x8000
        )
        assertEquals(2, result.size)
        assertEquals("LDA", result[0].mnemonic)
        assertEquals("#$42", result[0].operand)
        assertEquals(0x8002, result[0].nextAddress)
        assertEquals("STA", result[1].mnemonic)
        assertEquals("$2000", result[1].operand)
    }

    @Test
    fun resolvesRelativeBranchTargetWithSignedOffset() {
        val forward = Mos6502Disassembler.disassemble(
            byteArrayOf(0xD0.toByte(), 0x05), startAddress = 0x8000
        ).single()
        val backward = Mos6502Disassembler.disassemble(
            byteArrayOf(0xD0.toByte(), 0xFC.toByte()), startAddress = 0x8000
        ).single()

        assertEquals("$8007", forward.operand)
        assertEquals("$7FFE", backward.operand)
    }

    @Test
    fun preservesUnknownOpcodesAsSingleByteData() {
        val result = Mos6502Disassembler.disassemble(byteArrayOf(0x02, 0xEA.toByte()))
        assertEquals(2, result.size)
        assertEquals(".BYTE", result[0].mnemonic)
        assertFalse(result[0].isKnownOpcode)
        assertEquals("NOP", result[1].mnemonic)
    }

    @Test
    fun reportsTruncatedMultiByteInstructionWithoutReadingPastInput() {
        val result = Mos6502Disassembler.disassemble(byteArrayOf(0xA9.toByte()))
        assertEquals(1, result.size)
        assertEquals("<truncated>", result.single().operand)
        assertEquals(1, result.single().bytes.size)
        assertTrue(result.single().isKnownOpcode)
    }

    @Test
    fun supportsInstructionLimitAndAddressWrapping() {
        val result = Mos6502Disassembler.disassemble(
            byteArrayOf(0xEA.toByte(), 0xEA.toByte()),
            startAddress = 0xFFFF,
            maxInstructions = 1
        )
        assertEquals(1, result.size)
        assertEquals(0xFFFF, result.single().address)
        assertEquals(0, result.single().nextAddress)
    }
}
