package com.example.dlmsconfigurator.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dlmsconfigurator.core.data.AssociationObjectEntity
import com.example.dlmsconfigurator.core.data.CommSettings
import com.example.dlmsconfigurator.core.data.ConnectionParams
import com.example.dlmsconfigurator.core.data.DataRepository
import com.example.dlmsconfigurator.core.data.DeviceEntity
import com.example.dlmsconfigurator.core.data.commSettings
import com.example.dlmsconfigurator.core.data.transportLabel
import com.example.dlmsconfigurator.core.dlms.DlmsEngine
import com.example.dlmsconfigurator.core.transport.BleTransport
import com.example.dlmsconfigurator.core.transport.DlmsTransport
import com.example.dlmsconfigurator.core.transport.TcpTransport
import com.example.dlmsconfigurator.core.transport.UsbSerialTransport
import gurux.dlms.GXDLMSClient
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

// ─────────────────────────────────────────────────────────────────────────────
//  Connection State Machine
// ─────────────────────────────────────────────────────────────────────────────

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data class Connecting(val message: String = "Opening transport…") : ConnectionState()
    data class Associating(val message: String = "Associating…") : ConnectionState()
    data class Connected(val meterSerial: String? = null) : ConnectionState()
    data class Failed(val error: String) : ConnectionState()
}

data class RawTrafficEntry(
    val timestampMs: Long,
    val direction: String,
    val hex: String
)

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel — survives configuration changes / navigation recompositions
// ─────────────────────────────────────────────────────────────────────────────

class DeviceSessionViewModel : ViewModel() {

    private val TAG = "DeviceSessionVM"
    private val commDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "dlms-device-session").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    // ── Connection State ──────────────────────────────────────────────────────
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // ── Status log ────────────────────────────────────────────────────────────
    private val _statusLog = MutableStateFlow<List<String>>(emptyList())
    val statusLog: StateFlow<List<String>> = _statusLog.asStateFlow()

    private val _rawTrafficLog = MutableStateFlow<List<RawTrafficEntry>>(emptyList())
    val rawTrafficLog: StateFlow<List<RawTrafficEntry>> = _rawTrafficLog.asStateFlow()

    // ── Association view objects ───────────────────────────────────────────────
    private val _associationObjects = MutableStateFlow<List<AssociationObjectEntity>>(emptyList())
    val associationObjects: StateFlow<List<AssociationObjectEntity>> = _associationObjects.asStateFlow()

    private val _isReadingAssocView = MutableStateFlow(false)
    val isReadingAssocView: StateFlow<Boolean> = _isReadingAssocView.asStateFlow()

    // ── Error for one-shot dialog ──────────────────────────────────────────────
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ── Active engine + transport (held across recompositions) ────────────────
    var activeEngine: DlmsEngine? = null
        private set
    private var activeTransport: DlmsTransport? = null
    private var activeDeviceId: Long? = null

    // ─────────────────────────────────────────────────────────────────────────
    fun connect(deviceId: Long, repository: DataRepository, context: Context) {
        viewModelScope.launch(commDispatcher) {
            val device = repository.getDevice(deviceId) ?: return@launch
            connect(device, repository, context)
        }
    }

    fun connect(device: DeviceEntity, repository: DataRepository, context: Context) {
        if (_connectionState.value is ConnectionState.Connected ||
            _connectionState.value is ConnectionState.Connecting ||
            _connectionState.value is ConnectionState.Associating
        ) {
            if (activeDeviceId == device.id) return
            appendLog("Another meter session is already active. Disconnect first.")
            return
        }

        viewModelScope.launch(commDispatcher) {
            _statusLog.value = emptyList()
            _rawTrafficLog.value = emptyList()
            _connectionState.value = ConnectionState.Connecting()
            appendLog("Opening transport…")

            try {
                activeDeviceId = device.id
                val transport = buildTransport(device, context)
                activeTransport = transport
                appendLog("Transport created: ${device.transportLabel}")

                val connParams = buildConnectionParams(device, repository)
                val engine = DlmsEngine(connParams, transport) { direction, hex ->
                    appendRawTraffic(direction, hex)
                }
                activeEngine = engine

                _connectionState.value = ConnectionState.Associating()
                appendLog("Sending SNRM / AARQ…")

                engine.associate { msg ->
                    appendLog(msg)
                    if (_connectionState.value !is ConnectionState.Connected) {
                        _connectionState.value = ConnectionState.Associating(msg)
                    }
                }

                _connectionState.value = ConnectionState.Connected()
                appendLog("✓ Association successful")

                repository.touchDeviceConnected(device.id)

                // Load cached association objects
                val cached = repository.getAssociationObjects(device.id)
                _associationObjects.value = cached
                if (cached.isEmpty()) {
                    appendLog("No cached objects — prompt to read Association View")
                } else {
                    appendLog("${cached.size} objects loaded from cache")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}", e)
                appendLog("✗ ${e.message ?: "Unknown error"}")
                try {
                    activeEngine?.disconnect()
                    appendLog("Release / disconnect cleanup sent")
                } catch (cleanupError: Exception) {
                    Log.w(TAG, "Connection cleanup failed: ${cleanupError.message}")
                }
                _connectionState.value = ConnectionState.Failed(e.message ?: "Unknown error")
                safeCloseTransport()
                activeEngine = null
                activeDeviceId = null
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Disconnect
    // ─────────────────────────────────────────────────────────────────────────

    fun disconnect() {
        viewModelScope.launch(commDispatcher) {
            appendLog("Disconnecting…")
            try {
                activeEngine?.disconnect()
                appendLog("DLMS Release / Disconnect sent")
            } catch (e: Exception) {
                Log.w(TAG, "Disconnect error: ${e.message}")
                appendLog("Disconnect cleanup warning: ${e.message}")
            } finally {
                safeCloseTransport()
                activeEngine = null
                activeDeviceId = null
                _connectionState.value = ConnectionState.Idle
                appendLog("Disconnected")
            }
        }
    }

    fun disconnectDevice(deviceId: Long) {
        if (activeDeviceId == deviceId || activeDeviceId == null) {
            disconnect()
        } else {
            appendLog("Selected meter is not the active session.")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Read Association View (Class 15 object list)
    // ─────────────────────────────────────────────────────────────────────────

    fun readAssociationView(deviceId: Long, repository: DataRepository) {
        val engine = activeEngine ?: return
        if (_isReadingAssocView.value) return

        viewModelScope.launch(commDispatcher) {
            _isReadingAssocView.value = true
            appendLog("Reading Association View (Class 15)…")
            try {
                val objects = engine.readAssociationView()
                val entities = objects.map { it.toEntity(deviceId) }
                repository.saveAssociationObjects(deviceId, entities)
                _associationObjects.value = entities
                appendLog("✓ ${entities.size} objects cached from device")
            } catch (e: Exception) {
                Log.e(TAG, "readAssociationView failed: ${e.message}", e)
                appendLog("✗ Failed to read association view: ${e.message}")
                _error.value = "Failed to read Association View: ${e.message}"
            } finally {
                _isReadingAssocView.value = false
            }
        }
    }

    fun clearError() { _error.value = null }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun appendLog(msg: String) {
        _statusLog.value = (_statusLog.value + msg).takeLast(200)
    }

    private fun appendRawTraffic(direction: String, hex: String) {
        val now = System.currentTimeMillis()
        val current = _rawTrafficLog.value
        val last = current.lastOrNull()
        val merged = if (last != null && last.direction == direction && now - last.timestampMs <= 120L) {
            current.dropLast(1) + last.copy(
                timestampMs = now,
                hex = last.hex + hex
            )
        } else {
            current + RawTrafficEntry(
                timestampMs = now,
                direction = direction,
                hex = hex
            )
        }
        _rawTrafficLog.value = merged.takeLast(1000)
    }

    private fun safeCloseTransport() {
        try { activeTransport?.close() } catch (_: Exception) {}
        activeTransport = null
    }

    private fun buildTransport(device: DeviceEntity, context: Context): DlmsTransport {
        return when (val comm = device.commSettings) {
            is CommSettings.Otg -> UsbSerialTransport(context, comm.baudRate)
            is CommSettings.Ble -> BleTransport(context, comm.deviceAddress)
            is CommSettings.Tcp -> TcpTransport(comm.host, comm.port)
        }
    }

    private fun buildConnectionParams(device: DeviceEntity, repository: DataRepository): ConnectionParams {
        val password     = repository.resolveDeviceSecret(device.passwordKeyRef)
        val authKey      = repository.resolveDeviceSecret(device.authKeyRef)
        val encKey       = repository.resolveDeviceSecret(device.encKeyRef)
        val systemTitle  = repository.resolveDeviceSecret(device.systemTitleKeyRef)

        return ConnectionParams(
            baudRate              = (device.commSettings as? CommSettings.Otg)?.baudRate ?: 9600,
            clientAddress         = device.clientAddress,
            serverAddress         = resolveServerAddress(device),
            security              = device.security,
            password              = password,
            interfaceType         = device.interfaceType,
            addressType           = device.addressType,
            logicalServer         = device.logicalServer,
            physicalServer        = device.physicalServer,
            logicalNameReferencing = if (device.logicalNameReferencing) 1 else 0,
            systemTitle           = systemTitle,
            authenticationKey     = authKey,
            encryptionKey         = encKey,
            invocationCounterObis = device.invocationCounterObis,
            authentication        = device.authenticationRole,
            securitySuite         = device.securitySuite,
            ciphering             = device.ciphering,
            useInvocationCounter  = if (device.useInvocationCounter) 1 else 0,
            invocationCounterInitial = device.invocationCounterInitial,
            resendCount          = device.retryCount,
            waitTime             = retryIntervalToWaitTime(device.retryIntervalMs)
        )
    }

    private fun resolveServerAddress(device: DeviceEntity): Int {
        return when (device.addressType.lowercase()) {
            "default" -> GXDLMSClient.getServerAddress(device.logicalServer, device.physicalServer)
            "serialnumber" -> GXDLMSClient.getServerAddress(device.serverAddress)
            else -> device.serverAddress
        }
    }

    private fun retryIntervalToWaitTime(intervalMs: Int): String {
        val seconds = (intervalMs.coerceAtLeast(0) + 999) / 1000
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }

    override fun onCleared() {
        try {
            safeCloseTransport()
        } finally {
            commDispatcher.close()
            super.onCleared()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Extension — convert engine result to Room entity
// ─────────────────────────────────────────────────────────────────────────────

data class CosemObjectDescriptor(
    val classId: Int,
    val version: Short,
    val obisCode: String,
    val className: String = "",
    val attrAccessJson: String = "{}",
    val methodAccessJson: String = "{}"
)

fun CosemObjectDescriptor.toEntity(deviceId: Long) = AssociationObjectEntity(
    deviceId         = deviceId,
    classId          = classId,
    version          = version,
    obisCode         = obisCode,
    className        = className,
    attrAccessJson   = attrAccessJson,
    methodAccessJson = methodAccessJson
)
