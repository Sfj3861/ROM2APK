package com.sfj3861.rom2apk.core.porting

import org.junit.Assert.*
import org.junit.Test

class AdditionalCartridgeImageTest {
    @Test
    fun parsesGbaHeaderAndValidatesChecksum() {
        val rom = ByteArray(0xC0)
        rom[0] = 0x00
        rom[1] = 0x00
        rom[2] = 0x00
        rom[3] = 0xEA.toByte()
        "TESTGAME1234".forEachIndexed { i, c -> rom[0xA0 + i] = c.code.toByte() }
        "ABCD".forEachIndexed { i, c -> rom[0xAC + i] = c.code.toByte() }
        "01".forEachIndexed { i, c -> rom[0xB0 + i] = c.code.toByte() }
        rom[0xB2] = 0x96.toByte()
        val sum = (0xA0..0xBC).sumOf { rom[it].toInt() and 0xFF }
        rom[0xBD] = ((0x19 - sum) and 0xFF).toByte()

        val result = GbaCartridgeImage.parse(rom)
        assertTrue(result is GbaCartridgeImage.Result.Supported)
        assertEquals("ABCD", (result as GbaCartridgeImage.Result.Supported).header.gameCode)
    }

    @Test
    fun rejectsBadGbaFixedByte() {
        val rom = ByteArray(0xC0)
        assertTrue(GbaCartridgeImage.parse(rom) is GbaCartridgeImage.Result.Invalid)
    }

    @Test
    fun detectsSnesLoRomHeaderAndCopierHeader() {
        val rom = ByteArray(0x8000 + 512)
        val h = 512 + 0x7FC0
        "TEST SNES GAME".forEachIndexed { i, c -> rom[h + i] = c.code.toByte() }
        rom[h + 0x15] = 0x20
        rom[h + 0x16] = 0
        rom[h + 0x1C] = 0xFF.toByte()
        rom[h + 0x1D] = 0xFF.toByte()
        rom[h + 0x1E] = 0
        rom[h + 0x1F] = 0
        rom[h + 0x3C] = 0
        rom[h + 0x3D] = 0x80.toByte()

        val result = SnesCartridgeImage.parse(rom)
        assertTrue(result is SnesCartridgeImage.Result.Supported)
        val header = (result as SnesCartridgeImage.Result.Supported).header
        assertEquals(SnesCartridgeImage.Mapping.LOROM, header.mapping)
        assertEquals(512, header.copierHeaderBytes)
    }

    @Test
    fun rejectsTruncatedSnesImage() {
        assertTrue(SnesCartridgeImage.parse(ByteArray(0x100)) is SnesCartridgeImage.Result.Invalid)
    }
}
