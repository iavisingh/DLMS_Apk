package com.example.dlmsconfigurator.core.transport

import kotlinx.coroutines.flow.Flow

/**
 * Coroutine-native transport contract for future non-blocking DLMS sessions.
 *
 * The current Gurux-backed engine still uses [DlmsTransport]'s blocking reads,
 * so this interface is introduced alongside it rather than breaking the working
 * hardware path in one risky edit.
 */
interface AsyncDlmsTransport {
    val isConnected: Boolean

    suspend fun connect()
    suspend fun disconnect()
    suspend fun send(bytes: ByteArray)
    fun receiveFlow(): Flow<ByteArray>
}
