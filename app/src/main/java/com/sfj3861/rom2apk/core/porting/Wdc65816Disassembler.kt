package com.sfj3861.rom2apk.core.porting

/**
 * Conservative 65C816 instruction decoder for SNES static-analysis work.
 * Width-sensitive immediates are decoded using the caller's M/X status.
 * Unknown or unsupported opcodes remain explicitly unknown; this is not a CPU.
 */
object Wdc65816Disassembler {
    enum class Mode(val fixedSize: Int) {
        IMPLIED(1), IMMEDIATE_M(0), IMMEDIATE_X(0), IMMEDIATE8(2),
        DIRECT(2), DIRECT_X(2), DIRECT_Y(2), ABSOLUTE(3), ABSOLUTE_X(3),
        ABSOLUTE_Y(3), LONG(4), LONG_X(4), RELATIVE8(2), RELATIVE16(3),
        STACK_RELATIVE(2), DIRECT_INDIRECT(2), DIRECT_INDEXED_INDIRECT(2),
        DIRECT_INDIRECT_Y(2), DIRECT_LONG_INDIRECT(2), DIRECT_LONG_INDIRECT_Y(2),
        ABSOLUTE_INDIRECT(3), ABSOLUTE_INDEXED_INDIRECT(3), BLOCK_MOVE(3)
    }

    data class Instruction(
        val address: Int,
        val opcode: Int,
        val mnemonic: String,
        val mode: Mode?,
        val bytes: List<Int>,
        val operand: String,
        val nextAddress: Int,
        val known: Boolean
    )

    private data class Def(val name: String, val mode: Mode)
    private val defs = buildMap<Int, Def> {
        fun add(name: String, mode: Mode, vararg opcodes: Int) =
            opcodes.forEach { put(it, Def(name, mode)) }
        add("BRK", Mode.IMMEDIATE8, 0x00); add("COP", Mode.IMMEDIATE8, 0x02)
        add("NOP", Mode.IMPLIED, 0xEA); add("WDM", Mode.IMMEDIATE8, 0x42)
        add("CLC", Mode.IMPLIED, 0x18); add("SEC", Mode.IMPLIED, 0x38)
        add("CLI", Mode.IMPLIED, 0x58); add("SEI", Mode.IMPLIED, 0x78)
        add("CLV", Mode.IMPLIED, 0xB8); add("CLD", Mode.IMPLIED, 0xD8)
        add("SED", Mode.IMPLIED, 0xF8); add("XCE", Mode.IMPLIED, 0xFB)
        add("RTS", Mode.IMPLIED, 0x60); add("RTL", Mode.IMPLIED, 0x6B)
        add("RTI", Mode.IMPLIED, 0x40); add("JMP", Mode.ABSOLUTE, 0x4C)
        add("JML", Mode.LONG, 0x5C); add("JSR", Mode.ABSOLUTE, 0x20)
        add("JSL", Mode.LONG, 0x22); add("BRA", Mode.RELATIVE8, 0x80)
        add("BRL", Mode.RELATIVE16, 0x82)
        add("BPL", Mode.RELATIVE8, 0x10); add("BMI", Mode.RELATIVE8, 0x30)
        add("BVC", Mode.RELATIVE8, 0x50); add("BVS", Mode.RELATIVE8, 0x70)
        add("BCC", Mode.RELATIVE8, 0x90); add("BCS", Mode.RELATIVE8, 0xB0)
        add("BNE", Mode.RELATIVE8, 0xD0); add("BEQ", Mode.RELATIVE8, 0xF0)
        add("LDA", Mode.IMMEDIATE_M, 0xA9); add("ADC", Mode.IMMEDIATE_M, 0x69)
        add("SBC", Mode.IMMEDIATE_M, 0xE9); add("AND", Mode.IMMEDIATE_M, 0x29)
        add("ORA", Mode.IMMEDIATE_M, 0x09); add("EOR", Mode.IMMEDIATE_M, 0x49)
        add("CMP", Mode.IMMEDIATE_M, 0xC9)
        add("LDX", Mode.IMMEDIATE_X, 0xA2); add("LDY", Mode.IMMEDIATE_X, 0xA0)
        add("CPX", Mode.IMMEDIATE_X, 0xE0); add("CPY", Mode.IMMEDIATE_X, 0xC0)
        add("LDA", Mode.DIRECT, 0xA5); add("LDA", Mode.DIRECT_X, 0xB5)
        add("LDA", Mode.ABSOLUTE, 0xAD); add("LDA", Mode.ABSOLUTE_X, 0xBD)
        add("LDA", Mode.ABSOLUTE_Y, 0xB9); add("LDA", Mode.LONG, 0xAF)
        add("STA", Mode.DIRECT, 0x85); add("STA", Mode.DIRECT_X, 0x95)
        add("STA", Mode.ABSOLUTE, 0x8D); add("STA", Mode.ABSOLUTE_X, 0x9D)
        add("STA", Mode.ABSOLUTE_Y, 0x99); add("STA", Mode.LONG, 0x8F)
        add("LDX", Mode.DIRECT, 0xA6); add("LDX", Mode.DIRECT_Y, 0xB6)
        add("LDX", Mode.ABSOLUTE, 0xAE); add("LDX", Mode.ABSOLUTE_Y, 0xBE)
        add("LDY", Mode.DIRECT, 0xA4); add("LDY", Mode.DIRECT_X, 0xB4)
        add("LDY", Mode.ABSOLUTE, 0xAC); add("LDY", Mode.ABSOLUTE_X, 0xBC)
        add("TAX", Mode.IMPLIED, 0xAA); add("TAY", Mode.IMPLIED, 0xA8)
        add("TXA", Mode.IMPLIED, 0x8A); add("TYA", Mode.IMPLIED, 0x98)
        add("INX", Mode.IMPLIED, 0xE8); add("DEX", Mode.IMPLIED, 0xCA)
        add("INY", Mode.IMPLIED, 0xC8); add("DEY", Mode.IMPLIED, 0x88)
        add("PHA", Mode.IMPLIED, 0x48); add("PLA", Mode.IMPLIED, 0x68)
        add("PHX", Mode.IMPLIED, 0xDA); add("PLX", Mode.IMPLIED, 0xFA)
        add("PHY", Mode.IMPLIED, 0x5A); add("PLY", Mode.IMPLIED, 0x7A)
        add("MVN", Mode.BLOCK_MOVE, 0x54); add("MVP", Mode.BLOCK_MOVE, 0x44)
    }

    fun disassemble(
        bytes: ByteArray,
        startAddress: Int = 0x008000,
        accumulator8Bit: Boolean = true,
        index8Bit: Boolean = true,
        maxInstructions: Int = Int.MAX_VALUE
    ): List<Instruction> {
        require(startAddress in 0..0xFFFFFF)
        require(maxInstructions >= 0)
        val out = ArrayList<Instruction>()
        var offset = 0
        while (offset < bytes.size && out.size < maxInstructions) {
            val address = (startAddress + offset) and 0xFFFFFF
            val opcode = u8(bytes, offset)
            val def = defs[opcode]
            val mode = def?.mode
            val length = when (mode) {
                null -> 1
                Mode.IMMEDIATE_M -> if (accumulator8Bit) 2 else 3
                Mode.IMMEDIATE_X -> if (index8Bit) 2 else 3
                else -> mode.fixedSize
            }
            val available = minOf(length, bytes.size - offset)
            val raw = (0 until available).map { u8(bytes, offset + it) }
            val complete = available == length
            val operand = if (def == null) ".byte $" + opcode.toString(16).uppercase().padStart(2, '0')
                else if (!complete) "<truncated>" else format(mode!!, raw, address)
            out += Instruction(address, opcode, def?.name ?: ".BYTE", mode, raw, operand,
                (address + available) and 0xFFFFFF, def != null && complete)
            offset += available
            if (!complete) break
        }
        return out
    }

    private fun format(mode: Mode, b: List<Int>, address: Int): String {
        fun word() = b.getOrElse(1) { 0 } or (b.getOrElse(2) { 0 } shl 8)
        fun hex(v: Int, n: Int) = "$" + v.toString(16).uppercase().padStart(n, '0')
        return when (mode) {
            Mode.IMPLIED -> ""
            Mode.IMMEDIATE_M, Mode.IMMEDIATE_X, Mode.IMMEDIATE8 -> "#${hex(if (b.size > 2) word() else b[1], if (b.size > 2) 4 else 2)}"
            Mode.DIRECT, Mode.DIRECT_X, Mode.DIRECT_Y, Mode.STACK_RELATIVE -> hex(b[1], 2)
            Mode.ABSOLUTE, Mode.ABSOLUTE_X, Mode.ABSOLUTE_Y, Mode.ABSOLUTE_INDIRECT, Mode.ABSOLUTE_INDEXED_INDIRECT -> hex(word(), 4)
            Mode.LONG, Mode.LONG_X -> hex(word() or (b.getOrElse(3) { 0 } shl 16), 6)
            Mode.RELATIVE8 -> hex((address + 2 + if (b[1] < 0x80) b[1] else b[1] - 0x100) and 0xFFFFFF, 6)
            Mode.RELATIVE16 -> {
                val d = word().let { if (it < 0x8000) it else it - 0x10000 }
                hex((address + 3 + d) and 0xFFFFFF, 6)
            }
            Mode.BLOCK_MOVE -> "${hex(b[1], 2)},${hex(b[2], 2)}"
            else -> hex(b[1], 2)
        }
    }

    private fun u8(bytes: ByteArray, i: Int) = bytes[i].toInt() and 0xFF
}
