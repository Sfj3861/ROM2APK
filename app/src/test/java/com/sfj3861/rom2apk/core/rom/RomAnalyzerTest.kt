package com.sfj3861.rom2apk.core.rom

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class RomAnalyzerTest {
    @Test fun recognizesNesHeader() {
        val bytes = ByteArray(16 + 16_384)
        bytes[0] = 0x4E; bytes[1] = 0x45; bytes[2] = 0x53; bytes[3] = 0x1A
        bytes[4] = 1
        assertEquals(RomCheckStatus.VALIDATED,
            RomAnalyzer.analyze(RomFormat.NES, ByteArrayInputStream(bytes), bytes.size.toLong()).status)
    }

    @Test fun rejectsNesFileWithWrongMagic() {
        val bytes = ByteArray(64)
        assertEquals(RomCheckStatus.INVALID,
            RomAnalyzer.analyze(RomFormat.NES, ByteArrayInputStream(bytes), bytes.size.toLong()).status)
    }

    @Test fun recognizesThreeDsxAsSpecialHandling() {
        val bytes = ByteArray(64)
        "3DSX".toByteArray().copyInto(bytes)
        assertEquals(RomCheckStatus.NEEDS_SPECIAL_HANDLING,
            RomAnalyzer.analyze(RomFormat.THREE_DSX, ByteArrayInputStream(bytes), bytes.size.toLong()).status)
    }

    @Test fun invalidResultsIncludeDiagnosticAndRecommendation() {
        val result = RomAnalyzer.analyze(RomFormat.NES, ByteArrayInputStream(ByteArray(64)), 64)
        org.junit.Assert.assertTrue(result.diagnosticCode.isNotBlank())
        org.junit.Assert.assertTrue(result.recommendation.isNotBlank())
    }

    @Test fun acceptsUppercaseFileExtensions() {
        assertEquals(RomFormat.GBA, RomFormat.fromFileName("MY_GAME.GBA"))
    }

    @Test fun validatesGameBoyHeaderChecksum() {
        val bytes = ByteArray(0x150)
        bytes[0x14D] = 0xE7.toByte()
        assertEquals(RomCheckStatus.VALIDATED, RomAnalyzer.analyze(RomFormat.GB, ByteArrayInputStream(bytes), bytes.size.toLong()).status)
    }

    @Test fun validatesGbaHeaderChecksumAndFixedByte() {
        val bytes = ByteArray(0xC0)
        bytes[0xB2] = 0x96.toByte()
        bytes[0xBD] = 0x51.toByte()
        assertEquals(RomCheckStatus.VALIDATED, RomAnalyzer.analyze(RomFormat.GBA, ByteArrayInputStream(bytes), bytes.size.toLong()).status)
    }

    @Test fun detectsTruncatedInesPayload() {
        val bytes = ByteArray(32)
        bytes[0] = 0x4E; bytes[1] = 0x45; bytes[2] = 0x53; bytes[3] = 0x1A
        bytes[4] = 1
        assertEquals(RomCheckStatus.INVALID, RomAnalyzer.analyze(RomFormat.NES, ByteArrayInputStream(bytes), bytes.size.toLong()).status)
    }

    @Test fun recognizesSnesHeaderWithComplementaryChecksum() {
        val bytes = ByteArray(0x8000)
        val offset = 0x7FC0
        bytes[offset + 0x15] = 0x20
        bytes[offset + 0x1C] = 0x34
        bytes[offset + 0x1D] = 0x12
        bytes[offset + 0x1E] = 0xCB.toByte()
        bytes[offset + 0x1F] = 0xED.toByte()
        assertEquals(RomCheckStatus.VALIDATED, RomAnalyzer.analyze(RomFormat.SFC, ByteArrayInputStream(bytes), bytes.size.toLong()).status)
    }

    @Test fun rejectsTruncatedGameBoyHeader() {
        val bytes = ByteArray(32)
        assertEquals(RomCheckStatus.INVALID,
            RomAnalyzer.analyze(RomFormat.GB, ByteArrayInputStream(bytes), bytes.size.toLong()).status)
    }
}
