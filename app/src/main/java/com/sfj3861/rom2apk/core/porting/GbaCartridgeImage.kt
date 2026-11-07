package com.sfj3861.rom2apk.core.porting

/**
 * GBA cartridge header and executable-section preflight.
 * ARM/Thumb instruction translation is a separate stage; this parser never
 * interprets the ROM as an emulator would.
 */
object GbaCartridgeImage {
    data class Header(
        val title: String,
        val gameCode: String,
        val makerCode: String,
        val fixedValue: Int,
        val headerComplement: Int,
        val entryPoint: Int,
        val romSize: Int
    )

    sealed interface Result {
        data class Supported(val header: Header, val rom: ByteArray) : Result
        data class Invalid(val reason: String) : Result
        data class Unsupported(val reason: String) : Result
    }

    fun parse(bytes: ByteArray): Result {
        if (bytes.size < 0xC0) return Result.Invalid("Cabecera GBA incompleta: se necesitan al menos 192 bytes.")
        if (u8(bytes, 0xB2) != 0x96) return Result.Invalid("Byte fijo de cabecera GBA incorrecto.")
        val expected = (0xE7 - (0xA0..0xBC).sumOf { u8(bytes, it) }) and 0xFF
        if (expected != u8(bytes, 0xBD)) return Result.Invalid("Checksum de cabecera GBA incorrecto.")
        val entry = u32(bytes, 0)
        if ((entry and 0xFF000000.toInt()) != 0xEA000000.toInt()) {
            return Result.Unsupported("El punto de entrada no contiene el branch ARM habitual del arranque GBA.")
        }
        val title = ascii(bytes, 0xA0, 12)
        val gameCode = ascii(bytes, 0xAC, 4)
        val maker = ascii(bytes, 0xB0, 2)
        if (gameCode.any { !it.isLetterOrDigit() } || maker.any { !it.isLetterOrDigit() }) {
            return Result.Unsupported("Los códigos de juego/fabricante contienen caracteres no estándar.")
        }
        return Result.Supported(
            Header(title, gameCode, maker, u8(bytes, 0xB2), u8(bytes, 0xBD), entry, bytes.size),
            bytes.copyOf()
        )
    }

    private fun ascii(bytes: ByteArray, start: Int, length: Int): String =
        bytes.copyOfRange(start, start + length).map { (it.toInt() and 0xFF).toChar() }
            .joinToString("").trim('\u0000', ' ')

    private fun u8(bytes: ByteArray, offset: Int) = bytes[offset].toInt() and 0xFF
    private fun u32(bytes: ByteArray, offset: Int): Int =
        u8(bytes, offset) or (u8(bytes, offset + 1) shl 8) or
            (u8(bytes, offset + 2) shl 16) or (u8(bytes, offset + 3) shl 24)
}
