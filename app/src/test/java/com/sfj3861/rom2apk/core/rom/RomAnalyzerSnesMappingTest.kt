package com.sfj3861.rom2apk.core.rom

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class RomAnalyzerSnesMappingTest {
    private fun image(headerOffset: Int, mapMode: Int, copierHeader: Boolean = false): ByteArray {
        val payload = ByteArray(0x10000)
        payload[headerOffset + 0x15] = mapMode.toByte()
        payload[headerOffset + 0x1C] = 0xFF.toByte()
        payload[headerOffset + 0x1D] = 0xFF.toByte()
        payload[headerOffset + 0x1E] = 0
        payload[headerOffset + 0x1F] = 0
        return if (copierHeader) ByteArray(512) + payload else payload
    }

    @Test fun recognizesHiRomHeader() {
        val result = RomAnalyzer.analyze(
            RomFormat.SFC, ByteArrayInputStream(image(0xFFC0, 0x21)), 0x10000
        )
        assertEquals(RomCheckStatus.VALIDATED, result.status)
    }

    @Test fun recognizesHiRomWithCopierHeader() {
        val result = RomAnalyzer.analyze(
            RomFormat.SMC, ByteArrayInputStream(image(0xFFC0, 0x21, true)), 0x10200
        )
        assertEquals(RomCheckStatus.VALIDATED, result.status)
    }
}
