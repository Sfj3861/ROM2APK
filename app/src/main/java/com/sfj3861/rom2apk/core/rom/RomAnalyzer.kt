package com.sfj3861.rom2apk.core.rom

import java.io.InputStream

enum class RomCheckStatus { VALIDATED, NEEDS_SPECIAL_HANDLING, INCONCLUSIVE, INVALID }

data class RomAnalysis(
    val status: RomCheckStatus,
    val title: String,
    val details: String,
    val diagnosticCode: String = "ROM-GENERAL",
    val recommendation: String = "Comprueba que el archivo está completo y vuelve a analizarlo."
)

/** Conservative structural checks; a plausible header does not guarantee runtime compatibility. */
object RomAnalyzer {
    private const val MAX_HEADER_BYTES = 0x11020

    fun analyze(format: RomFormat, input: InputStream, knownSize: Long? = null): RomAnalysis {
        val header = input.use { readPrefix(it, MAX_HEADER_BYTES) }
        val size = knownSize ?: header.size.toLong()
        return when (format) {
            RomFormat.NES -> checkNes(header, size)
            RomFormat.SFC, RomFormat.SMC -> checkSnes(header, size)
            RomFormat.GB, RomFormat.GBC -> checkGameBoy(header, size)
            RomFormat.GBA -> checkGba(header, size)
            RomFormat.DS -> checkDs(header, size)
            RomFormat.THREE_DS -> checkThreeDs(header, size)
            RomFormat.THREE_DSX -> checkThreeDsX(header, size)
            RomFormat.CIA -> checkCia(header, size)
        }
    }

    private fun readPrefix(input: InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(limit)
        val buffer = ByteArray(8192)
        while (output.size() < limit) {
            val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (count <= 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun checkNes(data: ByteArray, size: Long): RomAnalysis {
        if (size < 17 || data.size < 16) return invalid("El archivo es demasiado pequeño para una cabecera NES.")
        if (!matches(data, 0, byteArrayOf(0x4E, 0x45, 0x53, 0x1A))) return invalid("No se encontró la firma iNES/NES 2.0.")
        val trainerBytes = if (u8(data, 6) and 0x04 != 0) 512L else 0L
        if (size <= 16L + trainerBytes) return invalid("La imagen no contiene datos de juego después de la cabecera.")
        return validated("Cabecera iNES/NES 2.0 reconocida. La identificación del mapper y la prueba de ejecución quedan pendientes.")
    }

    private fun checkSnes(data: ByteArray, size: Long): RomAnalysis {
        if (size < 0x8000 || data.size < 0x8000) return invalid("El archivo es demasiado pequeño para una imagen SNES convencional.")
        // Mapping modes and optional 512-byte copier headers vary. Treat this as a heuristic.
        val candidates = listOf(0x7FC0, 0xFFC0, 0x7FC0 + 512)
        val plausible = candidates.any { offset ->
            offset + 0x20 <= data.size &&
                u8(data, offset + 0x15) in 0x20..0x3F &&
                u16le(data, offset + 0x1C) + u16le(data, offset + 0x1E) == 0xFFFF
        }
        return if (plausible) validated("Se encontró una cabecera SNES plausible. El mapeo y la integridad completa aún requieren comprobaciones.")
        else inconclusive("Tamaño compatible, pero no se confirmó una cabecera SNES estándar. Puede usar un mapeo especial o una cabecera distinta.")
    }

    private fun checkGameBoy(data: ByteArray, size: Long): RomAnalysis {
        if (size < 0x150 || data.size < 0x150) return invalid("El archivo no contiene una cabecera Game Boy completa.")
        var checksum = 0
        for (index in 0x134..0x14C) checksum = (checksum - u8(data, index) - 1) and 0xFF
        if (checksum != u8(data, 0x14D)) return invalid("El checksum de cabecera Game Boy no coincide.")
        return validated("Checksum de cabecera Game Boy correcto.")
    }

    private fun checkGba(data: ByteArray, size: Long): RomAnalysis {
        if (size < 0xC0 || data.size < 0xC0) return invalid("El archivo no contiene una cabecera Game Boy Advance completa.")
        var sum = 0
        for (index in 0xA0..0xBC) sum = (sum + u8(data, index)) and 0xFF
        val expected = (0x100 - ((sum + 0x19) and 0xFF)) and 0xFF
        if (expected != u8(data, 0xBD)) return invalid("El checksum de cabecera GBA no coincide.")
        if (u8(data, 0xB2) != 0x96) return invalid("El identificador fijo de cabecera GBA no es válido.")
        return validated("Identificador fijo y checksum GBA correctos.")
    }

    private fun checkDs(data: ByteArray, size: Long): RomAnalysis {
        if (size < 0x160 || data.size < 0x160) return invalid("El archivo no contiene una cabecera Nintendo DS completa.")
        if (u16le(data, 0x15E) != crc16(data, 0, 0x15E)) return invalid("El CRC16 de la cabecera Nintendo DS no coincide.")
        return validated("CRC16 de cabecera Nintendo DS correcto.")
    }

    private fun checkThreeDs(data: ByteArray, size: Long): RomAnalysis {
        if (size < 0x200 || data.size < 0x104) return invalid("El archivo es demasiado pequeño para una imagen 3DS.")
        val magic = ascii(data, 0x100, 4)
        return if (magic == "NCSD" || magic == "NCCH") special("Cabecera $magic reconocida. Descifrado, claves y compatibilidad requieren comprobaciones adicionales.")
        else inconclusive("No se encontró una cabecera NCSD/NCCH visible. El archivo podría estar cifrado, truncado o no ser compatible.")
    }

    private fun checkThreeDsX(data: ByteArray, size: Long): RomAnalysis {
        if (size < 0x20 || data.size < 0x20) return invalid("El archivo 3DSX no contiene una cabecera completa.")
        return if (ascii(data, 0, 4) == "3DSX") special("Cabecera 3DSX reconocida. Es un ejecutable homebrew, no una ROM de cartucho convencional.")
        else invalid("La firma 3DSX no es válida.")
    }

    private fun checkCia(data: ByteArray, size: Long): RomAnalysis {
        if (size < 0x20 || data.size < 4) return invalid("El archivo CIA es demasiado pequeño.")
        val headerSize = u32le(data, 0)
        if (headerSize < 0x20 || headerSize > size) return invalid("El tamaño declarado de la cabecera CIA no es válido.")
        return special("Cabecera CIA plausible. Es necesario validar secciones, contenido, cifrado y requisitos de instalación.")
    }

    private fun crc16(data: ByteArray, start: Int, endExclusive: Int): Int {
        var crc = 0xFFFF
        for (index in start until endExclusive) {
            crc = crc xor u8(data, index)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
        }
        return crc and 0xFFFF
    }

    private fun u8(data: ByteArray, index: Int) = data[index].toInt() and 0xFF
    private fun u16le(data: ByteArray, index: Int) = u8(data, index) or (u8(data, index + 1) shl 8)
    private fun u32le(data: ByteArray, index: Int): Long =
        u8(data, index).toLong() or (u8(data, index + 1).toLong() shl 8) or
            (u8(data, index + 2).toLong() shl 16) or (u8(data, index + 3).toLong() shl 24)
    private fun matches(data: ByteArray, offset: Int, expected: ByteArray) =
        offset >= 0 && offset + expected.size <= data.size &&
            expected.indices.all { u8(data, offset + it) == (expected[it].toInt() and 0xFF) }
    private fun ascii(data: ByteArray, offset: Int, length: Int) =
        if (offset + length > data.size) "" else String(data, offset, length, Charsets.US_ASCII)

    private fun validated(details: String) = RomAnalysis(RomCheckStatus.VALIDATED, "Cabecera reconocida", details, "ROM-HEADER-OK", "Continúa con las comprobaciones del motor; esto no garantiza compatibilidad.")
    private fun special(details: String) = RomAnalysis(RomCheckStatus.NEEDS_SPECIAL_HANDLING, "Requiere comprobaciones adicionales", details, "ROM-SPECIAL", "Consulta los requisitos específicos del sistema y del motor antes de exportar.")
    private fun inconclusive(details: String) = RomAnalysis(RomCheckStatus.INCONCLUSIVE, "No concluyente", details, "ROM-INCONCLUSIVE", "Revisa el volcado y las cabeceras alternativas; no descartes el archivo solo por este resultado.")
    private fun invalid(details: String) = RomAnalysis(RomCheckStatus.INVALID, "Archivo no válido", details, "ROM-INVALID", "Comprueba que el archivo esté completo y selecciona una copia legítima sin daños.")
}
