package com.sfj3861.rom2apk.core.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RomFormatTest {
    @Test
    fun recognizesAllRequestedExtensionsCaseInsensitively() {
        val expected = mapOf(
            "game.nes" to RomFormat.NES,
            "game.sfc" to RomFormat.SFC,
            "game.smc" to RomFormat.SMC,
            "game.gb" to RomFormat.GB,
            "game.gbc" to RomFormat.GBC,
            "game.gba" to RomFormat.GBA,
            "game.ds" to RomFormat.DS,
            "game.3ds" to RomFormat.THREE_DS,
            "game.3dsx" to RomFormat.THREE_DSX,
            "game.cia" to RomFormat.CIA,
            "GAME.GBA" to RomFormat.GBA
        )

        expected.forEach { (name, format) ->
            assertEquals(name, format, RomFormat.fromFileName(name))
        }
    }

    @Test
    fun rejectsUnknownOrMissingExtensions() {
        assertNull(RomFormat.fromFileName("game.zip"))
        assertNull(RomFormat.fromFileName("game"))
        assertNull(RomFormat.fromFileName("game."))
    }
}
