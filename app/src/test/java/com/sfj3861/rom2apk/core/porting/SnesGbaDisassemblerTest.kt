package com.sfj3861.rom2apk.core.porting

import org.junit.Assert.*
import org.junit.Test

class SnesGbaDisassemblerTest {
    @Test fun decodesSnesImmediateWidthFromProcessorStatus() {
        val code = byteArrayOf(0xA9.toByte(), 0x34, 0x12, 0xEA.toByte())
        val m8 = Wdc65816Disassembler.disassemble(code, accumulator8Bit = true)
        val m16 = Wdc65816Disassembler.disassemble(code, accumulator8Bit = false)
        assertEquals("LDA", m8[0].mnemonic)
        assertEquals(0x8002, m8[0].nextAddress)
        assertEquals(0x8003, m16[0].nextAddress)
        assertEquals("NOP", m16[1].mnemonic)
    }

    @Test fun decodesSnesLongJumpAndRelativeBranch() {
        val code = byteArrayOf(0x5C, 0x56, 0x34, 0x12, 0x80.toByte(), 0xFC.toByte())
        val decoded = Wdc65816Disassembler.disassemble(code)
        assertEquals("JML", decoded[0].mnemonic)
        assertEquals("$123456", decoded[0].operand)
        assertEquals("$008004", decoded[1].operand)
    }

    @Test fun unknownSnesOpcodesAreNotPretendedToBeDecoded() {
        val decoded = Wdc65816Disassembler.disassemble(byteArrayOf(0x03, 0xEA.toByte()))
        assertFalse(decoded[0].known)
        assertEquals(".BYTE", decoded[0].mnemonic)
    }

    @Test fun decodesArmBranchTargetAndCondition() {
        val instruction = Arm7TdmiDisassembler.decodeArm(0xEA000000.toInt(), 0x08000000)
        assertEquals(Arm7TdmiDisassembler.Kind.BRANCH, instruction.kind)
        assertEquals(0x08000008, instruction.branchTarget)
        assertEquals("B", instruction.mnemonic)
    }

    @Test fun decodesThumbConditionalAndUnconditionalBranches() {
        val conditional = Arm7TdmiDisassembler.decodeThumb(0xD001, 0x08000000)
        val unconditional = Arm7TdmiDisassembler.decodeThumb(0xE7FE, 0x08000000)
        assertEquals(0x08000006, conditional.branchTarget)
        assertEquals(0x08000000, unconditional.branchTarget)
        assertEquals("BNE", conditional.mnemonic)
    }

    @Test fun decodesLittleEndianArmStreamAndIgnoresTrailingBytes() {
        val stream = Arm7TdmiDisassembler.decodeArmStream(
            byteArrayOf(0x00, 0x00, 0xA0.toByte(), 0xE1.toByte(), 0x7F),
            0x08000000
        )
        assertEquals(1, stream.size)
        assertEquals("MOV", stream[0].mnemonic)
    }
}
