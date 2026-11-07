package com.sfj3861.rom2apk.core.porting

/**
 * Linear 6502 disassembler used by static-recompiler frontends.
 *
 * This is analysis only: decoding instructions does not reproduce NES PPU/APU,
 * mapper, interrupts, DMA, timing, or memory-mapped I/O semantics.
 */
object Mos6502Disassembler {
    enum class AddressMode(val size: Int) {
        IMPLIED(1), ACCUMULATOR(1), IMMEDIATE(2), ZERO_PAGE(2),
        ZERO_PAGE_X(2), ZERO_PAGE_Y(2), ABSOLUTE(3), ABSOLUTE_X(3),
        ABSOLUTE_Y(3), INDIRECT(3), INDEXED_INDIRECT(2),
        INDIRECT_INDEXED(2), RELATIVE(2)
    }

    data class Instruction(
        val address: Int,
        val opcode: Int,
        val mnemonic: String,
        val mode: AddressMode?,
        val bytes: List<Int>,
        val operand: String,
        val nextAddress: Int,
        val isKnownOpcode: Boolean
    )

    private data class Definition(val mnemonic: String, val mode: AddressMode)

    private val definitions: Map<Int, Definition> = buildMap {
        val rows = """
            00 BRK IMPLIED | 01 ORA INDEXED_INDIRECT | 05 ORA ZERO_PAGE | 06 ASL ZERO_PAGE | 08 PHP IMPLIED | 09 ORA IMMEDIATE | 0A ASL ACCUMULATOR | 0D ORA ABSOLUTE | 0E ASL ABSOLUTE
            10 BPL RELATIVE | 11 ORA INDIRECT_INDEXED | 15 ORA ZERO_PAGE_X | 16 ASL ZERO_PAGE_X | 18 CLC IMPLIED | 19 ORA ABSOLUTE_Y | 1D ORA ABSOLUTE_X | 1E ASL ABSOLUTE_X
            20 JSR ABSOLUTE | 21 AND INDEXED_INDIRECT | 24 BIT ZERO_PAGE | 25 AND ZERO_PAGE | 26 ROL ZERO_PAGE | 28 PLP IMPLIED | 29 AND IMMEDIATE | 2A ROL ACCUMULATOR | 2C BIT ABSOLUTE | 2D AND ABSOLUTE | 2E ROL ABSOLUTE
            30 BMI RELATIVE | 31 AND INDIRECT_INDEXED | 35 AND ZERO_PAGE_X | 36 ROL ZERO_PAGE_X | 38 SEC IMPLIED | 39 AND ABSOLUTE_Y | 3D AND ABSOLUTE_X | 3E ROL ABSOLUTE_X
            40 RTI IMPLIED | 41 EOR INDEXED_INDIRECT | 45 EOR ZERO_PAGE | 46 LSR ZERO_PAGE | 48 PHA IMPLIED | 49 EOR IMMEDIATE | 4A LSR ACCUMULATOR | 4C JMP ABSOLUTE | 4D EOR ABSOLUTE | 4E LSR ABSOLUTE
            50 BVC RELATIVE | 51 EOR INDIRECT_INDEXED | 55 EOR ZERO_PAGE_X | 56 LSR ZERO_PAGE_X | 58 CLI IMPLIED | 59 EOR ABSOLUTE_Y | 5D EOR ABSOLUTE_X | 5E LSR ABSOLUTE_X
            60 RTS IMPLIED | 61 ADC INDEXED_INDIRECT | 65 ADC ZERO_PAGE | 66 ROR ZERO_PAGE | 68 PLA IMPLIED | 69 ADC IMMEDIATE | 6A ROR ACCUMULATOR | 6C JMP INDIRECT | 6D ADC ABSOLUTE | 6E ROR ABSOLUTE
            70 BVS RELATIVE | 71 ADC INDIRECT_INDEXED | 75 ADC ZERO_PAGE_X | 76 ROR ZERO_PAGE_X | 78 SEI IMPLIED | 79 ADC ABSOLUTE_Y | 7D ADC ABSOLUTE_X | 7E ROR ABSOLUTE_X
            81 STA INDEXED_INDIRECT | 84 STY ZERO_PAGE | 85 STA ZERO_PAGE | 86 STX ZERO_PAGE | 88 DEY IMPLIED | 8A TXA IMPLIED | 8C STY ABSOLUTE | 8D STA ABSOLUTE | 8E STX ABSOLUTE
            90 BCC RELATIVE | 91 STA INDIRECT_INDEXED | 94 STY ZERO_PAGE_X | 95 STA ZERO_PAGE_X | 96 STX ZERO_PAGE_Y | 98 TYA IMPLIED | 99 STA ABSOLUTE_Y | 9A TXS IMPLIED | 9D STA ABSOLUTE_X
            A0 LDY IMMEDIATE | A1 LDA INDEXED_INDIRECT | A2 LDX IMMEDIATE | A4 LDY ZERO_PAGE | A5 LDA ZERO_PAGE | A6 LDX ZERO_PAGE | A8 TAY IMPLIED | A9 LDA IMMEDIATE | AA TAX IMPLIED | AC LDY ABSOLUTE | AD LDA ABSOLUTE | AE LDX ABSOLUTE
            B0 BCS RELATIVE | B1 LDA INDIRECT_INDEXED | B4 LDY ZERO_PAGE_X | B5 LDA ZERO_PAGE_X | B6 LDX ZERO_PAGE_Y | B8 CLV IMPLIED | B9 LDA ABSOLUTE_Y | BA TSX IMPLIED | BC LDY ABSOLUTE_X | BD LDA ABSOLUTE_X | BE LDX ABSOLUTE_Y
            C0 CPY IMMEDIATE | C1 CMP INDEXED_INDIRECT | C4 CPY ZERO_PAGE | C5 CMP ZERO_PAGE | C6 DEC ZERO_PAGE | C8 INY IMPLIED | C9 CMP IMMEDIATE | CA DEX IMPLIED | CC CPY ABSOLUTE | CD CMP ABSOLUTE | CE DEC ABSOLUTE
            D0 BNE RELATIVE | D1 CMP INDIRECT_INDEXED | D5 CMP ZERO_PAGE_X | D6 DEC ZERO_PAGE_X | D8 CLD IMPLIED | D9 CMP ABSOLUTE_Y | DD CMP ABSOLUTE_X | DE DEC ABSOLUTE_X
            E0 CPX IMMEDIATE | E1 SBC INDEXED_INDIRECT | E4 CPX ZERO_PAGE | E5 SBC ZERO_PAGE | E6 INC ZERO_PAGE | E8 INX IMPLIED | E9 SBC IMMEDIATE | EA NOP IMPLIED | EC CPX ABSOLUTE | ED SBC ABSOLUTE | EE INC ABSOLUTE
            F0 BEQ RELATIVE | F1 SBC INDIRECT_INDEXED | F5 SBC ZERO_PAGE_X | F6 INC ZERO_PAGE_X | F8 SED IMPLIED | F9 SBC ABSOLUTE_Y | FD SBC ABSOLUTE_X | FE INC ABSOLUTE_X
        """.trimIndent()
        rows.replace("|", " ").split(Regex("\\s+")).filter { it.isNotBlank() }
            .chunked(3).forEach { tuple ->
                if (tuple.size == 3) {
                    val opcode = tuple[0].toInt(16)
                    put(opcode, Definition(tuple[1], AddressMode.valueOf(tuple[2])))
                }
            }
    }

    /** Number of documented NMOS 6502 opcodes represented by this decoder. */
    val supportedOpcodeCount: Int get() = definitions.size

    fun disassemble(
        bytes: ByteArray,
        startAddress: Int = 0x8000,
        maxInstructions: Int = Int.MAX_VALUE
    ): List<Instruction> {
        require(startAddress in 0..0xFFFF) { "La dirección inicial debe ser de 16 bits." }
        require(maxInstructions >= 0) { "El límite de instrucciones no puede ser negativo." }
        val result = ArrayList<Instruction>()
        var offset = 0
        while (offset < bytes.size && result.size < maxInstructions) {
            val address = (startAddress + offset) and 0xFFFF
            val opcode = bytes[offset].toInt() and 0xFF
            val definition = definitions[opcode]
            // BRK consumes a padding/signature byte even though its addressing mode is implied.
            val length = if (opcode == 0x00) 2 else definition?.mode?.size ?: 1
            val available = minOf(length, bytes.size - offset)
            val instructionBytes = (0 until available).map { bytes[offset + it].toInt() and 0xFF }
            val complete = available == length
            val operand = if (definition == null) {
                "$" + opcode.toString(16).uppercase().padStart(2, '0')
            } else if (!complete) {
                "<truncated>"
            } else {
                formatOperand(definition.mode, instructionBytes, address)
            }
            result += Instruction(
                address = address,
                opcode = opcode,
                mnemonic = if (definition == null) ".BYTE" else definition.mnemonic,
                mode = definition?.mode,
                bytes = instructionBytes,
                operand = operand,
                nextAddress = (address + available) and 0xFFFF,
                isKnownOpcode = definition != null
            )
            offset += available
            if (!complete) break
        }
        return result
    }

    private fun formatOperand(mode: AddressMode, bytes: List<Int>, address: Int): String {
        val low = bytes.getOrElse(1) { 0 }
        val high = bytes.getOrElse(2) { 0 }
        val word = low or (high shl 8)
        fun hex(value: Int, digits: Int) = "$" + value.toString(16).uppercase().padStart(digits, '0')
        return when (mode) {
            AddressMode.IMPLIED -> ""
            AddressMode.ACCUMULATOR -> "A"
            AddressMode.IMMEDIATE -> "#${hex(low, 2)}"
            AddressMode.ZERO_PAGE -> hex(low, 2)
            AddressMode.ZERO_PAGE_X -> "${hex(low, 2)},X"
            AddressMode.ZERO_PAGE_Y -> "${hex(low, 2)},Y"
            AddressMode.ABSOLUTE -> hex(word, 4)
            AddressMode.ABSOLUTE_X -> "${hex(word, 4)},X"
            AddressMode.ABSOLUTE_Y -> "${hex(word, 4)},Y"
            AddressMode.INDIRECT -> "(${hex(word, 4)})"
            AddressMode.INDEXED_INDIRECT -> "(${hex(low, 2)},X)"
            AddressMode.INDIRECT_INDEXED -> "(${hex(low, 2)}),Y"
            AddressMode.RELATIVE -> {
                val displacement = if (low < 0x80) low else low - 0x100
                hex((address + 2 + displacement) and 0xFFFF, 4)
            }
        }
    }
}
