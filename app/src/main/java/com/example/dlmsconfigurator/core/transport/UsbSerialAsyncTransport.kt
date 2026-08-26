package com.example.dlmsconfigurator.core.transport

import android.app.PendingIntent
import android.content.Context
import android.hardware.usb.UsbDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class UsbSerialAsyncTransport(
    context: Context,
    baudRate: Int
) : AsyncDlmsTransport {
    private val delegate = UsbSerialTransport(context, baudRate)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val incoming = Channel<ByteArray>(capacity = Channel.BUFFERED)
    private var readerJob: Job? = null

    override val isConnected: Boolean
        get() = delegate.isOpen()

    fun getDevice(): UsbDevice? = delegate.getDevice()

    fun hasPermission(): Boolean = delegate.hasPermission()

    fun requestPermission(intent: PendingIntent) = delegate.requestPermission(intent)

    override suspend fun connect() {
        if (isConnected) return
        withContext(Dispatchers.IO) {
            delegate.open()
            delegate.flush()
        }
        startReader()
    }

    override suspend fun disconnect() {
        readerJob?.cancel()
        readerJob = null
        withContext(Dispatchers.IO) {
            delegate.close()
        }
    }

    override suspend fun send(bytes: ByteArray) {
        if (!isConnected) throw IOException("USB serial transport is not connected")
        withContext(Dispatchers.IO) {
            delegate.write(bytes)
        }
    }

    override fun receiveFlow(): Flow<ByteArray> = incoming.receiveAsFlow()

    private fun startReader() {
        readerJob?.cancel()
        readerJob = scope.launch {
            val buffer = ByteArray(2048)
            while (isActive && delegate.isOpen()) {
                val count = runCatching { delegate.read(buffer, 250) }.getOrElse { break }
                if (count > 0) {
                    incoming.send(buffer.copyOf(count))
                }
            }
        }
    }
}
