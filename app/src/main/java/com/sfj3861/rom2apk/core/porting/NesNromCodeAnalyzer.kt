package com.sfj3861.rom2apk.core.porting

/**
 * Conservative reachable-code analysis for NROM PRG space.
 *
 * This is not a complete NES recompiler: indirect jumps, undocumented opcodes,
 * PPU/APU/MMIO behavior, interrupts and code/data ambiguity are reported.
 */
object NesNromCodeAnalyzer {
    data class Result(
        val resetVector: Int?,
        val instructions: List<Mos6502Disassembler.Instruction>,
        val reachableAddresses: Set<Int>,
        val diagnostics: List<String>,
        val analysisComplete: Boolean
    )

    private val conditionalBranches = setOf("BCC", "BCS", "BEQ", "BMI", "BNE", "BPL", "BVC", "BVS")

    fun analyze(prgRom: ByteArray, maxInstructions: Int = 100_000): Result {
        require(prgRom.size == 16_384 || prgRom.size == 32_768) {
            "NROM PRG-ROM debe medir 16 o 32 KiB."
        }
        require(maxInstructions > 0) { "El límite de instrucciones debe ser positivo." }

        fun cpuToOffset(address: Int): Int? {
            if (address !in 0x8000..0xFFFF) return null
            val raw = address - 0x8000
            return if (prgRom.size == 16_384) raw and 0x3FFF else raw
        }

        val resetOffset = cpuToOffset(0xFFFC)!!
        val resetVector = (prgRom[resetOffset].toInt() and 0xFF) or
            ((prgRom[resetOffset + 1].toInt() and 0xFF) shl 8)
        val diagnostics = linkedSetOf<String>()
        val decoded = linkedMapOf<Int, Mos6502Disassembler.Instruction>()
        val pending = ArrayDeque<Int>()
        val queued = hashSetOf<Int>()
        val visited = hashSetOf<Int>()
        var complete = true

        fun enqueue(address: Int, reason: String) {
            if (cpuToOffset(address) == null) {
                diagnostics += "$reason apunta fuera de PRG-ROM: ${hex(address)}."
                complete = false
            } else if (visited.size + pending.size < maxInstructions && queued.add(address)) {
                pending.addLast(address)
            } else if (visited.size + pending.size >= maxInstructions) {
                diagnostics += "Se alcanzó el límite de $maxInstructions instrucciones."
                complete = false
            }
        }

        enqueue(resetVector, "Vector RESET")
        while (pending.isNotEmpty() && visited.size < maxInstructions) {
            val address = pending.removeFirst()
            if (!visited.add(address)) continue
            if (cpuToOffset(address) == null) continue
            // Read through the NROM 16 KiB mirror boundary using CPU address mapping,
            // rather than treating the physical PRG array end as an instruction boundary.
            val instructionWindow = ByteArray(3)
            var available = 0
            while (available < instructionWindow.size) {
                val mappedOffset = cpuToOffset((address + available) and 0xFFFF) ?: break
                instructionWindow[available] = prgRom[mappedOffset]
                available++
            }
            val instruction = Mos6502Disassembler.disassemble(
                instructionWindow.copyOf(available),
                startAddress = address,
                maxInstructions = 1
            ).firstOrNull() ?: continue
            decoded[address] = instruction

            if (!instruction.isKnownOpcode || instruction.operand == "<truncated>") {
                diagnostics += "Opcode no traducible o incompleto en ${hex(address)} (${hex(instruction.opcode)})."
                complete = false
                continue
            }
            val next = instruction.nextAddress
            when {
                instruction.mnemonic in conditionalBranches -> {
                    val displacement = instruction.bytes[1]
                    val signed = if (displacement < 0x80) displacement else displacement - 0x100
                    enqueue((address + 2 + signed) and 0xFFFF, "Salto condicional")
                    enqueue(next, "Continuación")
                }
                instruction.mnemonic == "JMP" && instruction.mode == Mos6502Disassembler.AddressMode.ABSOLUTE -> {
                    val target = instruction.bytes[1] or (instruction.bytes[2] shl 8)
                    enqueue(target, "JMP")
                }
                instruction.mnemonic == "JMP" && instruction.mode == Mos6502Disassembler.AddressMode.INDIRECT -> {
                    diagnostics += "JMP indirecto en ${hex(address)}: destino dinámico, requiere análisis de memoria."
                    complete = false
                }
                instruction.mnemonic == "JSR" -> {
                    val target = instruction.bytes[1] or (instruction.bytes[2] shl 8)
                    enqueue(target, "JSR")
                    enqueue(next, "Retorno potencial de JSR")
                }
                instruction.mnemonic == "RTS" -> Unit
                instruction.mnemonic == "BRK" -> {
                    val vectorOffset = cpuToOffset(0xFFFE)!!
                    val target = (prgRom[vectorOffset].toInt() and 0xFF) or
                        ((prgRom[vectorOffset + 1].toInt() and 0xFF) shl 8)
                    diagnostics += "BRK en ${hex(address)}: la ejecución por interrupciones no está modelada."
                    complete = false
                    enqueue(target, "Vector IRQ/BRK")
                }
                instruction.mnemonic == "RTI" -> {
                    diagnostics += "RTI en ${hex(address)}: retorno de interrupción dependiente de la pila."
                    complete = false
                }
                else -> enqueue(next, "Flujo secuencial")
            }
        }
        if (pending.isNotEmpty()) {
            diagnostics += "El análisis terminó antes de recorrer todas las rutas pendientes."
            complete = false
        }
        if (decoded.isEmpty()) {
            diagnostics += "El vector RESET no apunta a una dirección de PRG-ROM."
            complete = false
        }
        return Result(
            resetVector = resetVector,
            instructions = decoded.values.sortedBy { it.address },
            reachableAddresses = decoded.keys,
            diagnostics = diagnostics.toList(),
            analysisComplete = complete
        )
    }

    private fun hex(value: Int) = "$" + value.toString(16).uppercase().padStart(4, '0')
}
