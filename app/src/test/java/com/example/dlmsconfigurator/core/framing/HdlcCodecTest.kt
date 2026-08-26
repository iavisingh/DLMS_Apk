package com.example.dlmsconfigurator.core.framing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HdlcCodecTest {
    @Test
    fun fcs16_matchesKnownDiscFrame() {
        val content = byteArrayOf(0xA0.toByte(), 0x07, 0x03, 0x61, 0x53)
        assertEquals(0x8165, HdlcCodec.fcs16(content))
    }

    @Test
    fun encodeDecode_roundTripsEscapedPayload() {
        val content = byteArrayOf(0xA0.toByte(), 0x07, 0x7E, 0x7D, 0x53)
        val encoded = HdlcCodec.encode(content)
        assertEquals(0x7E.toByte(), encoded.first())
        assertEquals(0x7E.toByte(), encoded.last())
        assertArrayEquals(content, HdlcCodec.decode(encoded))
    }

    @Test
    fun accumulator_reassemblesPartialReads() {
        val frame = HdlcCodec.encode(byteArrayOf(0xA0.toByte(), 0x07, 0x03, 0x61, 0x53))
        val accumulator = HdlcFrameAccumulator()
        assertEquals(0, accumulator.feed(frame.copyOfRange(0, 3)).size)
        val frames = accumulator.feed(frame.copyOfRange(3, frame.size))
        assertEquals(1, frames.size)
        assertArrayEquals(byteArrayOf(0xA0.toByte(), 0x07, 0x03, 0x61, 0x53), frames.first())
    }
}
