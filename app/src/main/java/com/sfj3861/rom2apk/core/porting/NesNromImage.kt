package com.sfj3861.rom2apk.core.porting

/**
 * Strict parser for the iNES/NES 2.0 container when the image uses NROM (mapper 0).
 * It extracts PRG/CHR regions only; it does not implement console hardware.
 */
object NesNromImage {
    data class Image(
        val prgRom: ByteArray,
        val chrRom: ByteArray,
        val hasChrRam: Boolean,
        val mirroring: Mirroring,
        val isNes2: Boolean,
        val trainer: ByteArray?
    )

    enum class Mirroring { HORIZONTAL, VERTICAL, FOUR_SCREEN }

    sealed interface Result {
        data class Supported(val image: Image) : Result
        data class Unsupported(val reason: String) : Result
        data class Invalid(val reason: String) : Result
    }

    fun parse(bytes: ByteArray): Result {
        if (bytes.size < 16) return Result.Invalid("Cabecera iNES incompleta.")
        if ((bytes[0].toInt() and 0xFF) != 0x4E ||
            (bytes[1].toInt() and 0xFF) != 0x45 ||
            (bytes[2].toInt() and 0xFF) != 0x53 ||
            (bytes[3].toInt() and 0xFF) != 0x1A
        ) return Result.Invalid("Firma iNES/NES 2.0 no válida.")

        val flags6 = u8(bytes, 6)
        val flags7 = u8(bytes, 7)
        val nes2 = (flags7 and 0x0C) == 0x08
        if (!nes2 && (flags7 and 0x0C) != 0) {
            return Result.Invalid("Cabecera iNES antigua con bits reservados inconsistentes.")
        }
        val mapper = (flags6 ushr 4) or (flags7 and 0xF0) or
            if (nes2) ((u8(bytes, 8) and 0x0F) shl 8) else 0
        if (mapper != 0) return Result.Unsupported("Mapper $mapper: la primera versión del backend solo contempla NROM (mapper 0).")
        if ((flags6 and 0x08) != 0) {
            return Result.Unsupported("NROM con four-screen mirroring requiere un subsistema PPU específico.")
        }

        val prgBanks = u8(bytes, 4)
        val chrBanks = u8(bytes, 5)
        if (prgBanks !in 1..2) return Result.Unsupported("NROM requiere 16 o 32 KiB de PRG-ROM.")
        if (chrBanks > 1) return Result.Unsupported("NROM inicial admite como máximo 8 KiB de CHR-ROM.")
        if (nes2 && ((u8(bytes, 9) and 0x0F) != 0 || (u8(bytes, 9) ushr 4) != 0)) {
            return Result.Unsupported("Codificación NES 2.0 de tamaño no lineal fuera del perfil NROM inicial.")
        }
        val trainerSize = if ((flags6 and 0x04) != 0) 512 else 0
        val prgSize = prgBanks * 16_384
        val chrSize = chrBanks * 8_192
        val required = 16L + trainerSize + prgSize + chrSize
        if (bytes.size.toLong() < required) return Result.Invalid("La imagen está truncada: faltan bytes PRG/CHR.")
        if (bytes.size.toLong() > required) {
            return Result.Unsupported("La imagen contiene datos extra tras PRG/CHR; se requiere identificar su formato adicional.")
        }
        var cursor = 16
        val trainer = if (trainerSize > 0) bytes.copyOfRange(cursor, cursor + trainerSize).also { cursor += trainerSize } else null
        val prg = bytes.copyOfRange(cursor, cursor + prgSize)
        cursor += prgSize
        val chr = if (chrSize > 0) bytes.copyOfRange(cursor, cursor + chrSize) else ByteArray(0)
        return Result.Supported(
            Image(
                prgRom = prg,
                chrRom = chr,
                hasChrRam = chrBanks == 0,
                mirroring = if ((flags6 and 0x01) != 0) Mirroring.VERTICAL else Mirroring.HORIZONTAL,
                isNes2 = nes2,
                trainer = trainer
            )
        )
    }

    private fun u8(bytes: ByteArray, index: Int) = bytes[index].toInt() and 0xFF
}
