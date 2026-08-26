package com.example.dlmsconfigurator.core.framing

/**
 * DLMS/COSEM wrapper framing for TCP/IP transport.
 *
 * Wrapper APDUs are length-prefixed:
 *   version(2) | source wPort(2) | destination wPort(2) | payload length(2) | payload
 */
object DlmsWrapperCodec {
    const val HEADER_SIZE = 8
    const val DEFAULT_VERSION = 0x0001

    data class WrapperFrame(
        val version: Int,
        val sourceWPort: Int,
        val destinationWPort: Int,
        val payload: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WrapperFrame) return false
            return version == other.version &&
                sourceWPort == other.sourceWPort &&
                destinationWPort == other.destinationWPort &&
                payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = version
            result = 31 * result + sourceWPort
            result = 31 * result + destinationWPort
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    fun encode(
        payload: ByteArray,
        sourceWPort: Int,
        destinationWPort: Int,
        version: Int = DEFAULT_VERSION
    ): ByteArray {
        require(payload.size <= 0xFFFF) { "Wrapper payload too large: ${payload.size}" }
        return byteArrayOf(
            (version ushr 8).toByte(),
            version.toByte(),
            (sourceWPort ushr 8).toByte(),
            sourceWPort.toByte(),
            (destinationWPort ushr 8).toByte(),
            destinationWPort.toByte(),
            (payload.size ushr 8).toByte(),
            payload.size.toByte()
        ) + payload
    }

    fun tryDecode(buffer: MutableList<Byte>): WrapperFrame? {
        if (buffer.size < HEADER_SIZE) return null

        val length = u16(buffer[6], buffer[7])
        val total = HEADER_SIZE + length
        if (buffer.size < total) return null

        val headerAndPayload = buffer.take(total)
        repeat(total) { buffer.removeAt(0) }

        return WrapperFrame(
            version = u16(headerAndPayload[0], headerAndPayload[1]),
            sourceWPort = u16(headerAndPayload[2], headerAndPayload[3]),
            destinationWPort = u16(headerAndPayload[4], headerAndPayload[5]),
            payload = headerAndPayload.drop(HEADER_SIZE).toByteArray()
        )
    }

    private fun u16(msb: Byte, lsb: Byte): Int {
        return ((msb.toInt() and 0xFF) shl 8) or (lsb.toInt() and 0xFF)
    }
}
