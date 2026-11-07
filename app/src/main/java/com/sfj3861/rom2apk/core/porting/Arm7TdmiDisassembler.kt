package com.sfj3861.rom2apk.core.porting

/**
 * ARM7TDMI instruction classifier/decoder for GBA static analysis.
 * It describes instruction fields and control-flow targets; it does not execute
 * ARM code, implement GBA peripherals, or claim a game can be ported yet.
 */
object Arm7TdmiDisassembler {
    enum class State { ARM, THUMB }
    enum class Kind { DATA_PROCESSING, MULTIPLY, BRANCH, BRANCH_EXCHANGE, SINGLE_TRANSFER,
        BLOCK_TRANSFER, SWI, HALFWORD_TRANSFER, PUSH_POP, CONDITIONAL_BRANCH,
        UNCONDITIONAL_BRANCH, LITERAL_LOAD, UNKNOWN }

    data class Instruction(
        val address: Int, val state: State, val raw: Int, val size: Int,
        val kind: Kind, val mnemonic: String, val condition: Int?,
        val branchTarget: Int?, val fields: Map<String, Int>
    )

    fun decodeArm(word: Int, address: Int): Instruction {
        require(address >= 0)
        val cond = (word ushr 28) and 0xF
        val kind = when {
            (word and 0x0FFFFFF0) == 0x012FFF10 -> Kind.BRANCH_EXCHANGE
            (word and 0x0E000000) == 0x0A000000 -> Kind.BRANCH
            (word and 0x0FC000F0) == 0x00000090 -> Kind.MULTIPLY
            (word and 0x0E000000) == 0x08000000 -> Kind.BLOCK_TRANSFER
            (word and 0x0C000000) == 0x04000000 -> Kind.SINGLE_TRANSFER
            (word and 0x0E000090) == 0x00000090 -> Kind.HALFWORD_TRANSFER
            (word and 0x0F000000) == 0x0F000000 -> Kind.SWI
            (word and 0x0C000000) == 0x00000000 -> Kind.DATA_PROCESSING
            else -> Kind.UNKNOWN
        }
        val branch = if (kind == Kind.BRANCH) {
            val imm = word and 0x00FFFFFF
            val signed = if ((imm and 0x00800000) != 0) imm or -0x01000000 else imm
            (address + 8 + (signed shl 2))
        } else null
        val names = listOf("EQ","NE","CS","CC","MI","PL","VS","VC","HI","LS","GE","LT","GT","LE","","")
        val condName = names[cond]
        val opcode = (word ushr 21) and 0xF
        val dataNames = listOf("AND","EOR","SUB","RSB","ADD","ADC","SBC","RSC","TST","TEQ","CMP","CMN","ORR","MOV","BIC","MVN")
        val mnemonic = when (kind) {
            Kind.BRANCH -> if ((word and (1 shl 24)) != 0) "BL" else "B"
            Kind.BRANCH_EXCHANGE -> "BX"
            Kind.MULTIPLY -> "MUL"
            Kind.SINGLE_TRANSFER -> if ((word and (1 shl 20)) != 0) "LDR" else "STR"
            Kind.BLOCK_TRANSFER -> if ((word and (1 shl 20)) != 0) "LDM" else "STM"
            Kind.HALFWORD_TRANSFER -> if ((word and (1 shl 20)) != 0) "LDRH" else "STRH"
            Kind.SWI -> "SWI"
            Kind.DATA_PROCESSING -> dataNames[opcode]
            else -> "UNKNOWN"
        } + if (cond == 0xE || cond == 0xF) "" else condName
        return Instruction(address, State.ARM, word, 4, kind, mnemonic,
            if (cond == 0xE) null else cond, branch,
            mapOf("rn" to ((word ushr 16) and 0xF), "rd" to ((word ushr 12) and 0xF),
                "opcode" to opcode, "immediate" to (word and 0xFFF)))
    }

    fun decodeThumb(halfword: Int, address: Int): Instruction {
        require(halfword in 0..0xFFFF && address >= 0)
        val h = halfword
        val kind: Kind
        val mnemonic: String
        var target: Int? = null
        when {
            (h and 0xF800) == 0xE000 -> {
                kind = Kind.UNCONDITIONAL_BRANCH; mnemonic = "B"
                val imm = h and 0x7FF
                val signed = if ((imm and 0x400) != 0) imm or -0x800 else imm
                target = address + 4 + (signed shl 1)
            }
            (h and 0xF000) == 0xD000 && ((h ushr 8) and 0xF) != 0xF &&
                ((h ushr 8) and 0xF) != 0xE -> {
                kind = Kind.CONDITIONAL_BRANCH
                val cond = (h ushr 8) and 0xF
                val names = listOf("EQ","NE","CS","CC","MI","PL","VS","VC","HI","LS","GE","LT","GT","LE")
                mnemonic = "B" + names[cond]
                val imm = h and 0xFF
                val signed = if ((imm and 0x80) != 0) imm - 0x100 else imm
                target = address + 4 + (signed shl 1)
            }
            (h and 0xF800) == 0x4800 -> { kind = Kind.LITERAL_LOAD; mnemonic = "LDR" }
            (h and 0xF800) == 0x1800 -> { kind = Kind.DATA_PROCESSING; mnemonic = "ADD/SUB" }
            (h and 0xF800) == 0x2000 -> { kind = Kind.DATA_PROCESSING; mnemonic = "MOV" }
            (h and 0xF800) == 0x2800 -> { kind = Kind.DATA_PROCESSING; mnemonic = "CMP" }
            (h and 0xF800) == 0x3000 -> { kind = Kind.DATA_PROCESSING; mnemonic = "ADD" }
            (h and 0xF800) == 0x3800 -> { kind = Kind.DATA_PROCESSING; mnemonic = "SUB" }
            (h and 0xFF00) == 0xBE00 -> { kind = Kind.UNKNOWN; mnemonic = "BKPT" }
            (h and 0xFF00) == 0xDF00 -> { kind = Kind.SWI; mnemonic = "SWI" }
            (h and 0xF600) == 0xB400 -> { kind = Kind.PUSH_POP; mnemonic = if ((h and 0x0800) == 0) "PUSH" else "POP" }
            else -> { kind = Kind.UNKNOWN; mnemonic = "UNKNOWN" }
        }
        return Instruction(address, State.THUMB, h, 2, kind, mnemonic, null, target,
            mapOf("lowRegister" to (h and 7), "immediate" to (h and 0xFF)))
    }

    fun decodeArmStream(bytes: ByteArray, startAddress: Int = 0x08000000): List<Instruction> {
        require(startAddress >= 0)
        val out = mutableListOf<Instruction>()
        var offset = 0
        while (offset + 4 <= bytes.size) {
            val word = (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
            out += decodeArm(word, startAddress + offset)
            offset += 4
        }
        return out
    }

    fun decodeThumbStream(bytes: ByteArray, startAddress: Int = 0x08000000): List<Instruction> {
        require(startAddress >= 0)
        val out = mutableListOf<Instruction>()
        var offset = 0
        while (offset + 2 <= bytes.size) {
            val half = (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8)
            out += decodeThumb(half, startAddress + offset)
            offset += 2
        }
        return out
    }
}
