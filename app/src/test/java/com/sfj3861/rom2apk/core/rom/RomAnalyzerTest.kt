package com.sfj3861.rom2apk.core.rom

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class RomAnalyzerTest {
    @Test fun recognizesNesHeader() {
        val bytes = ByteArray(32)
        bytes[0] = 0x4E; bytes[1] = 0x45; bytes[2] = 0x53; bytes[3] = 0x1A
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

    @Test fun invalidResultsIncludeDiagnosticAndRecommendation() {\n        val result = RomAnalyzer.analyze(RomFormat.NES, ByteArrayInputStream(ByteArray(64)), 64)\n        org.junit.Assert.assertTrue(result.diagnosticCode.isNotBlank())\n        org.junit.Assert.assertTrue(result.recommendation.isNotBlank())\n    }\n\n    @Test fun acceptsUppercaseFileExtensions() {\n        assertEquals(RomFormat.GBA, RomFormat.fromFileName("MY_GAME.GBA"))\n    }\n\n    @Test fun rejectsTruncatedGameBoyHeader() {
        val bytes = ByteArray(32)
        assertEquals(RomCheckStatus.INVALID,
            RomAnalyzer.analyze(RomFormat.GB, ByteArrayInputStream(bytes), bytes.size.toLong()).status)
    }
}
