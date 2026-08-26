package com.example.dlmsconfigurator

import com.example.dlmsconfigurator.core.data.JsonConfigValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CosemValidationTest {

    @Test
    fun testValidConfiguration() {
        val validJson = """
            {
              "connection": {
                "baud_rate": 9600,
                "client_address": 16,
                "server_address": 1,
                "security": "low",
                "password": "secretPassword",
                "interface": "HDLC"
              },
              "default_retry": {
                "max_attempts": 3,
                "delay_ms": 2000
              },
              "operations": [
                {
                  "type": "get",
                  "obis": "1.0.0.3.0.255",
                  "class_id": 3,
                  "attribute": 2
                },
                {
                  "type": "set",
                  "obis": "0.0.96.1.1.255",
                  "class_id": 1,
                  "attribute": 2,
                  "value": "TestValue"
                },
                {
                  "type": "action",
                  "obis": "0.0.10.0.0.255",
                  "class_id": 8,
                  "method": 1
                }
              ]
            }
        """.trimIndent()

        val result = JsonConfigValidator.validate(validJson)
        assertTrue("Validation failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val content = result.getOrNull()!!
        assertEquals(9600, content.connection.baudRate)
        assertEquals(3, content.defaultRetry?.maxAttempts)
        assertEquals(3, content.operations.size)
        assertEquals("get", content.operations[0].type)
        assertEquals("set", content.operations[1].type)
        assertEquals("action", content.operations[2].type)
    }

    @Test
    fun testEmptyOperationsFailure() {
        val invalidJson = """
            {
              "connection": {
                "baud_rate": 9600,
                "client_address": 16,
                "server_address": 1,
                "security": "none",
                "interface": "HDLC"
              },
              "operations": []
            }
        """.trimIndent()

        val result = JsonConfigValidator.validate(invalidJson)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Operations array cannot be empty") == true)
    }

    @Test
    fun testGetMissingAttributeFailure() {
        val invalidJson = """
            {
              "connection": { "baud_rate": 9600 },
              "operations": [
                {
                  "type": "get",
                  "obis": "1.0.0.3.0.255",
                  "class_id": 3
                }
              ]
            }
        """.trimIndent()

        val result = JsonConfigValidator.validate(invalidJson)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("must specify 'attribute'") == true)
    }

    @Test
    fun testSetMissingValueFailure() {
        val invalidJson = """
            {
              "connection": { "baud_rate": 9600 },
              "operations": [
                {
                  "type": "set",
                  "obis": "0.0.96.1.1.255",
                  "class_id": 1,
                  "attribute": 2
                }
              ]
            }
        """.trimIndent()

        val result = JsonConfigValidator.validate(invalidJson)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("must specify 'value'") == true)
    }

    @Test
    fun testActionMissingMethodFailure() {
        val invalidJson = """
            {
              "connection": { "baud_rate": 9600 },
              "operations": [
                {
                  "type": "action",
                  "obis": "0.0.10.0.0.255",
                  "class_id": 8
                }
              ]
            }
        """.trimIndent()

        val result = JsonConfigValidator.validate(invalidJson)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("must specify 'method'") == true)
    }

    @Test
    fun testInvalidTypeFailure() {
        val invalidJson = """
            {
              "connection": { "baud_rate": 9600 },
              "operations": [
                {
                  "type": "delete",
                  "obis": "0.0.10.0.0.255",
                  "class_id": 8
                }
              ]
            }
        """.trimIndent()

        val result = JsonConfigValidator.validate(invalidJson)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("invalid type") == true)
    }

    @Test
    fun testSecureConnectionParams() {
        val validJson = """
            {
              "connection": {
                "baud_rate": 9600,
                "client_address": 17,
                "server_address": 1,
                "security": "high",
                "system_title": "4d52436c69656e74",
                "authentication_key": "000102030405060708090a0b0c0d0e0f",
                "encryption_key": "000102030405060708090a0b0c0d0e0f",
                "invocation_counter_obis": "0.0.43.1.1.255",
                "interface": "HDLC",
                "ciphering": true
              },
              "operations": [
                {
                  "type": "get",
                  "obis": "1.0.99.1.0.255",
                  "class_id": 7,
                  "attribute": 2
                }
              ]
            }
        """.trimIndent()

        val result = JsonConfigValidator.validate(validJson)
        assertTrue(result.isSuccess)
        val content = result.getOrNull()!!
        assertEquals("high", content.connection.security)
        assertEquals("4d52436c69656e74", content.connection.systemTitle)
        assertEquals("000102030405060708090a0b0c0d0e0f", content.connection.authenticationKey)
        assertEquals("000102030405060708090a0b0c0d0e0f", content.connection.encryptionKey)
        assertEquals("0.0.43.1.1.255", content.connection.invocationCounterObis)
        assertTrue(content.connection.ciphering)
    }

    @Test
    fun testUserProvidedJson() {
        val userJson = """
            {
              "connection": {
                "name": "LnG",
                "interface": "HDLC",
                "authentication": "US",
                "logical_name_referencing": 1,
                "client_address": 48,
                "server_address": 1,
                "password": "0x000102030405060708090A0B0C0D0E0F",
                "wait_time": "00:00:05",
                "resend_count": 3,
                "frame_size": 2048,
                "security_suite": "Suite0",
                "security": "AuthenticationEncryption",
                "system_title": "4553594130303030",
                "block_cipher_key": "000102030405060708090A0B0C0D0E0F",
                "authentication_key": "000102030405060708090A0B0C0D0E0F",
                "frame_counter_LN": "0.0.43.1.3.255"
              },
              "default_retry": {
                "max_attempts": 2,
                "delay_ms": 1000
              },
              "operations": [
                {
                  "name": "Meter Clock",
                  "type": "get",
                  "obis": "0.0.1.0.0.255",
                  "class_id": 8,
                  "attribute": 2,
                  "permission": "read-write",
                  "default_value": "2026-07-27T12:00:00"
                }
              ]
            }
        """.trimIndent()

        val result = JsonConfigValidator.validate(userJson)
        assertTrue("Validation should succeed for user JSON. Error: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val content = result.getOrNull()!!
        assertEquals("LnG", content.connection.name)
        assertEquals("AuthenticationEncryption", content.connection.security)
        assertEquals("0x000102030405060708090A0B0C0D0E0F", content.connection.password)
        assertEquals(1, content.operations.size)
        assertEquals("Meter Clock", content.operations[0].name)
    }

    @Test
    fun testInvocationCounterLnParsing() {
        val json = """
            {
              "connection": {
                "invocation_counter_ln": "0.0.43.1.3.255"
              },
              "operations": [
                {
                  "type": "get",
                  "obis": "1.0.0.3.0.255",
                  "class_id": 3,
                  "attribute": 2
                }
              ]
            }
        """.trimIndent()

        val result = JsonConfigValidator.validate(json)
        assertTrue(result.isSuccess)
        assertEquals("0.0.43.1.3.255", result.getOrNull()?.connection?.invocationCounterLN)
    }
}
