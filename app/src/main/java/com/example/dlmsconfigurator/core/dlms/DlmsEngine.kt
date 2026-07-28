package com.example.dlmsconfigurator.core.dlms

import com.example.dlmsconfigurator.core.data.ConnectionParams
import com.example.dlmsconfigurator.core.data.OperationItem
import com.example.dlmsconfigurator.core.transport.DlmsTransport
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
import gurux.dlms.objects.GXDLMSObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DlmsEngine(
    private val connectionParams: ConnectionParams,
    private val transport: DlmsTransport
) {
    private val client = GXDLMSSecureClient()

    init {
        client.apply {
            useLogicalNameReferencing = true
            interfaceType = when (connectionParams.interfaceType.uppercase()) {
                "HDLC" -> InterfaceType.HDLC
                else -> InterfaceType.HDLC
            }
            clientAddress = connectionParams.clientAddress
            serverAddress = connectionParams.serverAddress

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
        val cleanHex = hex.replace(" ", "").replace(":", "")
        if (cleanHex.isEmpty()) return ByteArray(0)
        val result = ByteArray(cleanHex.length / 2)
        for (i in 0 until cleanHex.length step 2) {
            result[i / 2] = cleanHex.substring(i, i + 2).toInt(16).toByte()
        }
        return result
    }

    private fun readInvocationCounter(): Long {
        val tempClient = GXDLMSSecureClient().apply {
            useLogicalNameReferencing = true
            interfaceType = client.interfaceType
            clientAddress = 16 // Public Client for pre-read
            serverAddress = client.serverAddress
            authentication = Authentication.NONE
            ciphering.security = Security.NONE
        }

        val reply = GXReplyData()

        // 1. SNRM (only if HDLC)
        if (tempClient.interfaceType == InterfaceType.HDLC) {
            val snrm = tempClient.snrmRequest()
            if (snrm != null) {
                readDLMSPacket(tempClient, snrm, reply)
                tempClient.parseUAResponse(reply.data)
            }
        }

        // 2. AARQ
        val aarqPackets = tempClient.aarqRequest()
        for (packet in aarqPackets) {
            reply.clear()
            readDLMSPacket(tempClient, packet, reply)
        }
        tempClient.parseAareResponse(reply.data)

        // 3. Read Invocation Counter
        val obis = connectionParams.invocationCounterObis 
            ?: connectionParams.invocationCounterLN
            ?: (if (connectionParams.authentication?.uppercase() == "US") "0.0.43.1.3.255" else null)
            ?: connectionParams.frameCounterLN 
            ?: "0.0.43.1.0.255"
            
        val dataObj = createCosemObject(1, obis)
        val readPackets = tempClient.read(dataObj, 2)
        val readReply = GXReplyData()
        readDataBlock(tempClient, readPackets, readReply)
        tempClient.updateValue(dataObj, 2, readReply.value)

        // 4. Disconnect (Send and Forget)
        try {
            val release = tempClient.releaseRequest()
            for (packet in release) {
                transport.write(packet)
            }
            val disc = tempClient.disconnectRequest()
            if (disc != null) {
                transport.write(disc)
            }
        } catch (ignored: Exception) {}

        val counterVal = readReply.value
        return when (counterVal) {
            is Number -> counterVal.toLong()
            is ByteArray -> bytesToLong(counterVal)
            else -> throw IOException("Invocation counter read from $obis returned non-numeric value: ${counterVal?.javaClass?.simpleName}")
        }
    }

    private fun bytesToLong(bytes: ByteArray): Long {
        var result: Long = 0
        for (b in bytes) {
            result = (result shl 8) or (b.toLong() and 0xFF)
        }
        return result
    }

    fun associate() {
        if (!transport.isOpen()) {
            transport.open()
        }

        if (connectionParams.ciphering || client.ciphering.security != Security.NONE) {
            try {
                val counterVal = readInvocationCounter()
                client.ciphering.invocationCounter = counterVal + 1
            } catch (e: Exception) {
                throw IOException("Failed to synchronize invocation counter unauthenticated: ${e.message}", e)
            }
        }

        val reply = GXReplyData()

        // 1. SNRM Request (only for HDLC interface type)
        if (client.interfaceType == InterfaceType.HDLC) {
            val snrm = client.snrmRequest()
            if (snrm != null) {
                readDLMSPacket(client, snrm, reply)
                client.parseUAResponse(reply.data)
            }
        }

        // 2. AARQ Request
        val aarqPackets = client.aarqRequest()
        for (packet in aarqPackets) {
            reply.clear()
            readDLMSPacket(client, packet, reply)
        }
        client.parseAareResponse(reply.data)

        // 3. Challenge (HLS authentication)
        if (client.isAuthenticationRequired) {
            val challenge = client.applicationAssociationRequest
            for (packet in challenge) {
                reply.clear()
                readDLMSPacket(client, packet, reply)
            }

            // Re-adding the guard for Step 4 (ActionResponse).
            // Gurux's parseAareResponse expects the AARE (0x61).
            // Verification of the meter's proof in ActionResponse is done by getData.
            if (reply.data != null && reply.data.size() > 0 && reply.data.data[0] == 0x61.toByte()) {
                client.parseAareResponse(reply.data)
            }
        }

        isAssociated = true
    }

    fun disconnect() {
        if (isAssociated) {
            try {
                // Send RLRQ
                val release = client.releaseRequest()
                for (packet in release) {
                    transport.write(packet)
                }
                // Send DISC
                val disc = client.disconnectRequest()
                if (disc != null) {
                    transport.write(disc)
                }
            } catch (ignored: Exception) {}
            isAssociated = false
        }
        transport.close()
    }

    fun executeGet(
        op: OperationItem,
        onTrafficLogged: (requestHex: String, responseHex: String) -> Unit
    ): String {
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
        return formatValue(value)
    }

    private fun getDataTypeForValue(value: Any?): DataType {
        return when (value) {
            is Boolean -> DataType.BOOLEAN
            is Byte -> DataType.INT8
            is Short -> DataType.INT16
            is Int -> DataType.INT32
            is Long -> DataType.INT64
            is Float -> DataType.FLOAT32
            is Double -> DataType.FLOAT64
            is String -> DataType.OCTET_STRING
            is ByteArray -> DataType.OCTET_STRING
            is List<*> -> DataType.STRUCTURE
            else -> DataType.NONE
        }
    }

    fun executeSet(
        op: OperationItem,
        onTrafficLogged: (requestHex: String, responseHex: String) -> Unit
    ) {
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
    }

    fun executeAction(
        op: OperationItem,
        onTrafficLogged: (requestHex: String, responseHex: String) -> Unit
    ) {
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
    }

    private fun readDLMSPacket(activeClient: GXDLMSClient, data: ByteArray, reply: GXReplyData) {
        if (!reply.streaming && data.isEmpty()) return

        reply.error = 0
        val eop: Byte? = if (activeClient.interfaceType == InterfaceType.WRAPPER) null else 0x7E.toByte()

        val rd = GXByteBuffer()
        val buffer = ByteArray(4096)
        val startTime = System.currentTimeMillis()
        val timeoutMs = 5000 // Reverted to 5s per user request for stable connection
        var attempt = 0
        var succeeded = false

        if (!reply.streaming) {
            transport.write(data)
        }

        val notify = GXReplyData()

        while (!succeeded) {
            val bytesRead = try {
                transport.read(buffer, 100)
            } catch (e: Exception) {
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    throw IOException("Timeout reading packet from meter", e)
                }
                0
            }

            if (bytesRead > 0) {
                rd.set(buffer, 0, bytesRead)
                if (activeClient.getData(rd, reply, notify)) {
                    succeeded = true
                }
            } else {
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    if (attempt++ >= 3) {
                        throw IOException("Failed to receive reply from the device in given time.")
                    }
                    // Retransmit
                    if (!reply.streaming) {
                        transport.write(data)
                    }
                }
                Thread.sleep(50)
            }
        }
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
        if (element is JsonPrimitive) {
            if (element.isString) {
                val str = element.content
                
                // If it's Class 40 (Push Setup), Attribute 3 (Destination), wrap it
                if (classId == 40 && attrOrMethodId == 3) {
                    return listOf(
                        gurux.dlms.GXEnum(0), // transport_service (TCP)
                        str.toByteArray(Charsets.US_ASCII), // destination_address
                        gurux.dlms.GXEnum(255) // message_type
                    )
                }
                
                // If it's class 8 (Clock), parse ISO date time
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
        return element.toString()
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
                    is ByteArray -> String(second).trim()
                    else -> second?.toString()?.trim() ?: ""
                }
                // If it looks like a destination, return only the string as requested
                if (s.contains(":") || s.contains(".") || s.startsWith("[")) {
                    return s
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

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun maskPasswordsInHex(hex: String): String {
        val pwd = connectionParams.password ?: return hex
        val pwdHex = hexToBytes(pwd).joinToString("") { "%02x".format(it) }
        
        if (pwdHex.length >= 4 && hex.contains(pwdHex)) {
            val mask = "*".repeat(pwdHex.length)
            return hex.replace(pwdHex, mask)
        }
        return hex
    }
}
