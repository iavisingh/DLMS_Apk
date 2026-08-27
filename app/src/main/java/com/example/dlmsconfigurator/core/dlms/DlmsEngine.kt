package com.example.dlmsconfigurator.core.dlms

import android.util.Log
import com.example.dlmsconfigurator.core.data.ConnectionParams
import com.example.dlmsconfigurator.core.data.OperationItem
import com.example.dlmsconfigurator.core.transport.DlmsTransport
import com.example.dlmsconfigurator.ui.CosemObjectDescriptor
import gurux.dlms.GXByteBuffer
import gurux.dlms.GXDLMSClient
import gurux.dlms.secure.GXDLMSSecureClient
import gurux.dlms.GXReplyData
import gurux.dlms.GXDateTime
import gurux.dlms.enums.Authentication
import gurux.dlms.enums.DataType
import gurux.dlms.enums.InterfaceType
import gurux.dlms.enums.ObjectType
import gurux.dlms.enums.Security
import gurux.dlms.objects.GXDLMSAssociationLogicalName
import gurux.dlms.objects.GXDLMSObject
import gurux.dlms.objects.GXDLMSProfileGeneric
import gurux.dlms.objects.enums.SecuritySuite
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DlmsEngine(
    private val connectionParams: ConnectionParams,
    private val transport: DlmsTransport,
    private val onRawFrame: ((direction: String, hex: String) -> Unit)? = null
) {
    private val client = GXDLMSSecureClient()
    private val TAG = "DLMS_COMM"
    private val operationLock = ReentrantLock()
    private val retryCount = (connectionParams.resendCount ?: 3).coerceAtLeast(0)
    private val readTimeoutMs = parseWaitTimeMillis(connectionParams.waitTime)
    private var cachedReleasePackets: Array<ByteArray> = emptyArray()
    private var cachedDisconnectPacket: ByteArray? = null

    init {
        client.apply {
            useLogicalNameReferencing = connectionParams.logicalNameReferencing != 0
            interfaceType = when (connectionParams.interfaceType.uppercase()) {
                "HDLC" -> InterfaceType.HDLC
                else -> InterfaceType.HDLC
            }
            clientAddress = connectionParams.clientAddress
            serverAddress = connectionParams.serverAddress
            ciphering.securitySuite = when (connectionParams.securitySuite?.lowercase()) {
                "suite1" -> SecuritySuite.SUITE_1
                "suite2" -> SecuritySuite.SUITE_2
                else -> SecuritySuite.SUITE_0
            }

            connectionParams.systemTitle?.let {
                ciphering.systemTitle = hexToBytes(it)
            }
            connectionParams.authenticationKey?.let {
                ciphering.authenticationKey = hexToBytes(it)
            }
            (connectionParams.blockCipherKey ?: connectionParams.encryptionKey)?.let {
                ciphering.blockCipherKey = hexToBytes(it)
            }

            val pwd = connectionParams.password
            val pwdBytes = pwd?.let { hexToBytes(it) }

            when (connectionParams.security.lowercase()) {
                "none" -> {
                    authentication = Authentication.NONE
                    ciphering.security = Security.NONE
                }
                "low" -> {
                    authentication = Authentication.LOW
                    password = pwdBytes
                    ciphering.security = Security.NONE
                }
                "high" -> {
                    authentication = Authentication.HIGH
                    password = pwdBytes
                    ciphering.security = Security.NONE
                }
                "authentication" -> {
                    authentication = Authentication.HIGH
                    password = pwdBytes
                    ciphering.security = Security.AUTHENTICATION
                }
                "authenticationencryption", "high_auth_enc" -> {
                    authentication = Authentication.HIGH
                    password = pwdBytes
                    ciphering.security = Security.AUTHENTICATION_ENCRYPTION
                }
            }

            if (connectionParams.ciphering) {
                ciphering.security = Security.AUTHENTICATION_ENCRYPTION
            }
        }
    }

    private var isAssociated = false

    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.trim()
            .removePrefix("0x").removePrefix("0X") // allow "0x..." prefix in JSON config
            .replace(" ", "").replace(":", "")
        if (cleanHex.isEmpty()) return ByteArray(0)
        val result = ByteArray(cleanHex.length / 2)
        for (i in 0 until cleanHex.length step 2) {
            result[i / 2] = cleanHex.substring(i, i + 2).toInt(16).toByte()
        }
        return result
    }

    private fun readInvocationCounter(onStatusUpdate: ((String) -> Unit)? = null): Long {
        Log.d(TAG, "Syncing invocation counter via Public Client...")
        onStatusUpdate?.invoke("Public Client: Syncing invocation counter...")
        val tempClient = GXDLMSSecureClient().apply {
            useLogicalNameReferencing = true
            interfaceType = client.interfaceType
            clientAddress = 16 // Public Client for pre-read
            serverAddress = client.serverAddress
            authentication = Authentication.NONE
            ciphering.security = Security.NONE
        }

        // Disconnect frames pre-generated inside the try block (while state is valid),
        // declared here so the finally block can access them without touching Gurux state.
        var rlrqPackets: Array<ByteArray> = emptyArray()
        var discPacket: ByteArray? = null

        try {
            val reply = GXReplyData()

            // 1. SNRM (only if HDLC)
            if (tempClient.interfaceType == InterfaceType.HDLC) {
                transport.flush()
                Log.d(TAG, "Public Client: Sending SNRM...")
                onStatusUpdate?.invoke("Public Client: Sending SNRM...")
                val snrm = tempClient.snrmRequest()
                if (snrm != null) {
                    readDLMSPacket(tempClient, snrm, reply)
                    tempClient.parseUAResponse(reply.data)
                    Log.d(TAG, "Public Client: Received UA.")
                    onStatusUpdate?.invoke("Public Client: Received SNRM UA")
                }
            }

            // 2. AARQ
            Log.d(TAG, "Public Client: Sending AARQ...")
            onStatusUpdate?.invoke("Public Client: Sending AARQ...")
            val aarqPackets = tempClient.aarqRequest()
            for (packet in aarqPackets) {
                reply.clear()
                readDLMSPacket(tempClient, packet, reply)
            }
            tempClient.parseAareResponse(reply.data)
            Log.d(TAG, "Public Client: Associated.")
            onStatusUpdate?.invoke("Public Client: Associated (AARE Received)")

            // 3. Read Invocation Counter
            val obis = connectionParams.invocationCounterObis
                ?: connectionParams.invocationCounterLN
                ?: (if (connectionParams.authentication?.uppercase() == "US") "0.0.43.1.3.255" else null)
                ?: connectionParams.frameCounterLN
                ?: "0.0.43.1.0.255"

            Log.d(TAG, "Public Client: Reading invocation counter from $obis...")
            onStatusUpdate?.invoke("Public Client: Reading counter from $obis...")
            val dataObj = createCosemObject(1, obis)
            val readPackets = tempClient.read(dataObj, 2)
            val readReply = GXReplyData()
            readDataBlock(tempClient, readPackets, readReply)
            tempClient.updateValue(dataObj, 2, readReply.value)

            val counterVal = readReply.value
            val result = when (counterVal) {
                is Number -> counterVal.toLong()
                is ByteArray -> bytesToLong(counterVal)
                else -> throw IOException("Invocation counter read from $obis returned non-numeric value: ${counterVal?.javaClass?.simpleName}")
            }
            Log.d(TAG, "Public Client: Current invocation counter = $result")
            onStatusUpdate?.invoke("Public Client: Counter = $result")

            try {
                rlrqPackets = tempClient.releaseRequest()
                Log.d(TAG, "Public Client: RLRQ frame(s) pre-generated (${rlrqPackets.size} packet(s)).")
            } catch (e: Exception) {
                Log.w(TAG, "PC: Failed to pre-generate RLRQ: ${e.message}")
            }
            try {
                discPacket = buildHdlcDiscFrame(
                    serverAddress = tempClient.serverAddress,
                    clientAddress = tempClient.clientAddress
                )
                Log.d(TAG, "Public Client: DISC frame built manually: ${bytesToHex(discPacket!!)}")
            } catch (e: Exception) {
                Log.w(TAG, "PC: Failed to build DISC frame: ${e.message}")
            }

            return result

        } finally {
            try {
                Log.d(TAG, "Public Client: Sending RLRQ (fire-and-forget, then 500ms)...")
                onStatusUpdate?.invoke("Public Client: Releasing connection (RLRQ/DISC)...")
                for (packet in rlrqPackets) {
                    val hex = bytesToHex(packet)
                    Log.d(TAG, "PC TX (RLRQ): $hex")
                    logRawFrame("TX", hex)
                    try {
                        transport.write(packet)
                    } catch (e: Exception) {
                        Log.w(TAG, "PC RLRQ write failed: ${e.message}")
                    }
                }
                Thread.sleep(500)

                Log.d(TAG, "Public Client: Sending DISC (fire-and-forget, then 500ms)...")
                val disc = discPacket
                if (disc != null) {
                    val hex = bytesToHex(disc)
                    Log.d(TAG, "PC TX (DISC): $hex")
                    logRawFrame("TX", hex)
                    try {
                        transport.write(disc)
                    } catch (e: Exception) {
                        Log.w(TAG, "PC DISC write failed: ${e.message}")
                    }
                } else {
                    Log.w(TAG, "PC: No DISC frame to send.")
                }
                Thread.sleep(500)
                Log.d(TAG, "Public Client: Disconnect sequence complete.")
            } catch (e: Exception) {
                Log.w(TAG, "PC disconnect sequence error: ${e.message}")
            }
        }
    }


    private fun bytesToLong(bytes: ByteArray): Long {
        var result: Long = 0
        for (b in bytes) {
            result = (result shl 8) or (b.toLong() and 0xFF)
        }
        return result
    }

    fun associate(onStatusUpdate: ((String) -> Unit)? = null) {
        Log.d(TAG, "Starting DLMS Association...")
        onStatusUpdate?.invoke("Opening transport hardware port...")
        if (!transport.isOpen()) {
            transport.open()
        }

        if (connectionParams.isUseInvocationCounter && (connectionParams.ciphering || client.ciphering.security != Security.NONE)) {
            try {
                onStatusUpdate?.invoke("Syncing invocation counter via Public Client...")
                val counterVal = readInvocationCounter(onStatusUpdate)
                client.ciphering.invocationCounter = counterVal + 1
                Log.d(TAG, "Invocation Counter incremented to: ${client.ciphering.invocationCounter}")
                onStatusUpdate?.invoke("Invocation Counter synced: ${client.ciphering.invocationCounter}")
            } catch (e: Exception) {
                Log.e(TAG, "Invocation counter sync failed: ${e.message}")
                onStatusUpdate?.invoke("Invocation counter sync failed: ${e.message}")
                throw IOException("Failed to synchronize invocation counter unauthenticated: ${e.message}", e)
            }
        } else if (connectionParams.ciphering || client.ciphering.security != Security.NONE) {
            client.ciphering.invocationCounter = connectionParams.invocationCounterInitial ?: 0
            Log.d(TAG, "Skipping Public Client invocation counter sync (use_invocation_counter=0). Using configured counter ${client.ciphering.invocationCounter}.")
        }

        onStatusUpdate?.invoke("Connecting US Client (Address: ${client.clientAddress})...")
        val reply = GXReplyData()

        // 1. SNRM Request (only for HDLC interface type)
        if (client.interfaceType == InterfaceType.HDLC) {
            transport.flush()
            Log.d(TAG, "Sending SNRM...")
            onStatusUpdate?.invoke("Sending SNRM...")
            val snrm = client.snrmRequest()
            if (snrm != null) {
                readDLMSPacket(client, snrm, reply)
                client.parseUAResponse(reply.data)
                Log.d(TAG, "Received UA.")
                onStatusUpdate?.invoke("Received SNRM UA (HDLC Link Established)")
            }
            try { Thread.sleep(200) } catch (ignored: Exception) {}
        }

        // 2. AARQ Request
        transport.flush()
        Log.d(TAG, "Sending AARQ...")
        onStatusUpdate?.invoke("Sending AARQ...")
        val aarqPackets = client.aarqRequest()
        for (packet in aarqPackets) {
            reply.clear()
            readDLMSPacket(client, packet, reply)
        }
        client.parseAareResponse(reply.data)
        Log.d(TAG, "Received AARE (Application Associated).")
        onStatusUpdate?.invoke("Received AARE (Application Associated)")

        // 3. Challenge (HLS authentication)
        if (client.isAuthenticationRequired) {
            try { Thread.sleep(200) } catch (ignored: Exception) {}
            transport.flush()
            Log.d(TAG, "HLS Authentication Required. Sending Challenge response...")
            onStatusUpdate?.invoke("Sending HLS Challenge response...")
            val challenge = client.applicationAssociationRequest
            for (packet in challenge) {
                reply.clear()
                readDLMSPacket(client, packet, reply)
            }

            if (reply.data != null && reply.data.size() > 0 && reply.data.data[0] == 0x61.toByte()) {
                client.parseAareResponse(reply.data)
                Log.d(TAG, "HLS Authentication step 2 complete.")
                onStatusUpdate?.invoke("HLS Authentication Complete")
            }
        }

        isAssociated = true
        cacheDisconnectFrames()
        Log.i(TAG, "DLMS Association Successful.")
        onStatusUpdate?.invoke("DLMS Association Successful!")
    }

    fun disconnect() {
        try {
            Log.d(TAG, "Disconnecting with fire-and-forget RLRQ/DISC...")
            if (!transport.isOpen()) return

            if (cachedReleasePackets.isEmpty() && cachedDisconnectPacket == null) {
                cacheDisconnectFrames()
            }

            cachedReleasePackets.forEach { packet ->
                writeDisconnectFrame("RLRQ", packet)
            }
            Thread.sleep(200)

            cachedDisconnectPacket?.let { writeDisconnectFrame("DISC", it) }
            Thread.sleep(300)
        } catch (ignored: Exception) {
            Log.w(TAG, "Disconnect cleanup failed: ${ignored.message}")
        } finally {
            transport.close()
            isAssociated = false
            cachedReleasePackets = emptyArray()
            cachedDisconnectPacket = null
        }
    }

    private fun cacheDisconnectFrames() {
        cachedDisconnectPacket = try {
            if (client.interfaceType == InterfaceType.HDLC) {
                buildHdlcDiscFrame(
                    serverAddress = client.serverAddress,
                    clientAddress = client.clientAddress
                )
            } else {
                client.disconnectRequest()
            }
        } catch (e: Exception) {
            Log.w(TAG, "DISC frame generation failed: ${e.message}")
            null
        }

        cachedReleasePackets = try {
            client.releaseRequest()
        } catch (e: Exception) {
            Log.w(TAG, "RLRQ frame generation failed: ${e.message}")
            emptyArray()
        }
    }

    private fun writeDisconnectFrame(label: String, packet: ByteArray) {
        val hex = bytesToHex(packet)
        Log.d(TAG, "TX ($label): $hex")
        logRawFrame("TX", hex)
        try {
            transport.write(packet)
        } catch (e: Exception) {
            Log.w(TAG, "$label write failed: ${e.message}")
        }
    }

    /**
     * Reads the COSEM object directory from the device using Class 15
     * (Association Logical Name) attribute 2 (object_list).
     *
     * Must be called after [associate] returns successfully.
     */
    fun readAssociationView(): List<CosemObjectDescriptor> = operationLock.withLock {
        transport.flush()
        Log.d(TAG, "Reading Association View (Class 15, attr 2)…")

        val assocObj = GXDLMSAssociationLogicalName()
        val requestList = client.read(assocObj, 2)
        val reply = GXReplyData()
        readDataBlock(client, requestList, reply)
        client.updateValue(assocObj, 2, reply.value)

        val objectList = assocObj.objectList ?: return@withLock emptyList()
        Log.i(TAG, "Association View: ${objectList.size} objects returned")

        return@withLock objectList.map { obj ->
            val logicalName = obj.logicalName ?: ""
            CosemObjectDescriptor(
                classId   = obj.objectType.value,
                version   = obj.version.toShort(),
                obisCode  = logicalName,
                className = resolveObisDisplayName(logicalName, obj.objectType.value)
                    ?: resolveClassName(obj.objectType.value),
                attrAccessJson = encodeAttributeAccess(obj),
                methodAccessJson = encodeMethodAccess(obj)
            )
        }
    }

    private fun encodeAttributeAccess(obj: GXDLMSObject): String {
        return buildJsonObject {
            for (index in 1..obj.attributeCount) {
                runCatching { obj.getAccess(index).name }.getOrNull()?.let { put(index.toString(), it) }
            }
        }.toString()
    }

    private fun encodeMethodAccess(obj: GXDLMSObject): String {
        return buildJsonObject {
            for (index in 1..obj.methodCount) {
                runCatching { obj.getMethodAccess(index).name }.getOrNull()?.let { put(index.toString(), it) }
            }
        }.toString()
    }

    private fun resolveClassName(classId: Int): String = when (classId) {
        1    -> "Data"
        3    -> "Register"
        4    -> "Extended Register"
        5    -> "Demand Register"
        6    -> "Register Activation"
        7    -> "Profile Generic"
        8    -> "Clock"
        9    -> "Script Table"
        10   -> "Schedule"
        11   -> "Special Days Table"
        15   -> "Association LN"
        17   -> "SAP Assignment"
        18   -> "Image Transfer"
        19   -> "Ikek"
        20   -> "Register Monitor"
        21   -> "Register Table"
        22   -> "Action Schedule"
        23   -> "Activity Calendar"
        24   -> "Security Setup"
        25   -> "IEC HDLC Setup"
        26   -> "IEC Twisted Pair"
        27   -> "M-Bus Slave Port"
        28   -> "Data Protection"
        29   -> "Push Setup"
        40   -> "Push Setup"
        41   -> "TCP UDP Setup"
        42   -> "IPv4 Setup"
        43   -> "MAC Address Setup"
        44   -> "PPP Setup"
        45   -> "GPRS Modem Setup"
        46   -> "SMTP Setup"
        47   -> "GSM Diagnostic"
        48   -> "IPv6 Setup"
        64   -> "Utility Tables"
        70   -> "Disconnect Control"
        71   -> "Limiter"
        72   -> "M-Bus Client"
        73   -> "Wireless Mode Q-Channel"
        74   -> "M-Bus Master Port"
        75   -> "DLMS Port Protection"
        else -> "Class $classId"
    }

    fun readObjectSnapshot(
        classId: Int,
        obisCode: String,
        profileReadRequest: DlmsProfileReadRequest = DlmsProfileReadRequest()
    ): DlmsVisualSnapshot = operationLock.withLock {
        transport.flush()
        val title = resolveObisDisplayName(obisCode, classId) ?: resolveClassName(classId)
        val sections = mutableListOf<DlmsVisualSection>()
        var profileControls: DlmsProfileControls? = null
        var profileTable: DlmsProfileTable? = null
        var profileDataTable: DlmsProfileTable? = null
        var profileCaptureTable: DlmsProfileTable? = null

        when (classId) {
            1 -> {
                sections += DlmsVisualSection(
                    "Data Value",
                    listOf(readVisualRow(classId, obisCode, 2, "Value"))
                )
            }
            3 -> {
                val scalerUnit = readScalerUnit(classId, obisCode, 3)
                val value = readAttributeValue(classId, obisCode, 2)
                sections += DlmsVisualSection(
                    "Register Value",
                    listOf(
                        visualRowFromValue("Scaled value", applyScaler(value, scalerUnit), value, attribute = 2),
                        visualRowFromValue("Raw value", value, attribute = 2),
                        DlmsVisualRow("Scaler", scalerUnit?.scaler?.toString() ?: "Unavailable", DlmsVisualKind.NUMBER),
                        DlmsVisualRow("Unit", scalerUnit?.unitName ?: "Unavailable")
                    )
                )
            }
            4 -> {
                val scalerUnit = readScalerUnit(classId, obisCode, 3)
                val value = readAttributeValue(classId, obisCode, 2)
                sections += DlmsVisualSection(
                    "Extended Register",
                    listOf(
                        visualRowFromValue("Scaled value", applyScaler(value, scalerUnit), value, attribute = 2),
                        visualRowFromValue("Raw value", value, attribute = 2),
                        DlmsVisualRow("Scaler", scalerUnit?.scaler?.toString() ?: "Unavailable", DlmsVisualKind.NUMBER),
                        DlmsVisualRow("Unit", scalerUnit?.unitName ?: "Unavailable"),
                        readVisualRow(classId, obisCode, 4, "Status"),
                        readVisualRow(classId, obisCode, 5, "Capture time")
                    )
                )
            }
            5 -> {
                val scalerUnit = readScalerUnit(classId, obisCode, 4)
                val currentAverage = readAttributeValue(classId, obisCode, 2)
                val lastAverage = readAttributeValue(classId, obisCode, 3)
                sections += DlmsVisualSection(
                    "Demand Register",
                    listOf(
                        visualRowFromValue("Current average", applyScaler(currentAverage, scalerUnit), currentAverage, attribute = 2),
                        visualRowFromValue("Last average", applyScaler(lastAverage, scalerUnit), lastAverage, attribute = 3),
                        DlmsVisualRow("Scaler", scalerUnit?.scaler?.toString() ?: "Unavailable", DlmsVisualKind.NUMBER),
                        DlmsVisualRow("Unit", scalerUnit?.unitName ?: "Unavailable"),
                        readVisualRow(classId, obisCode, 5, "Status"),
                        readVisualRow(classId, obisCode, 6, "Capture time"),
                        readVisualRow(classId, obisCode, 7, "Start time current"),
                        readVisualRow(classId, obisCode, 8, "Period"),
                        readVisualRow(classId, obisCode, 9, "Number of periods")
                    )
                )
            }
            7 -> {
                val captureObjects = safeReadAttribute(classId, obisCode, 3).getOrNull()
                val capturePeriod = safeReadAttribute(classId, obisCode, 4).getOrNull()
                val sortMethod = safeReadAttribute(classId, obisCode, 5).getOrNull()
                val sortObject = safeReadAttribute(classId, obisCode, 6).getOrNull()
                val entriesInUse = safeReadAttribute(classId, obisCode, 7).getOrNull()
                val profileEntries = safeReadAttribute(classId, obisCode, 8).getOrNull()
                val captureColumns = captureObjectsToColumns(captureObjects)
                val bufferResult = safeReadProfileBuffer(obisCode, profileReadRequest)
                profileControls = DlmsProfileControls(
                    logicalName = obisCode,
                    capturePeriod = formatValue(capturePeriod),
                    entriesInUse = formatValue(entriesInUse),
                    profileEntries = formatValue(profileEntries),
                    sortMode = formatValue(sortMethod),
                    sortObject = formatValue(sortObject)
                )
                sections += DlmsVisualSection(
                    "Profile Metadata",
                    listOf(
                        visualRowFromValue("Logical Name", obisCode),
                        visualRowFromValue("Period", capturePeriod),
                        visualRowFromValue("Entries", "${formatValue(entriesInUse)} / ${formatValue(profileEntries)}"),
                        visualRowFromValue("Sort Mode", sortMethod),
                        visualRowFromValue("Sort Object", sortObject)
                    )
                )
                profileCaptureTable = captureColumnsToTable(captureColumns)
                profileDataTable = bufferResult.fold(
                    onSuccess = { profileBufferToTable(captureColumns, it) },
                    onFailure = {
                        DlmsProfileTable(
                            columns = listOf("Status"),
                            rows = listOf(listOf("Profile data read failed: ${it.message ?: "unknown error"}"))
                        )
                    }
                )
                profileTable = profileCaptureTable
            }
            8 -> {
                sections += DlmsVisualSection(
                    "Clock",
                    listOf(
                        readVisualRow(classId, obisCode, 2, "Time"),
                        readVisualRow(classId, obisCode, 3, "Time zone"),
                        readVisualRow(classId, obisCode, 4, "Status"),
                        readVisualRow(classId, obisCode, 5, "Daylight savings begin"),
                        readVisualRow(classId, obisCode, 6, "Daylight savings end"),
                        readVisualRow(classId, obisCode, 7, "Daylight savings deviation"),
                        readVisualRow(classId, obisCode, 8, "Daylight savings enabled")
                    )
                )
            }
            29, 40 -> {
                sections += DlmsVisualSection(
                    "Push Setup",
                    listOf(
                        readVisualRow(classId, obisCode, 2, "Push object list"),
                        readVisualRow(classId, obisCode, 3, "Destination and method"),
                        readVisualRow(classId, obisCode, 4, "Communication window"),
                        readVisualRow(classId, obisCode, 5, "Randomisation start interval"),
                        readVisualRow(classId, obisCode, 6, "Number of retries"),
                        readVisualRow(classId, obisCode, 7, "Repetition delay")
                    )
                )
                sections += DlmsVisualSection(
                    "Push Controls",
                    listOf(
                        DlmsVisualRow("Action", "Use method 1 to trigger a push when supported by the meter."),
                        DlmsVisualRow("Transport note", "Destination is decoded from the COSEM transport-service structure when present.")
                    )
                )
            }
            else -> {
                sections += DlmsVisualSection(
                    "Standard Attributes",
                    listOf(
                        readVisualRow(classId, obisCode, 2, "Attribute 2"),
                        DlmsVisualRow("Fallback", "No specialized visual template is defined for this class yet.")
                    )
                )
            }
        }

        DlmsVisualSnapshot(
            title = title,
            classId = classId,
            obisCode = obisCode,
            sections = sections,
            profileControls = profileControls,
            profileTable = profileTable,
            profileDataTable = profileDataTable,
            profileCaptureTable = profileCaptureTable
        )
    }

    private fun readVisualRow(classId: Int, obisCode: String, attribute: Int, label: String): DlmsVisualRow {
        return safeReadAttribute(classId, obisCode, attribute)
            .fold(
                onSuccess = { visualRowFromValue(label, it, attribute = attribute) },
                onFailure = { DlmsVisualRow(label, it.message ?: "Read failed", DlmsVisualKind.ERROR, attribute = attribute) }
            )
    }

    private fun safeReadAttribute(classId: Int, obisCode: String, attribute: Int): Result<Any?> {
        return try {
            Result.success(readAttributeValue(classId, obisCode, attribute))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readAttributeValue(classId: Int, obisCode: String, attribute: Int): Any? {
        val cosemObj = createCosemObject(classId, obisCode)
        val requestBytesList = client.read(cosemObj, attribute)
        val reply = GXReplyData()
        readDataBlock(client, requestBytesList, reply)
        try {
            client.updateValue(cosemObj, attribute, reply.value)
        } catch (ignored: Exception) {}
        return reply.value
    }

    private fun safeReadProfileBuffer(obisCode: String, request: DlmsProfileReadRequest): Result<Any?> {
        return try {
            Result.success(readProfileBuffer(obisCode, request))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readProfileBuffer(obisCode: String, request: DlmsProfileReadRequest): Any? {
        val profile = GXDLMSProfileGeneric(obisCode)
        val requestBytesList = when (request.mode) {
            DlmsProfileReadMode.ENTRY -> client.readRowsByEntry(
                profile,
                request.startEntry.coerceAtLeast(1),
                request.entryCount.coerceAtLeast(1)
            )
            DlmsProfileReadMode.LAST_DAYS -> {
                val now = Date()
                val from = Calendar.getInstance().apply {
                    time = now
                    add(Calendar.DAY_OF_YEAR, -request.lastDays.coerceAtLeast(1))
                }.time
                client.readRowsByRange(profile, from, now)
            }
            DlmsProfileReadMode.RANGE -> client.readRowsByRange(
                profile,
                parseProfileDate(request.fromDateTime, "From"),
                parseProfileDate(request.toDateTime, "To")
            )
            DlmsProfileReadMode.ALL -> client.read(profile, 2)
        }
        val reply = GXReplyData()
        readDataBlock(client, requestBytesList, reply)
        try {
            client.updateValue(profile, 2, reply.value)
        } catch (ignored: Exception) {}
        return reply.value
    }

    private fun parseProfileDate(value: String, label: String): Date {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("$label date/time is required")
        }
        val patterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "dd-MM-yyyy HH:mm:ss", "dd-MM-yyyy HH:mm")
        for (pattern in patterns) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                return parser.parse(trimmed) ?: continue
            } catch (ignored: Exception) {}
        }
        throw IllegalArgumentException("$label date/time must be yyyy-MM-dd HH:mm or dd-MM-yyyy HH:mm")
    }

    private fun readScalerUnit(classId: Int, obisCode: String, attribute: Int): ScalerUnit? {
        val value = safeReadAttribute(classId, obisCode, attribute).getOrNull() ?: return null
        val list = when (value) {
            is List<*> -> value
            is Array<*> -> value.toList()
            else -> return null
        }
        if (list.size < 2) return null
        val scaler = numericValue(list[0])?.toInt() ?: return null
        val unitCode = numericValue(list[1])?.toInt() ?: return null
        val unitName = preferredIndianUnitName(resolveDlmsUnit(unitCode))
        return ScalerUnit(scaler, unitCode, unitName)
    }

    private fun applyScaler(value: Any?, scalerUnit: ScalerUnit?): Any? {
        val number = numericValue(value) ?: return value
        val scaler = scalerUnit ?: return value
        val scaled = number * Math.pow(10.0, scaler.scaler.toDouble())
        return if (scaler.unitName == "none") {
            trimDouble(scaled)
        } else {
            "${trimDouble(scaled)} ${scaler.unitName}"
        }
    }

    private fun visualRowFromValue(label: String, value: Any?, rawValue: Any? = null, attribute: Int? = null): DlmsVisualRow {
        val kind = when (value) {
            is Boolean -> DlmsVisualKind.BOOLEAN
            is Byte, is Short, is Int, is Long, is Float, is Double -> DlmsVisualKind.NUMBER
            is ByteArray -> when (value.size) {
                4, 5, 12 -> DlmsVisualKind.DATE_TIME
                else -> DlmsVisualKind.HEX
            }
            is List<*>, is Array<*> -> DlmsVisualKind.STRUCTURE
            is GXDateTime -> DlmsVisualKind.DATE_TIME
            else -> DlmsVisualKind.TEXT
        }
        return DlmsVisualRow(
            label = label,
            value = formatValue(value),
            kind = kind,
            raw = rawValue?.let { formatValue(it) },
            attribute = attribute
        )
    }

    private fun captureObjectsToTable(value: Any?): DlmsProfileTable? {
        val rows = flattenTopLevel(value).mapIndexed { index, item ->
            val cells = flattenTopLevel(item).map { formatValue(it, true) }
            listOf((index + 1).toString()) + cells
        }
        if (rows.isEmpty()) return null
        val maxCells = rows.maxOf { it.size }
        val columns = (0 until maxCells).map { idx ->
            when (idx) {
                0 -> "#"
                1 -> "Class"
                2 -> "OBIS"
                3 -> "Attribute"
                4 -> "Data index"
                else -> "Value $idx"
            }
        }
        return DlmsProfileTable(columns, rows.map { it + List(maxCells - it.size) { "" } })
    }

    private data class CaptureColumn(
        val classId: Int?,
        val obisCode: String,
        val attribute: Int?,
        val dataIndex: Int?,
        val name: String
    ) {
        val heading: String
            get() = listOf(
                obisCode,
                if (attribute == 2) "Value" else "Attribute ${attribute ?: "-"}",
                name
            ).joinToString("\n")
    }

    private fun captureObjectsToColumns(value: Any?): List<CaptureColumn> {
        return flattenTopLevel(value).mapNotNull { item ->
            val cells = flattenTopLevel(item)
            if (cells.size < 4) return@mapNotNull null
            val captureClassId = numericValue(cells[0])?.toInt()
            val captureObis = formatLogicalName(cells[1])
            val attribute = numericValue(cells[2])?.toInt()
            val dataIndex = numericValue(cells[3])?.toInt()
            CaptureColumn(
                classId = captureClassId,
                obisCode = captureObis,
                attribute = attribute,
                dataIndex = dataIndex,
                name = resolveObisDisplayName(captureObis, captureClassId ?: 0)
                    ?: resolveClassName(captureClassId ?: 0)
            )
        }
    }

    private fun captureColumnsToTable(columns: List<CaptureColumn>): DlmsProfileTable? {
        if (columns.isEmpty()) return null
        return DlmsProfileTable(
            columns = listOf("#", "Class", "OBIS", "Attribute", "Data index", "Name"),
            rows = columns.mapIndexed { index, column ->
                listOf(
                    (index + 1).toString(),
                    column.classId?.toString() ?: "-",
                    column.obisCode,
                    column.attribute?.toString() ?: "-",
                    column.dataIndex?.toString() ?: "-",
                    column.name
                )
            }
        )
    }

    private fun profileBufferToTable(columns: List<CaptureColumn>, buffer: Any?): DlmsProfileTable {
        val headings = if (columns.isEmpty()) {
            listOf("Value")
        } else {
            columns.map { it.heading }
        }
        val rows = flattenTopLevel(buffer).mapIndexed { index, row ->
            val values = flattenTopLevel(row).ifEmpty { listOf(row) }.map { formatValue(it, true) }
            listOf(if (index == 0) "▶" else "") + values + List((headings.size - values.size).coerceAtLeast(0)) { "" }
        }
        return DlmsProfileTable(
            columns = listOf("") + headings,
            rows = rows.ifEmpty { listOf(listOf("") + List(headings.size) { "" }) }
        )
    }

    private fun formatLogicalName(value: Any?): String {
        val bytes = value as? ByteArray
        if (bytes != null && bytes.size == 6) {
            return bytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
        }
        return formatValue(value, true)
    }

    private fun flattenTopLevel(value: Any?): List<Any?> {
        return when (value) {
            is List<*> -> value
            is Array<*> -> value.toList()
            else -> emptyList()
        }
    }

    private fun numericValue(value: Any?): Double? = when (value) {
        is Byte -> value.toDouble()
        is Short -> value.toDouble()
        is Int -> value.toDouble()
        is Long -> value.toDouble()
        is Float -> value.toDouble()
        is Double -> value
        else -> value?.toString()?.toDoubleOrNull()
    }

    private fun trimDouble(value: Double): String {
        return if (value % 1.0 == 0.0) value.toLong().toString() else "%.6f".format(Locale.US, value).trimEnd('0').trimEnd('.')
    }


    fun executeGet(
        op: OperationItem,
        onTrafficLogged: (requestHex: String, responseHex: String) -> Unit
    ): String = operationLock.withLock {
        transport.flush()
        Log.d(TAG, "Execute GET: ${op.name} (OBIS: ${op.obis}, Class: ${op.classId})")
        val attribute = op.attribute ?: 2
        val cosemObj = createCosemObject(op.classId, op.obis)
        val requestBytesList = client.read(cosemObj, attribute)
        
        val requestHex = requestBytesList.joinToString("") { bytesToHex(it) }
        val reply = GXReplyData()

        readDataBlock(client, requestBytesList, reply)

        val responseHex = bytesToHex(reply.data.data)
        onTrafficLogged(maskPasswordsInHex(requestHex), maskPasswordsInHex(responseHex))

        // Update value inside the object to let Gurux parse it
        try {
            client.updateValue(cosemObj, attribute, reply.value)
        } catch (ignored: Exception) {}
        
        val value = reply.value
        val formatted = formatValue(value)
        Log.i(TAG, "GET Result: $formatted")
        return formatted
    }

    private fun getDataTypeForValue(value: Any?): DataType {
        return when (value) {
            is Boolean  -> DataType.BOOLEAN
            is Byte     -> DataType.INT8
            is Short    -> DataType.INT16
            is Int      -> DataType.INT32
            is Long     -> DataType.INT64
            is Float    -> DataType.FLOAT32
            is Double   -> DataType.FLOAT64
            is String   -> DataType.OCTET_STRING
            is ByteArray -> DataType.OCTET_STRING
            is List<*>  -> {
                // ARRAY when list elements are themselves collections (e.g. Class 22 Attr 4 execution_time).
                // STRUCTURE for a flat list of scalar/ByteArray fields (e.g. Push Setup destination).
                if (value.isNotEmpty() && (value.first() is Array<*> || value.first() is List<*>))
                    DataType.ARRAY
                else
                    DataType.STRUCTURE
            }
            else -> DataType.NONE
        }
    }

    fun executeSet(
        op: OperationItem,
        onTrafficLogged: (requestHex: String, responseHex: String) -> Unit
    ) = operationLock.withLock {
        transport.flush()
        Log.d(TAG, "Execute SET: ${op.name} (OBIS: ${op.obis}, Value: ${op.value})")
        val attribute = op.attribute ?: 2
        val valueJson = op.value ?: throw IllegalArgumentException("Set operation must specify 'value'")
        val cosemObj = createCosemObject(op.classId, op.obis)

        val valueObj = parseJsonElementValue(valueJson, op.classId, attribute)
        val requestBytesList = client.write(
            cosemObj.logicalName,
            valueObj,
            getDataTypeForValue(valueObj),
            cosemObj.objectType,
            attribute
        )
        val requestHex = requestBytesList.joinToString("") { bytesToHex(it) }
        
        val reply = GXReplyData()
        readDataBlock(client, requestBytesList, reply)

        val responseHex = bytesToHex(reply.data.data)
        onTrafficLogged(maskPasswordsInHex(requestHex), maskPasswordsInHex(responseHex))
        Log.i(TAG, "SET Successful (Response: $responseHex)")
    }

    fun executeAction(
        op: OperationItem,
        onTrafficLogged: (requestHex: String, responseHex: String) -> Unit
    ) = operationLock.withLock {
        transport.flush()
        Log.d(TAG, "Execute ACTION: ${op.name} (OBIS: ${op.obis}, Method: ${op.method})")
        val method = op.method ?: 1
        val paramsJson = op.params
        val cosemObj = createCosemObject(op.classId, op.obis)

        // Actions can take parameters
        val paramObj = paramsJson?.let { parseJsonElementValue(it, 0, method) }
        
        // Generate method request
        val requestBytesList = if (paramObj != null) {
            client.method(cosemObj, method, paramObj, gurux.dlms.enums.DataType.NONE)
        } else {
            client.method(cosemObj, method, null, gurux.dlms.enums.DataType.NONE)
        }
        
        val requestHex = requestBytesList.joinToString("") { bytesToHex(it) }
        
        val reply = GXReplyData()
        readDataBlock(client, requestBytesList, reply)

        val responseHex = bytesToHex(reply.data.data)
        onTrafficLogged(maskPasswordsInHex(requestHex), maskPasswordsInHex(responseHex))
        Log.i(TAG, "ACTION Successful (Response: $responseHex)")
    }

    private fun readDLMSPacket(activeClient: GXDLMSClient, data: ByteArray, reply: GXReplyData) {
        if (!reply.streaming && data.isEmpty()) return

        reply.error = 0
        val eop: Byte? = if (activeClient.interfaceType == InterfaceType.WRAPPER) null else 0x7E.toByte()

        val rd = GXByteBuffer()
        val buffer = ByteArray(4096)
        val startTime = System.currentTimeMillis()
        val timeoutMs = readTimeoutMs
        var attempt = 0
        var succeeded = false

        if (!reply.streaming) {
            val txHex = bytesToHex(data)
            Log.d(TAG, "TX: $txHex")
            logRawFrame("TX", txHex)
            transport.write(data)
        }

        val notify = GXReplyData()

        while (!succeeded) {
            val bytesRead = try {
                transport.read(buffer, 100)
            } catch (e: Exception) {
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    Log.e(TAG, "Read Error: ${e.message}")
                    throw IOException("Timeout reading packet from meter", e)
                }
                0
            }

            if (bytesRead > 0) {
                val rxPart = ByteArray(bytesRead)
                System.arraycopy(buffer, 0, rxPart, 0, bytesRead)
                val rxHex = bytesToHex(rxPart)
                Log.d(TAG, "RX: $rxHex")
                logRawFrame("RX", rxHex)
                
                rd.set(buffer, 0, bytesRead)
                if (activeClient.getData(rd, reply, notify)) {
                    succeeded = true
                }
            } else {
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    if (attempt++ >= retryCount) {
                        Log.e(TAG, "Max attempts reached. No reply.")
                        throw IOException("Failed to receive reply from the device in given time.")
                    }
                    // Retransmit
                    if (!reply.streaming) {
                        Log.w(TAG, "Timeout. Retransmitting TX...")
                        logRawFrame("TX", bytesToHex(data))
                        transport.write(data)
                    }
                }
                Thread.sleep(50)
            }
        }
    }

    private fun parseWaitTimeMillis(waitTime: String?): Long {
        val fallback = 10_000L
        val parts = waitTime?.split(":") ?: return fallback
        if (parts.size != 3) return fallback
        val hours = parts[0].toLongOrNull() ?: return fallback
        val minutes = parts[1].toLongOrNull() ?: return fallback
        val seconds = parts[2].toLongOrNull() ?: return fallback
        return ((hours * 3600 + minutes * 60 + seconds) * 1000).coerceAtLeast(1000)
    }

    private fun readDataBlock(activeClient: GXDLMSClient, requestList: Array<ByteArray>, reply: GXReplyData) {
        for (packet in requestList) {
            reply.clear()
            readDLMSPacket(activeClient, packet, reply)
        }

        while (reply.isMoreData) {
            val nextPacket = if (reply.isStreaming) {
                null
            } else {
                activeClient.receiverReady(reply.moreData)
            }

            if (nextPacket != null) {
                readDLMSPacket(activeClient, nextPacket, reply)
            } else {
                readDLMSPacket(activeClient, ByteArray(0), reply)
            }
        }
    }

    private fun createCosemObject(classId: Int, obisCode: String): GXDLMSObject {
        val type = ObjectType.forValue(classId)
        val obj = GXDLMSClient.createObject(type) ?: gurux.dlms.objects.GXDLMSData(obisCode)
        obj.logicalName = obisCode
        return obj
    }

    private fun parseJsonElementValue(element: JsonElement, classId: Int, attrOrMethodId: Int): Any? {
        // ── Class 22 (Action Schedule), Attribute 4 (execution_time) ─────────────────────────────
        // The value is an ARRAY of STRUCTURE { time(4B octet-string), date(5B octet-string) }.
        // Accept:
        //   • A JSON array of strings: ["HH:MM:SS:hh", ...] (each parsed to [time, date] bytes)
        //   • A single string (wrapped in a one-element list)
        // Wildcard fields use "*" (encoded as 0xFF).  Hundredths and date default to all-wildcards.
        if (classId == 22 && attrOrMethodId == 4) {
            return when (element) {
                is JsonArray -> element.map { parseScheduleTimeEntry(it.jsonPrimitive.content) }
                is JsonPrimitive -> {
                    val content = element.content
                    // Split content by comma or newline if user entered multiple schedules in one string
                    val entries = if (content.contains(",") || content.contains("\n")) {
                        content.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
                    } else {
                        listOf(content.trim()).filter { it.isNotEmpty() }
                    }
                    entries.map { parseScheduleTimeEntry(it) }
                }
                else -> emptyList<gurux.dlms.GXStructure>()
            }
        }

        if (element is JsonPrimitive) {
            if (element.isString) {
                val str = element.content

                // Class 40 (Push Setup), Attribute 3 (Destination): wrap as transport structure
                if (classId == 40 && attrOrMethodId == 3) {
                    val struct = gurux.dlms.GXStructure()
                    struct.add(gurux.dlms.GXEnum(0))                   // transport_service (0 = TCP)
                    struct.add(str.toByteArray(Charsets.US_ASCII))     // destination_address (ASCII OctetString)
                    struct.add(gurux.dlms.GXEnum(0))                   // message_type (0 = DLMS APDU)
                    return struct
                }

                // Class 8 (Clock): parse ISO date-time string
                if (classId == 8) {
                    try {
                        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(str)
                        if (date != null) {
                            val cal = Calendar.getInstance().apply { time = date }
                            return GXDateTime(cal)
                        }
                    } catch (ignored: Exception) {}
                }
                return str
            }
            element.longOrNull?.let { return it }
            element.doubleOrNull?.let { return it }
            element.booleanOrNull?.let { return it }
        }

        // Generic JsonArray → list (recursive, for future use)
        if (element is JsonArray) {
            return element.map { parseJsonElementValue(it, classId, attrOrMethodId) }
        }

        return element.toString()
    }

    /**
     * Parses an execution_time entry string into [time_bytes(4), date_bytes(5)].
     *
     * Accepts both orderings — auto-detected by whether the first token contains '-' (date) or ':' (time):
     *
     *   Time-first:  "HH:MM:SS"  |  "HH:MM:SS:hh"  |  "HH:MM:SS:hh DD-MM-YYYY"
     *   Date-first:  "DD-MM-YYYY HH:MM:SS"  |  "*-*-* *:00:00"
     *
     * Wildcard: use "*" for any field → encoded as 0xFF.
     * Hundredths and date default to all-wildcards if omitted.
     *
     * Examples:
     *   "*-*-* *:00:00"  →  time=[FF FF 00 00], date=[FF FF FF FF FF]
     *   "*:00:00"        →  time=[FF 00 00 FF], date=[FF FF FF FF FF]
     *   "*:11:00 01-*-*" →  time=[FF 0B 00 FF], date=[FF FF FF 01 FF]
     */
    private fun parseScheduleTimeEntry(str: String): gurux.dlms.GXStructure {
        fun field(s: String?): Byte = if (s == null || s.trim() == "*") 0xFF.toByte()
                                      else (s.trim().toIntOrNull() ?: 0xFF).let {
                                          if (it > 0xFF) 0xFF.toByte() else it.toByte()
                                      }

        val parts = str.trim().split(" ", limit = 2)

        // Detect ordering: if first token contains '-' it is the date part, otherwise time part.
        val firstContainsDash = parts[0].contains("-")
        val rawTimePart = if (firstContainsDash) parts.getOrNull(1)?.trim() ?: "" else parts[0]
        val rawDatePart = if (firstContainsDash) parts[0] else parts.getOrNull(1)?.trim()

        val timeParts = rawTimePart.split(":")
        val timeBytes = byteArrayOf(
            field(timeParts.getOrNull(0)),  // hour
            field(timeParts.getOrNull(1)),  // minute
            field(timeParts.getOrNull(2)),  // second
            field(timeParts.getOrNull(3))   // hundredths (default *)
        )

        val dateBytes: ByteArray = if (rawDatePart != null) {
            // "DD-MM-YYYY" or "DD-MM-*" or "*-*-*" etc.
            val dp = rawDatePart.split("-")
            val day     = field(dp.getOrNull(0))
            val month   = field(dp.getOrNull(1))
            val yearStr = dp.getOrNull(2)?.trim()
            val yearVal = if (yearStr == null || yearStr == "*") 0xFFFF
                          else yearStr.toIntOrNull() ?: 0xFFFF
            byteArrayOf(
                ((yearVal shr 8) and 0xFF).toByte(),
                (yearVal and 0xFF).toByte(),
                month,
                day,
                0xFF.toByte() // day-of-week: always wildcard
            )
        } else {
            // No date specified — all wildcards
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        }

        val struct = gurux.dlms.GXStructure()
        struct.add(timeBytes)
        struct.add(dateBytes)
        return struct
    }


    private fun formatValue(value: Any?, isNested: Boolean = false): String {
        if (value == null) return "null"
        
        // Handle structures (List or Array)
        if (value is List<*> || value is Array<*>) {
            val list = if (value is List<*>) value else (value as Array<*>).toList()
            
            // Special handling for Push Setup Destination (Structure of 3 items)
            // Extract the middle element if it looks like a destination string
            if (!isNested && list.size == 3) {
                val second = list[1]
                val s = when (second) {
                    // Decode as ISO-8859-1 (1:1 byte mapping) then drop non-printable bytes.
                    // Using the default UTF-8 charset causes replacement characters (������)
                    // when the byte array contains null padding or other non-ASCII bytes.
                    is ByteArray -> String(second, Charsets.ISO_8859_1)
                        .filter { it.code in 32..126 }  // keep printable ASCII only
                        .trim()
                    else -> second?.toString()?.trim() ?: ""
                }
                // If it looks like a destination (IP, hostname, or bracketed IPv6), return it
                if (s.contains(":") || s.contains(".") || s.startsWith("[")) {
                    return s
                }
            }
            
            // Handling for Action Schedule entry structure (List of 2 items: time and date)
            if (isNested && list.size == 2) {
                val first = list[0]
                val second = list[1]
                if (first is ByteArray && second is ByteArray) {
                    if (first.size == 4 && second.size == 5) {
                        // first is time (4B), second is date (5B) → format as "date time"
                        return "${parseDlmsDate(second)} ${parseDlmsTime(first)}"
                    } else if (first.size == 5 && second.size == 4) {
                        // first is date (5B), second is time (4B) → format as "date time"
                        return "${parseDlmsDate(first)} ${parseDlmsTime(second)}"
                    }
                }
            }

            // Intelligent joining: Newline for top-level entries, Space for nested components
            val separator = if (isNested) " " else "\n"
            return list.joinToString(separator) { formatValue(it, true) }
        }

        if (value is ByteArray) {
            // Check if it's a 12-byte date-time (Octet String)
            if (value.size == 12 && value[0] in 0x07..0x08) { // Likely 2000s year
                try {
                    return parseDlmsDateTime(value)
                } catch (ignored: Exception) {}
            }

            // Check for DLMS Time (4 bytes) or Date (5 bytes)
            if (value.size == 4) {
                try {
                    return parseDlmsTime(value)
                } catch (ignored: Exception) {}
            }
            if (value.size == 5) {
                try {
                    return parseDlmsDate(value)
                } catch (ignored: Exception) {}
            }

            return bytesToHex(value)
        }
        
        if (value is GXDateTime) {
            // Use toString() to handle DLMS wildcards correctly for reader
            return value.toString()
        }
        
        return value.toString()
    }

    private fun parseDlmsDateTime(bytes: ByteArray): String {
        // year(2), month(1), day(1), day_of_week(1), hour(1), minute(1), second(1), hundredths(1), deviation(2), status(1)
        val year = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
        val month = bytes[2].toInt() and 0xFF
        val day = bytes[3].toInt() and 0xFF
        val hour = bytes[5].toInt() and 0xFF
        val min = bytes[6].toInt() and 0xFF
        val sec = bytes[7].toInt() and 0xFF
        
        // Return DD-MM-YYYY HH:mm:ss format
        return "%02d-%02d-%04d %02d:%02d:%02d".format(day, month, year, hour, min, sec)
    }

    private fun parseDlmsTime(bytes: ByteArray): String {
        // hour(1), minute(1), second(1), hundredths(1)
        val h = if (bytes[0] == 0xFF.toByte()) "*" else "%02d".format(bytes[0].toInt() and 0xFF)
        val m = if (bytes[1] == 0xFF.toByte()) "*" else "%02d".format(bytes[1].toInt() and 0xFF)
        val s = if (bytes[2] == 0xFF.toByte()) "*" else "%02d".format(bytes[2].toInt() and 0xFF)
        // hundredths (bytes[3]) usually ignored in short display
        return "$h:$m:$s"
    }

    private fun parseDlmsDate(bytes: ByteArray): String {
        // year(2), month(1), day(1), day_of_week(1)
        val day = if (bytes[3] == 0xFF.toByte()) "*" else "%02d".format(bytes[3].toInt() and 0xFF)
        val month = if (bytes[2] == 0xFF.toByte()) "*" else "%02d".format(bytes[2].toInt() and 0xFF)
        val yearVal = if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFF.toByte()) 0xFFFF
                      else (((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF))
        val year = if (yearVal == 0xFFFF) "*" else "%04d".format(yearVal)
        return "$day-$month-$year"
    }

    // -----------------------------------------------------------------------------------------
    // HDLC frame helpers — used to manually construct the DISC frame for the public client
    // because GXDLMSClient.disconnectRequest() returns null after releaseRequest() is called.
    // -----------------------------------------------------------------------------------------

    /**
     * Builds a minimal HDLC DISC frame:
     *   7E | A0 len | [dest bytes] | [src bytes] | 0x53 | CRC_L CRC_H | 7E
     *
     * Reference (GxDirector, client=48 server=1): 7E A0 07 03 61 53 65 81 7E
     */
    private fun buildHdlcDiscFrame(serverAddress: Int, clientAddress: Int): ByteArray {
        val dest = encodeHdlcAddress(serverAddress)   // destination = server
        val src  = encodeHdlcAddress(clientAddress)   // source      = client
        // frame length = FF(2) + dest + src + ctrl(1) + FCS(2), all bytes between the flags
        val frameLen = 2 + dest.size + src.size + 1 + 2
        val ff      = byteArrayOf(0xa0.toByte(), frameLen.toByte())
        val content = ff + dest + src + byteArrayOf(0x53.toByte()) // 0x53 = DISC control
        val crc     = hdlcCrc16(content)
        val fcs     = byteArrayOf((crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte())
        return byteArrayOf(0x7e.toByte()) + content + fcs + byteArrayOf(0x7e.toByte())
    }

    /**
     * Encodes an integer address into HDLC address byte(s).
     * Each byte holds 7 bits of address; LSB=0 means more bytes follow, LSB=1 means last byte.
     * Most DLMS meters use single-byte addresses (address ≤ 127).
     *
     *   client=16  → 0x21   (16<<1|1 = 33)   ✓ matches TX logs
     *   client=48  → 0x61   (48<<1|1 = 97)   ✓ matches GxDirector reference
     *   server=1   → 0x03   ( 1<<1|1 =  3)   ✓ matches TX logs
     */
    private fun encodeHdlcAddress(address: Int): ByteArray {
        return when {
            address <= 0x7F   -> byteArrayOf(((address shl 1) or 1).toByte())
            address <= 0x3FFF -> byteArrayOf(
                ((address ushr 7 shl 1) and 0xFE).toByte(),
                ((address shl 1) or 1).toByte()
            )
            else              -> byteArrayOf(
                ((address ushr 21 shl 1) and 0xFE).toByte(),
                ((address ushr 14 shl 1) and 0xFE).toByte(),
                ((address ushr 7  shl 1) and 0xFE).toByte(),
                ((address shl 1) or 1).toByte()
            )
        }
    }

    /**
     * CRC-16-CCITT reflected (poly=0x8408, init=0xFFFF, finalXor=0xFFFF).
     * This is the standard FCS algorithm used in HDLC framing.
     *
     * Verified: input=[A0,07,03,61,53] (DISC for client=48 server=1) → CRC=0x8165
     *           matches GxDirector reference frame: ...53 65 81 7E (stored little-endian)
     */
    private fun hdlcCrc16(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            var x = b.toInt() and 0xFF
            repeat(8) {
                crc = if ((crc xor x) and 1 != 0) (crc ushr 1) xor 0x8408
                      else crc ushr 1
                x = x ushr 1
            }
        }
        return crc xor 0xFFFF
    }



    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun logRawFrame(direction: String, hex: String) {
        onRawFrame?.invoke(direction, hex)
    }

    private fun maskPasswordsInHex(hex: String): String {
        var masked = hex
        val keysToMask = listOfNotNull(
            connectionParams.password,
            connectionParams.authenticationKey,
            connectionParams.blockCipherKey,
            connectionParams.encryptionKey
        )

        for (key in keysToMask) {
            if (key.isBlank()) continue
            val cleanKey = key.replace(" ", "")
            if (cleanKey.length >= 4 && masked.contains(cleanKey, ignoreCase = true)) {
                val mask = "*".repeat(cleanKey.length)
                masked = masked.replace(cleanKey, mask, ignoreCase = true)
            }
            try {
                val asciiHex = cleanKey.toByteArray(Charsets.US_ASCII).joinToString("") { "%02x".format(it) }
                if (asciiHex.length >= 4 && masked.contains(asciiHex, ignoreCase = true)) {
                    val mask = "*".repeat(asciiHex.length)
                    masked = masked.replace(asciiHex, mask, ignoreCase = true)
                }
            } catch (ignored: Exception) {}
        }
        return masked
    }
}
