package com.example.dlmsconfigurator.core.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.min

class TcpAsyncTransport(
    private val host: String,
    private val port: Int,
    private val connectTimeoutMs: Int = 5_000,
    private val maxReconnectAttempts: Int = 3
) : AsyncDlmsTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val incoming = Channel<ByteArray>(capacity = Channel.BUFFERED)

    private var socket: Socket? = null
    private var readerJob: Job? = null

    override val isConnected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false

    override suspend fun connect() {
        if (isConnected) return

        var attempt = 0
        var delayMs = 250L
        var lastError: Throwable? = null
        while (attempt < maxReconnectAttempts) {
            try {
                openSocket()
                startReader()
                return
            } catch (e: Throwable) {
                lastError = e
                disconnect()
                attempt++
                if (attempt < maxReconnectAttempts) {
                    delay(delayMs)
                    delayMs = min(delayMs * 2, 4_000L)
                }
            }
        }
        throw IOException("TCP connection to $host:$port failed after $maxReconnectAttempts attempts", lastError)
    }

    override suspend fun disconnect() {
        readerJob?.cancel()
        readerJob = null
        withContext(Dispatchers.IO) {
            try { socket?.close() } catch (_: Exception) {}
            socket = null
        }
    }

    override suspend fun send(bytes: ByteArray) {
        val stream = socket?.getOutputStream() ?: throw IOException("TCP transport is not connected")
        withContext(Dispatchers.IO) {
            stream.write(bytes)
            stream.flush()
        }
    }

    override fun receiveFlow(): Flow<ByteArray> = incoming.receiveAsFlow()

    private suspend fun openSocket() = withContext(Dispatchers.IO) {
        val cleanHost = normalizeHost(host)
        if (isLinkLocalIpv6WithoutScope(cleanHost)) {
            throw IOException("IPv6 link-local TCP host requires an interface scope, for example $cleanHost%wlan0")
        }
        val address = InetAddress.getByName(cleanHost)
        val newSocket = Socket()
        newSocket.connect(InetSocketAddress(address, port), connectTimeoutMs)
        socket = newSocket
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

    private fun startReader() {
        val activeSocket = socket ?: return
        readerJob?.cancel()
        readerJob = scope.launch {
            val stream = activeSocket.getInputStream()
            val buffer = ByteArray(4096)
            while (isActive && !activeSocket.isClosed) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (count > 0) {
                    incoming.send(buffer.copyOf(count))
                }
            }
        }
    }
}
