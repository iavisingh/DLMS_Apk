package com.example.dlmsconfigurator.core.framing

/**
 * HDLC framing helpers for serial DLMS links.
 *
 * The codec keeps message boundary handling out of raw serial reads:
 * callers feed arbitrary byte chunks into [HdlcFrameAccumulator] and receive
 * complete CRC-valid frames when enough bytes have arrived.
 */
object HdlcCodec {
    const val FLAG: Byte = 0x7E
    const val ESCAPE: Byte = 0x7D
    private const val ESCAPE_XOR = 0x20

    fun fcs16(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            var x = b.toInt() and 0xFF
            repeat(8) {
                crc = if (((crc xor x) and 1) != 0) {
                    (crc ushr 1) xor 0x8408
                } else {
                    crc ushr 1
                }
                x = x ushr 1
            }
        }
        return crc xor 0xFFFF
    }

    fun encode(contentWithoutFlagsOrFcs: ByteArray): ByteArray {
        val crc = fcs16(contentWithoutFlagsOrFcs)
        val body = contentWithoutFlagsOrFcs + byteArrayOf(
            (crc and 0xFF).toByte(),
            ((crc ushr 8) and 0xFF).toByte()
        )
        return byteArrayOf(FLAG) + escape(body) + byteArrayOf(FLAG)
    }

    fun decode(frameWithFlags: ByteArray): ByteArray {
        require(frameWithFlags.size >= 4) { "HDLC frame too short" }
        require(frameWithFlags.first() == FLAG && frameWithFlags.last() == FLAG) {
            "HDLC frame must start and end with 0x7E"
        }
        val unescaped = unescape(frameWithFlags.copyOfRange(1, frameWithFlags.lastIndex))
        require(unescaped.size >= 3) { "HDLC frame body too short" }
        val content = unescaped.copyOfRange(0, unescaped.size - 2)
        val received = (unescaped[unescaped.lastIndex - 1].toInt() and 0xFF) or
            ((unescaped[unescaped.lastIndex].toInt() and 0xFF) shl 8)
        val expected = fcs16(content)
        require(received == expected) {
            "Invalid HDLC FCS. expected=${expected.toString(16)}, received=${received.toString(16)}"
        }
        return content
    }

    private fun escape(bytes: ByteArray): ByteArray {
        val out = ArrayList<Byte>(bytes.size)
        bytes.forEach { b ->
            if (b == FLAG || b == ESCAPE) {
                out += ESCAPE
                out += (b.toInt() xor ESCAPE_XOR).toByte()
            } else {
                out += b
            }
        }
        return out.toByteArray()
    }

    private fun unescape(bytes: ByteArray): ByteArray {
        val out = ArrayList<Byte>(bytes.size)
        var escaping = false
        bytes.forEach { b ->
            if (escaping) {
                out += (b.toInt() xor ESCAPE_XOR).toByte()
                escaping = false
            } else if (b == ESCAPE) {
                escaping = true
            } else {
                out += b
            }
        }
        require(!escaping) { "Dangling HDLC escape byte" }
        return out.toByteArray()
    }
}

class HdlcFrameAccumulator {
    private val buffer = ArrayList<Byte>()
    private var inFrame = false

    fun feed(bytes: ByteArray): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        bytes.forEach { b ->
            if (b == HdlcCodec.FLAG) {
                if (inFrame && buffer.isNotEmpty()) {
                    val raw = byteArrayOf(HdlcCodec.FLAG) + buffer.toByteArray() + byteArrayOf(HdlcCodec.FLAG)
                    runCatching { HdlcCodec.decode(raw) }.getOrNull()?.let(frames::add)
                    buffer.clear()
                }
                inFrame = true
            } else if (inFrame) {
                buffer += b
            }
        }
        return frames
    }

    fun clear() {
        buffer.clear()
        inFrame = false
    }
}
