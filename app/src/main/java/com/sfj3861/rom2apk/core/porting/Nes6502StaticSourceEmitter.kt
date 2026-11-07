package com.sfj3861.rom2apk.core.porting

/**
 * Emits statically translated Kotlin for a deliberately small 6502 subset.
 *
 * This emits direct register/memory operations, not an opcode interpreter.
 * It is only a CPU translation component: no NES PPU/APU, mapper, interrupts,
 * DMA, cycle timing, cartridge bus or game Activity is supplied here.
 */
object Nes6502StaticSourceEmitter {
    data class Output(
        val success: Boolean,
        val source: String?,
        val translatedInstructionCount: Int,
        val diagnostics: List<String>
    )

    fun emit(
        analysis: NesNromCodeAnalyzer.Result,
        packageName: String = "generated.cpu"
    ): Output {
        require(packageName.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*"))) {
            "Nombre de paquete Kotlin no válido."
        }
        if (!analysis.analysisComplete) {
            return Output(false, null, 0, listOf("El análisis de flujo no está completo.") + analysis.diagnostics)
        }
        if (analysis.instructions.isEmpty()) {
            return Output(false, null, 0, listOf("No hay instrucciones alcanzables para traducir."))
        }

        val instructions = analysis.instructions.associateBy { it.address }
        val diagnostics = mutableListOf<String>()
        val supported = setOf(
            "NOP", "RTS", "LDA", "LDX", "LDY", "STA", "TAX", "TAY", "TXA", "TYA",
            "INX", "DEX", "INY", "DEY", "CLC", "SEC", "CLV",
            "BCC", "BCS", "BEQ", "BNE", "BMI", "BPL", "BVC", "BVS", "JMP"
        )
        for (instruction in analysis.instructions) {
            val mode = instruction.mode
            val supportedMode = when (instruction.mnemonic) {
                "NOP", "RTS", "TAX", "TAY", "TXA", "TYA", "INX", "DEX", "INY", "DEY", "CLC", "SEC", "CLV" ->
                    mode == Mos6502Disassembler.AddressMode.IMPLIED
                "LDA", "LDX", "LDY" -> mode == Mos6502Disassembler.AddressMode.IMMEDIATE ||
                    mode == Mos6502Disassembler.AddressMode.ZERO_PAGE
                "STA" -> mode == Mos6502Disassembler.AddressMode.ZERO_PAGE
                "BCC", "BCS", "BEQ", "BNE", "BMI", "BPL", "BVC", "BVS" ->
                    mode == Mos6502Disassembler.AddressMode.RELATIVE
                "JMP" -> mode == Mos6502Disassembler.AddressMode.ABSOLUTE
                else -> false
            }
            if (instruction.mnemonic !in supported || !supportedMode) {
                diagnostics += "No traducible en este perfil: ${instruction.mnemonic} ${instruction.operand} en ${hex(instruction.address)}."
                continue
            }
            if (instruction.mnemonic == "JMP") {
                val target = word(instruction)
                if (target !in instructions) diagnostics += "JMP en ${hex(instruction.address)} apunta a código no analizado (${hex(target)})."
            }
            if (instruction.mnemonic in setOf("BCC", "BCS", "BEQ", "BNE", "BMI", "BPL", "BVC", "BVS")) {
                val target = branchTarget(instruction)
                if (target !in instructions) diagnostics += "Rama en ${hex(instruction.address)} apunta a código no analizado (${hex(target)})."
                if (instruction.nextAddress !in instructions) diagnostics += "La continuación de la rama en ${hex(instruction.address)} no está analizada."
            }
        }
        if (diagnostics.isNotEmpty()) return Output(false, null, 0, diagnostics.distinct())

        val source = buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("/** CPU state for statically translated NROM instruction blocks. */")
            appendLine("data class Nes6502State(")
            appendLine("    var a: Int = 0, var x: Int = 0, var y: Int = 0,")
            appendLine("    var pc: Int = 0x${analysis.resetVector!!.toString(16).uppercase()},")
            appendLine("    var carry: Boolean = false, var zero: Boolean = false,")
            appendLine("    var negative: Boolean = false, var overflow: Boolean = false,")
            appendLine("    val ram: ByteArray = ByteArray(256)")
            appendLine(")")
            appendLine()
            appendLine("/** Directly generated instruction operations; no ROM or opcode decoder is embedded. */")
            appendLine("class Nes6502TranslatedProgram {")
            appendLine("    fun run(state: Nes6502State, maxInstructions: Int = 100000): Nes6502State {")
            appendLine("        require(maxInstructions > 0)")
            appendLine("        var steps = 0")
            appendLine("        while (steps < maxInstructions) {")
            appendLine("            when (state.pc) {")
            analysis.instructions.sortedBy { it.address }.forEach { instruction ->
                appendLine("                0x${instruction.address.toString(16).uppercase()} -> {")
                emitInstruction(this, instruction)
                appendLine("                    steps++")
                appendLine("                }")
            }
            appendLine("                else -> return state")
            appendLine("            }")
            appendLine("        }")
            appendLine("        return state")
            appendLine("    }")
            appendLine("    private fun setNZ(state: Nes6502State, value: Int): Int {")
            appendLine("        val result = value and 0xFF")
            appendLine("        state.zero = result == 0")
            appendLine("        state.negative = (result and 0x80) != 0")
            appendLine("        return result")
            appendLine("    }")
            appendLine("}")
        }
        return Output(true, source, analysis.instructions.size, emptyList())
    }

    private fun emitInstruction(out: StringBuilder, i: Mos6502Disassembler.Instruction) {
        val next = i.nextAddress
        val operand = i.bytes.getOrElse(1) { 0 }
        when (i.mnemonic) {
            "NOP" -> out.appendLine("                    state.pc = 0x${hexValue(next)}")
            "RTS" -> out.appendLine("                    return state")
            "LDA", "LDX", "LDY" -> {
                val value = if (i.mode == Mos6502Disassembler.AddressMode.IMMEDIATE)
                    "0x${hexValue(operand)}" else "(state.ram[0x${hexValue(operand)}].toInt() and 0xFF)"
                val register = when (i.mnemonic) { "LDA" -> "a"; "LDX" -> "x"; else -> "y" }
                out.appendLine("                    state.$register = setNZ(state, $value)")
                out.appendLine("                    state.pc = 0x${hexValue(next)}")
            }
            "STA" -> {
                out.appendLine("                    state.ram[0x${hexValue(operand)}] = state.a.toByte()")
                out.appendLine("                    state.pc = 0x${hexValue(next)}")
            }
            "TAX", "TAY", "TXA", "TYA" -> {
                val (from, to) = when (i.mnemonic) {
                    "TAX" -> "a" to "x"; "TAY" -> "a" to "y"
                    "TXA" -> "x" to "a"; else -> "y" to "a"
                }
                out.appendLine("                    state.$to = setNZ(state, state.$from)")
                out.appendLine("                    state.pc = 0x${hexValue(next)}")
            }
            "INX", "DEX", "INY", "DEY" -> {
                val register = if (i.mnemonic.endsWith("X")) "x" else "y"
                val delta = if (i.mnemonic.startsWith("IN")) "+ 1" else "- 1"
                out.appendLine("                    state.$register = setNZ(state, state.$register $delta)")
                out.appendLine("                    state.pc = 0x${hexValue(next)}")
            }
            "CLC" -> out.appendLine("                    state.carry = false; state.pc = 0x${hexValue(next)}")
            "SEC" -> out.appendLine("                    state.carry = true; state.pc = 0x${hexValue(next)}")
            "CLV" -> out.appendLine("                    state.overflow = false; state.pc = 0x${hexValue(next)}")
            "JMP" -> out.appendLine("                    state.pc = 0x${hexValue(word(i))}")
            else -> {
                val target = branchTarget(i)
                val condition = when (i.mnemonic) {
                    "BCC" -> "!state.carry"; "BCS" -> "state.carry"
                    "BEQ" -> "state.zero"; "BNE" -> "!state.zero"
                    "BMI" -> "state.negative"; "BPL" -> "!state.negative"
                    "BVC" -> "!state.overflow"; else -> "state.overflow"
                }
                out.appendLine("                    state.pc = if ($condition) 0x${hexValue(target)} else 0x${hexValue(next)}")
            }
        }
    }

    private fun word(i: Mos6502Disassembler.Instruction) =
        i.bytes[1] or (i.bytes[2] shl 8)

    private fun branchTarget(i: Mos6502Disassembler.Instruction): Int {
        val raw = i.bytes[1]
        val displacement = if (raw < 0x80) raw else raw - 0x100
        return (i.address + 2 + displacement) and 0xFFFF
    }

    private fun hex(value: Int) = "$" + value.toString(16).uppercase().padStart(4, '0')
    private fun hexValue(value: Int) = value.toString(16).uppercase().padStart(4, '0')
}
