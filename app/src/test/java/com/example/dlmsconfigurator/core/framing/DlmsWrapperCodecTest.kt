package com.example.dlmsconfigurator.core.framing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DlmsWrapperCodecTest {
    @Test
    fun wrapperDecode_waitsForCompletePayload() {
        val encoded = DlmsWrapperCodec.encode(
            payload = byteArrayOf(0x60, 0x01, 0x02),
            sourceWPort = 1,
            destinationWPort = 16
        )
        val buffer = encoded.take(9).toMutableList()
        assertNull(DlmsWrapperCodec.tryDecode(buffer))
        buffer.addAll(encoded.drop(9))

        val frame = DlmsWrapperCodec.tryDecode(buffer)!!
        assertEquals(1, frame.version)
        assertEquals(1, frame.sourceWPort)
        assertEquals(16, frame.destinationWPort)
        assertArrayEquals(byteArrayOf(0x60, 0x01, 0x02), frame.payload)
        assertEquals(0, buffer.size)
    }
}
