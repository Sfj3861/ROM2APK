package com.sfj3861.rom2apk.core.porting

import com.sfj3861.rom2apk.core.rom.RomAnalysis
import com.sfj3861.rom2apk.core.rom.RomCheckStatus

/** Combines strict NROM container parsing with conservative static code discovery. */
object NesNromPreflight {
    fun analyze(bytes: ByteArray): RomAnalysis {
        return when (val parsed = NesNromImage.parse(bytes)) {
            is NesNromImage.Result.Invalid -> RomAnalysis(
                RomCheckStatus.INVALID,
                "Imagen NES no válida",
                parsed.reason,
                "NES-NROM-INVALID",
                "Comprueba la cabecera iNES y que el archivo esté completo."
            )
            is NesNromImage.Result.Unsupported -> RomAnalysis(
                RomCheckStatus.NEEDS_SPECIAL_HANDLING,
                "NES detectada, backend inicial no compatible",
                parsed.reason,
                "NES-NROM-UNSUPPORTED",
                "Este perfil no se puede traducir todavía; no se generará un APK falso."
            )
            is NesNromImage.Result.Supported -> {
                val image = parsed.image
                val code = NesNromCodeAnalyzer.analyze(image.prgRom)
                val cpuTranslation = if (code.analysisComplete) Nes6502StaticSourceEmitter.emit(code) else null
                val summary = buildString {
                    append("NROM reconocido: ")
                    append(if (image.prgRom.size == 16_384) "16 KiB PRG" else "32 KiB PRG")
                    append(", ")
                    append(if (image.hasChrRam) "CHR-RAM" else "8 KiB CHR-ROM")
                    append(", ")
                    append(image.mirroring.name.lowercase().replace('_', ' '))
                    append(". Vector RESET: ")
                    append(code.resetVector?.let { "$" + it.toString(16).uppercase().padStart(4, '0') } ?: "desconocido")
                    append(". Instrucciones alcanzables: ")
                    append(code.instructions.size)
                    append(".")
                    if (cpuTranslation?.success == true) {
                        append(" Instrucciones convertibles al subconjunto Kotlin estático: ")
                        append(cpuTranslation.translatedInstructionCount)
                        append(".")
                    } else if (cpuTranslation != null) {
                        append(" El traductor estático parcial no cubre todo el flujo: ")
                        append(cpuTranslation.diagnostics.take(3).joinToString(" "))
                    }
                    if (code.diagnostics.isNotEmpty()) {
                        append(" Diagnósticos: ")
                        append(code.diagnostics.take(4).joinToString(" "))
                    }
                }
                if (code.analysisComplete) {
                    RomAnalysis(
                        RomCheckStatus.VALIDATED,
                        "NROM y flujo de código analizados",
                        "$summary El traductor solo genera código CPU para un subconjunto limitado. Esto no es un juego ejecutable: faltan PPU, APU, pila/retornos completos, interrupciones, temporización, bus y semántica de hardware.",
                        "NES-NROM-ANALYZED",
                        "El siguiente hito es ampliar las instrucciones traducibles y construir los subsistemas nativos de vídeo, audio y hardware antes de habilitar la exportación de juegos."
                    )
                } else {
                    RomAnalysis(
                        RomCheckStatus.NEEDS_SPECIAL_HANDLING,
                        "NROM reconocido; análisis incompleto",
                        "$summary No se puede afirmar que el programa completo sea traducible.",
                        "NES-NROM-PARTIAL",
                        "Resuelve los saltos dinámicos y opcodes no compatibles antes de generar código nativo."
                    )
                }
            }
        }
    }
}
