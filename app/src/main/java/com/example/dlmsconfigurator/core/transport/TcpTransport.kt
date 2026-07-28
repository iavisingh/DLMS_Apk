package com.example.dlmsconfigurator.core.transport

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class TcpTransport(
    private val host: String,
    private val port: Int
) : DlmsTransport {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    override fun open() {
        if (socket != null) return
        
        // Handle IPv6 bracketed hosts if passed manually
        val cleanHost = host.removePrefix("[").removeSuffix("]")
        
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

    override fun close() {
        try { inputStream?.close() } catch (ignored: Exception) {}
        inputStream = null
        try { outputStream?.close() } catch (ignored: Exception) {}
        outputStream = null
        try { socket?.close() } catch (ignored: Exception) {}
        socket = null
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
        sock.soTimeout = timeoutMs
        val count = stream.read(buffer)
        if (count == -1) {
            throw IOException("Socket stream closed")
        }
        return count
    }
}
