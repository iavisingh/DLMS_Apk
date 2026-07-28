package com.example.dlmsconfigurator.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
data class ConnectionParams(
    @SerialName("baud_rate") val baudRate: Int = 9600,
    @SerialName("client_address") val clientAddress: Int = 16,
    @SerialName("server_address") val serverAddress: Int = 1,
    @SerialName("security") val security: String = "none",
    @SerialName("password") val password: String? = null,
    @SerialName("interface") val interfaceType: String = "HDLC",
    @SerialName("system_title") val systemTitle: String? = null,
    @SerialName("authentication_key") val authenticationKey: String? = null,
    @SerialName("encryption_key") val encryptionKey: String? = null,
    @SerialName("invocation_counter_obis") val invocationCounterObis: String? = null,
    @SerialName("invocation_counter_ln") val invocationCounterLN: String? = null,
    @SerialName("ciphering") val ciphering: Boolean = true,
    @SerialName("name") val name: String? = null,
    
    // Genus/LnG specific fields
    @SerialName("manufacturer") val manufacturer: String? = null,
    @SerialName("authentication") val authentication: String? = null,
    @SerialName("logical_name_referencing") val logicalNameReferencing: Int? = 1,
    @SerialName("wait_time") val waitTime: String? = "00:00:05",
    @SerialName("resend_count") val resendCount: Int? = 3,
    @SerialName("frame_size") val frameSize: Int? = 2048,
    @SerialName("security_suite") val securitySuite: String? = "Suite0",
    @SerialName("block_cipher_key") val blockCipherKey: String? = null,
    @SerialName("frame_counter_LN") val frameCounterLN: String? = "0.0.43.1.3.255",
    @SerialName("push_serverv6_ip") val pushServerV6Ip: String? = null,
    @SerialName("push_serverv4_ip") val pushServerV4Ip: String? = null,
    @SerialName("push_serverv6_port") val pushServerV6Port: Int? = null,
    @SerialName("push_serverv4_port") val pushServerV4Port: Int? = null,
    @SerialName("use_invocation_counter") val useInvocationCounter: Int? = 1
) {
    val isUseInvocationCounter: Boolean get() = (useInvocationCounter ?: 1) != 0
}

@Serializable
data class RetryParams(
    @SerialName("max_attempts") val maxAttempts: Int = 1,
    @SerialName("delay_ms") val delayMs: Int = 1000
)

@Serializable
data class OperationItem(
    @SerialName("type") val type: String,
    @SerialName("obis") val obis: String,
    @SerialName("class_id") val classId: Int,
    @SerialName("attribute") val attribute: Int? = null,
    @SerialName("method") val method: Int? = null,
    @SerialName("value") val value: JsonElement? = null,
    @SerialName("params") val params: JsonElement? = null,
    @SerialName("retry") val retry: RetryParams? = null,
    
    // Interactive UI fields
    @SerialName("name") val name: String? = null,
    @SerialName("permission") val permission: String? = "read", // read, write, read-write
    @SerialName("default_value") val defaultValue: JsonElement? = null
)

@Serializable
data class JsonFileContent(
    @SerialName("connection") val connection: ConnectionParams,
    @SerialName("default_retry") val defaultRetry: RetryParams? = null,
    @SerialName("operations") val operations: List<OperationItem>
)

object JsonConfigValidator {
    private val json = Json { ignoreUnknownKeys = true }

    fun validate(jsonString: String): Result<JsonFileContent> {
        return try {
            val content = json.decodeFromString<JsonFileContent>(jsonString)
            validateContent(content)
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun validateContent(content: JsonFileContent) {
        if (content.operations.isEmpty()) {
            throw IllegalArgumentException("Operations array cannot be empty")
        }
        content.operations.forEach { op ->
            when (op.type.lowercase()) {
                "get" -> {
                    if (op.attribute == null) throw IllegalArgumentException("Operation 'get' for ${op.obis} must specify 'attribute'")
                }
                "set" -> {
                    if (op.attribute == null) throw IllegalArgumentException("Operation 'set' for ${op.obis} must specify 'attribute'")
                    if (op.value == null) throw IllegalArgumentException("Operation 'set' for ${op.obis} must specify 'value'")
                }
                "action" -> {
                    if (op.method == null) throw IllegalArgumentException("Operation 'action' for ${op.obis} must specify 'method'")
                }
                else -> throw IllegalArgumentException("Operation for ${op.obis} has invalid type: ${op.type}")
            }
        }
    }
}
