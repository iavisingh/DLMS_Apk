package com.example.dlmsconfigurator.core.transport

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class TcpTransport(
    private val host: String,
    private val port: Int
) : DlmsTransport {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var pendingInput = ByteArray(0)

    override fun open() {
        if (socket != null) return
        
        val cleanHost = normalizeHost(host)
        if (isLinkLocalIpv6WithoutScope(cleanHost)) {
            throw IOException("IPv6 link-local TCP host requires an interface scope, for example $cleanHost%wlan0")
        }
        
        val newSocket = Socket()
        try {
            // Use InetAddress.getByName to ensure we handle IPv4/v6 resolution correctly
            val address = InetAddress.getByName(cleanHost)
            newSocket.connect(InetSocketAddress(address, port), 5000)
            socket = newSocket
            inputStream = newSocket.getInputStream()
            outputStream = newSocket.getOutputStream()
        } catch (e: Exception) {
            try { newSocket.close() } catch (ignored: Exception) {}
            throw IOException("TCP connection to $host:$port failed: ${e.message}", e)
        }
    }

    private fun normalizeHost(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length > 2) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
    }

    private fun isLinkLocalIpv6WithoutScope(value: String): Boolean {
        val addressText = value.substringBefore('%')
        return !value.contains("%") && addressText.startsWith("fe80:", ignoreCase = true)
    }

    override fun close() {
        try { inputStream?.close() } catch (ignored: Exception) {}
        inputStream = null
        try { outputStream?.close() } catch (ignored: Exception) {}
        outputStream = null
        try { socket?.close() } catch (ignored: Exception) {}
        socket = null
        pendingInput = ByteArray(0)
    }

    override fun isOpen(): Boolean {
        return socket?.isConnected == true && socket?.isClosed == false
    }

    override fun write(data: ByteArray) {
        val stream = outputStream ?: throw IOException("Socket not open")
        stream.write(data)
        stream.flush()
    }

    override fun read(buffer: ByteArray, timeoutMs: Int): Int {
        val stream = inputStream ?: throw IOException("Socket not open")
        val sock = socket ?: throw IOException("Socket not open")
        if (pendingInput.isNotEmpty()) {
            return copyFromPending(buffer)
        }
        sock.soTimeout = timeoutMs.coerceIn(25, 500)
        val firstCount = try {
            stream.read(buffer)
        } catch (_: SocketTimeoutException) {
            return 0
        }
        if (firstCount == -1) throw IOException("Socket stream closed")
        if (firstCount <= 0) return 0

        var total = firstCount
        while (stream.available() > 0 && total < buffer.size) {
            val room = buffer.size - total
            val count = stream.read(buffer, total, room)
            if (count <= 0) break
            total += count
        }
        while (stream.available() > 0) {
            val extra = ByteArray(stream.available().coerceAtMost(8192))
            val count = stream.read(extra)
            if (count <= 0) break
            pendingInput += extra.copyOf(count)
        }
        return total
    }

    override fun flush() {
        val stream = inputStream ?: return
        try {
            pendingInput = ByteArray(0)
            val available = stream.available()
            if (available > 0) {
                val skipBuffer = ByteArray(available)
                stream.read(skipBuffer)
            }
        } catch (ignored: Exception) {}
    }

    private fun copyFromPending(buffer: ByteArray): Int {
        val count = pendingInput.size.coerceAtMost(buffer.size)
        pendingInput.copyInto(buffer, endIndex = count)
        pendingInput = pendingInput.copyOfRange(count, pendingInput.size)
        return count
    }
}
