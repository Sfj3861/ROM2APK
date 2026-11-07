package com.sfj3861.rom2apk.core.porting

import org.junit.Assert.*
import org.junit.Test

class NesNromImageTest {
    private fun rom(prgBanks: Int = 1, chrBanks: Int = 0, flags6: Int = 0): ByteArray {
        val size = 16 + prgBanks * 16_384 + chrBanks * 8_192
        return ByteArray(size).also {
            it[0] = 0x4E; it[1] = 0x45; it[2] = 0x53; it[3] = 0x1A
            it[4] = prgBanks.toByte(); it[5] = chrBanks.toByte(); it[6] = flags6.toByte()
        }
    }

    @Test
    fun parsesNromWithChrRamAndResetVector() {
        val bytes = rom()
        val prg = 16
        bytes[prg + 0x3FFC] = 0x00
        bytes[prg + 0x3FFD] = 0x80
        val result = NesNromImage.parse(bytes)
        assertTrue(result is NesNromImage.Result.Supported)
        val image = (result as NesNromImage.Result.Supported).image
        assertEquals(16_384, image.prgRom.size)
        assertTrue(image.hasChrRam)
        assertEquals(NesNromImage.Mirroring.HORIZONTAL, image.mirroring)
    }

    @Test
    fun rejectsTruncatedAndUnsupportedMapperImages() {
        val truncated = rom().copyOf(100)
        assertTrue(NesNromImage.parse(truncated) is NesNromImage.Result.Invalid)
        val mapperOne = rom(flags6 = 0x10)
        assertTrue(NesNromImage.parse(mapperOne) is NesNromImage.Result.Unsupported)
    }

    @Test
    fun rejectsPrgSizesOutsideNromProfile() {
        assertTrue(NesNromImage.parse(rom(prgBanks = 3)) is NesNromImage.Result.Unsupported)
    }

    @Test
    fun detectsTrainerAndExtractsPayloadAtCorrectOffset() {
        val bytes = rom(flags6 = 0x04)
        bytes[16] = 0x55
        val result = NesNromImage.parse(bytes) as NesNromImage.Result.Supported
        assertEquals(512, result.image.trainer?.size)
        assertEquals(0x55, result.image.trainer!![0].toInt() and 0xFF)
    }
}
