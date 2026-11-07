package com.sfj3861.rom2apk.core.porting

/**
 * Header and cartridge-layout preflight for standard SNES LoROM/HiROM images.
 * This is not a SNES CPU/PPU/APU implementation and does not claim to port a game.
 */
object SnesCartridgeImage {
    enum class Mapping { LOROM, HIROM, EXHIROM }

    data class Header(
        val title: String,
        val mapping: Mapping,
        val mapMode: Int,
        val cartridgeType: Int,
        val romSizeCode: Int,
        val ramSizeCode: Int,
        val regionCode: Int,
        val checksum: Int,
        val checksumComplement: Int,
        val resetVector: Int,
        val headerOffset: Int,
        val copierHeaderBytes: Int
    )

    sealed interface Result {
        data class Supported(val header: Header, val rom: ByteArray) : Result
        data class Unsupported(val reason: String) : Result
        data class Invalid(val reason: String) : Result
    }

    fun parse(input: ByteArray): Result {
        if (input.size < 0x8000) return Result.Invalid("La imagen SNES es demasiado pequeña para contener una ROM válida.")
        val copier = if (input.size % 0x8000 == 512) 512 else 0
        val rom = if (copier == 512) input.copyOfRange(512, input.size) else input
        if (rom.size < 0x8000) return Result.Invalid("La imagen no contiene un banco ROM completo.")

        val candidates = listOf(
            0x7FC0 to Mapping.LOROM,
            0xFFC0 to Mapping.HIROM,
            0x40FFC0 to Mapping.EXHIROM
        ).filter { (offset, _) -> offset + 0x40 <= rom.size }

        val ranked = candidates.mapNotNull { (offset, mapping) ->
            val mapMode = u8(rom, offset + 0x15)
            val checksum = u16(rom, offset + 0x1E)
            val complement = u16(rom, offset + 0x1C)
            val reset = u16(rom, offset + 0x3C)
            val score = (if (checksum xor complement == 0xFFFF) 4 else 0) +
                (if (reset >= 0x8000) 2 else 0) +
                (if ((mapMode and 0x0F) == expectedMapNibble(mapping)) 3 else 0) +
                (if (rom.copyOfRange(offset, offset + 21).any { it.toInt() != 0 && it.toInt() != 0xFF }) 1 else 0)
            Triple(offset, mapping, score)
        }.sortedByDescending { it.third }

        val best = ranked.firstOrNull()
            ?: return Result.Invalid("No se encuentra una cabecera LoROM/HiROM/ExHiROM completa.")
        if (best.third < 5) return Result.Unsupported("No hay una cabecera SNES suficientemente fiable para determinar el mapa de memoria.")
        val offset = best.first
        val map = best.second
        val checksum = u16(rom, offset + 0x1E)
        val complement = u16(rom, offset + 0x1C)
        val reset = u16(rom, offset + 0x3C)
        val title = rom.copyOfRange(offset, offset + 21)
            .map { it.toInt().toChar() }.joinToString("")
            .trim('\u0000', ' ', '\u007F')
        val header = Header(
            title = title.ifBlank { "SNES game" },
            mapping = map,
            mapMode = u8(rom, offset + 0x15),
            cartridgeType = u8(rom, offset + 0x16),
            romSizeCode = u8(rom, offset + 0x17),
            ramSizeCode = u8(rom, offset + 0x18),
            regionCode = u8(rom, offset + 0x19),
            checksum = checksum,
            checksumComplement = complement,
            resetVector = reset,
            headerOffset = offset,
            copierHeaderBytes = copier
        )
        if (reset < 0x8000) return Result.Unsupported("El vector RESET no apunta al área ROM habitual; se requiere análisis de hardware adicional.")
        if (checksum xor complement != 0xFFFF) return Result.Unsupported("La cabecera está localizada, pero el complemento/checksum no coincide.")
        if (header.cartridgeType in setOf(0x03, 0x05, 0x06, 0x13, 0x14, 0x15, 0x1A, 0x1B)) {
            return Result.Unsupported("El tipo de cartucho declara coprocesador o hardware especial; requiere un backend específico.")
        }
        return Result.Supported(header, rom)
    }

    private fun expectedMapNibble(mapping: Mapping): Int = when (mapping) {
        Mapping.LOROM -> 0x0
        Mapping.HIROM -> 0x1
        Mapping.EXHIROM -> 0x5
    }

    private fun u8(bytes: ByteArray, offset: Int) = bytes[offset].toInt() and 0xFF
    private fun u16(bytes: ByteArray, offset: Int) = u8(bytes, offset) or (u8(bytes, offset + 1) shl 8)
}
