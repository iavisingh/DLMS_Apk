package com.example.dlmsconfigurator.core.session

import com.example.dlmsconfigurator.core.transport.AsyncDlmsTransport
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Minimal coroutine-native DLMS session shell.
 *
 * It enforces the important concurrency rule from the development spec:
 * one request in flight per physical association. Protocol-specific APDU
 * generation remains in the Gurux-backed engine until it is migrated.
 */
class DlmsSession(
    private val meterId: String,
    private val transport: AsyncDlmsTransport,
    private val frameCounterStore: FrameCounterStore
) {
    private val requestMutex = Mutex()

    @Volatile
    var state: AssociationState = AssociationState.Idle
        private set

    suspend fun connect() {
        transport.connect()
        state = AssociationState.AarqSent
    }

    suspend fun markAssociated() {
        state = AssociationState.Associated
    }

    suspend fun release() {
        state = AssociationState.Releasing
        transport.disconnect()
        state = AssociationState.Closed
    }

    fun nextFrameCounter(): Long = frameCounterStore.nextOutgoingCounter(meterId)

    fun acceptIncomingFrameCounter(counter: Long): Boolean {
        return frameCounterStore.acceptIncomingCounter(meterId, counter)
    }

    suspend fun requestResponse(request: ByteArray, timeoutMs: Long = 10_000L): ByteArray {
        return requestMutex.withLock {
            try {
                withTimeout(timeoutMs) {
                    transport.send(request)
                    transport.receiveFlow().first()
                }
            } catch (e: TimeoutCancellationException) {
                throw DlmsSessionTimeoutException("Timed out waiting for meter response after ${timeoutMs}ms", e)
            }
        }
    }
}

class DlmsSessionTimeoutException(message: String, cause: Throwable) : Exception(message, cause)
