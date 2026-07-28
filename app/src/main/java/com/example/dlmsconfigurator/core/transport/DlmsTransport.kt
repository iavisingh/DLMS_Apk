package com.example.dlmsconfigurator.core.transport

interface DlmsTransport {
    fun open()
    fun close()
    fun isOpen(): Boolean
    fun write(data: ByteArray)
    fun read(buffer: ByteArray, timeoutMs: Int): Int
    fun flush()
}
